package com.cecapi.app.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Single shared wrapper around Android's [SpeechRecognizer] and [TextToSpeech].
 *
 * Every module (voice assistant, OCR, requests, learning, environment...) reads
 * [state] and calls [speak]/[startListening] instead of creating its own engine
 * instance, because only one SpeechRecognizer/TextToSpeech session is reliable
 * per process. This is also what lets the app "narrate" every screen transition
 * automatically, which is the core accessibility requirement for CECAPI users.
 */
@Singleton
class VoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cues: FeedbackCues,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _recognizedSpeech = MutableSharedFlow<RecognizedSpeech>(extraBufferCapacity = 4)
    val recognizedSpeech: SharedFlow<RecognizedSpeech> = _recognizedSpeech.asSharedFlow()

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeech = ArrayDeque<SpeechRequest>()

    private data class SpeechRequest(
        val text: String,
        val listenAfter: Boolean,
        val speedFactor: Float,
    )

    private val _mode = MutableStateFlow(AssistantMode.ACTIVE)
    val mode: StateFlow<AssistantMode> = _mode.asStateFlow()

    @Volatile var addressStyle: AddressStyle = AddressStyle.TU
    private var currentPitch = 1.0f
    private var currentVoiceName = ""

    // Global voice commands (volume, phone status...) that work on every screen, registered by GlobalVoiceCommands.
    private val speechInterceptors = mutableListOf<(String) -> Boolean>()

    /**
     * Interceptors that run even while a screen is capturing raw text (a username, a password, an answer in
     * Documentos): today only the black-screen and brightness exit, because the person must always have a way
     * back to a normal screen, on every screen, with nothing swallowing it.
     */
    private val criticalInterceptors = mutableListOf<(String) -> Boolean>()

    /** The last thing the assistant said aloud, so "repite lo que dijiste" can say it again. */
    @Volatile
    var lastSpoken: String? = null
        private set

    // Set by speak(listenAfter = true): open the mic as soon as the current utterance finishes,
    // so a spoken prompt ("Dime tu usuario") flows straight into the user's answer.
    @Volatile private var listenAfterSpeech = false

    // Backed by Módulo 1's `configuracion_usuario` table (see SessionRepository.aplicarConfiguracion).
    private var currentRate = 1.0f
    private var currentVolume = 1.0f

    private var speechRecognizer: SpeechRecognizer? = null

    // Wake word: while enabled, a second recognizer keeps listening for one of [wakeWords]
    // ("hola" plus the name the user gave the assistant). It never runs while the assistant
    // is speaking or while a command is being captured, so it cannot trigger on its own voice.
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeWords: Set<String> = emptySet()
        set(value) {
            field = value
            wakePatterns = value.map { word ->
                val pattern = word.split(' ').joinToString("\\s+") { Regex.escape(it) }
                Regex("(?<![\\p{L}\\p{N}])$pattern(?![\\p{L}\\p{N}])")
            }
        }
    private var wakePatterns: List<Regex> = emptyList()
    private var openMic = false
    private var wakeRecognizer: SpeechRecognizer? = null
    private val wakeRestart = Runnable { listenForWakeWord() }

    /** How many wake-word restarts in a row failed with a network error; drives the backoff in [listenForWakeWord]. */
    @Volatile private var consecutiveWakeNetworkErrors = 0

    // Barge-in: while the assistant talks, a light mic monitor (VOICE_COMMUNICATION source, so the
    // phone's echo canceller strips most of our own voice) cuts the speech if the user starts talking.
    @Volatile private var bargeInToken: AtomicBoolean? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) {
                // Nothing can be spoken at all: say so on screen and with a distinct double buzz.
                Log.e(TAG, "TextToSpeech failed to start (status=$status)")
                _state.value = VoiceState.Error(
                    "La voz del teléfono no está disponible. Es posible que el teléfono no tenga elegido " +
                        "un motor de voz, aunque esté instalado. Toca el botón de abajo para elegirlo.",
                    openTtsSettings = true,
                )
                cues.play(FeedbackCues.Cue.ERROR)
            }
            if (ttsReady) {
                selectSpanishVoice()
                applyProfile()
                textToSpeech?.setSpeechRate(currentRate)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // state already set to Speaking by speak()
                    }

                    override fun onDone(utteranceId: String?) {
                        // A long text is spoken in parts; only the last one ends the speech.
                        if (chunksLeft.decrementAndGet() > 0) return
                        stopBargeInDetection()
                        if (_state.value is VoiceState.Speaking) {
                            _state.value = VoiceState.Idle
                        }
                        val listen = listenAfterSpeech
                        listenAfterSpeech = false
                        mainHandler.post {
                            if (listen) mainHandler.postDelayed({ startListening() }, MIC_HANDOFF_MS) else resumeWakeWord()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        chunksLeft.set(0)
                        stopBargeInDetection()
                        listenAfterSpeech = false
                        _state.value = VoiceState.Idle
                        mainHandler.post { resumeWakeWord() }
                    }
                })
                while (pendingSpeech.isNotEmpty()) {
                    val request = pendingSpeech.removeFirst()
                    speakInternal(request.text, request.listenAfter, request.speedFactor)
                }
            }
        }
    }

    private val chunksLeft = AtomicInteger(0)

    private fun splitForSpeech(text: String, maxLength: Int = 3500): List<String> {
        if (text.length <= maxLength) return listOf(text)
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in text.split(SENTENCE_END)) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > maxLength) {
                parts.add(current.toString())
                current.clear()
            }
            // A single sentence longer than the limit is cut hard rather than dropped.
            sentence.chunked(maxLength).forEach { piece ->
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    /** Mexican Spanish if the phone has it, otherwise any Spanish, otherwise the phone's own language. */
    private fun selectSpanishVoice() {
        val tts = textToSpeech ?: return
        val unusable = setOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)
        if (tts.setLanguage(Locale("es", "MX")) in unusable && tts.setLanguage(Locale("es")) in unusable) {
            Log.w(TAG, "No Spanish voice installed; using the phone's default language")
            tts.setLanguage(Locale.getDefault())
        }
    }

    /**
     * Reads [text] aloud. Interrupts whatever is currently being spoken.
     * With [listenAfter] the mic opens as soon as the reading ends, to capture the user's answer.
     * [speedFactor] lowers or raises the saved voice rate only for this utterance; the screen
     * reader uses 0.9 so long texts are easier to follow without changing the rest of the app.
     */
    fun speak(
        text: String,
        listenAfter: Boolean = false,
        force: Boolean = false,
        speedFactor: Float = 1f,
    ) {
        // Muted or stopped: stay quiet. Only the farewell after "para" goes through ([force]).
        if (!force && _mode.value != AssistantMode.ACTIVE) return
        if (!ttsReady) {
            pendingSpeech.addLast(SpeechRequest(text, listenAfter, speedFactor))
            return
        }
        speakInternal(text, listenAfter, speedFactor)
    }

    /** Runs [block], a request that may be slow, and talks the person through the wait so they know it is still working. */
    suspend fun <T> speakWhile(block: suspend () -> T): T = coroutineScope {
        val progress = launch {
            delay(2_000)
            speak("Déjame pensarlo un momento.")
            delay(4_000)
            speak("Estoy consultando la información. La conexión está algo lenta, pero sigo aquí.")
            delay(4_000)
            speak("Casi… si no llega pronto, busco en Wikipedia.")
        }
        try {
            block()
        } finally {
            progress.cancel()
        }
    }

    private fun speakInternal(rawText: String, listenAfter: Boolean, speedFactor: Float = 1f) {
        // Text from an AI or a scanned document may carry markdown or emojis that must not be read aloud.
        val text = VoiceText.forSpeech(rawText).ifBlank { return }
        pauseWakeWord()
        lastSpoken = text
        listenAfterSpeech = listenAfter
        _state.value = VoiceState.Speaking(text)
        textToSpeech?.setSpeechRate((currentRate * speedFactor).coerceIn(0.5f, 2.0f))
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume) }
        // Android's speech engine refuses more than about 4000 characters at once (long AI answers, scanned
        // documents), so long text goes out in parts, split at sentence ends.
        val parts = splitForSpeech(text)
        chunksLeft.set(parts.size)
        parts.forEachIndexed { index, part ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            textToSpeech?.speak(part, mode, params, UUID.randomUUID().toString())
        }
        startBargeInDetection()
    }

    /**
     * Applies the user's saved preferences from Módulo 1 (`configuracion_usuario`).
     * [velocidadVoz] is a TTS rate multiplier (0.5x–2.0x); [volumen] is 0.0–1.0.
     */
    fun applyVoiceSettings(velocidadVoz: Float, volumen: Float) {
        currentRate = velocidadVoz.coerceIn(0.5f, 2.0f)
        currentVolume = volumen.coerceIn(0.0f, 1.0f)
        if (ttsReady) {
            textToSpeech?.setSpeechRate(currentRate)
        }
    }

    fun stopSpeaking() {
        silenceSpeech()
        mainHandler.post { resumeWakeWord() }
    }

    // ---- Assistant mode: "silencio" and "para" ----------------------------------------------------

    /** The person opened the app or tapped the mic: the assistant works normally again. */
    fun activate() {
        if (_mode.value == AssistantMode.ACTIVE) return
        _mode.value = AssistantMode.ACTIVE
        mainHandler.post { resumeWakeWord() }
    }

    /** Lets other classes handle a phrase on any screen before it reaches the screen's own ViewModel. */
    fun addSpeechInterceptor(interceptor: (String) -> Boolean) {
        speechInterceptors.add(interceptor)
    }

    /** Like [addSpeechInterceptor], but also runs while a screen is capturing raw text. See [criticalInterceptors]. */
    fun addCriticalSpeechInterceptor(interceptor: (String) -> Boolean) {
        criticalInterceptors.add(interceptor)
    }

    /** "¿En qué te puedo ayudar?" in the wording the person chose (tú or usted). */
    fun wakePrompt(): String = addressStyle.pick(VoiceMessages.WAKE_PROMPT, VoiceMessages.WAKE_PROMPT_USTED)

    /** Every recognized phrase goes through here: control words first, then global commands, then the screens. */
    private fun dispatchSpeech(text: String) {
        if (handleControl(text)) return
        if (criticalInterceptors.any { it(text) }) return
        // A screen that is capturing raw text (a username, a password) must receive it untouched.
        if (!rawInput && speechInterceptors.any { it(text) }) return
        if (!appVisible) {
            // Heard outside the app (background listening): only the global commands can be answered.
            speak(VoiceMessages.OPEN_APP)
            return
        }
        _recognizedSpeech.tryEmit(RecognizedSpeech(text, System.currentTimeMillis()))
    }

    /** True while a screen wants the next phrase exactly as spoken, without global commands in between. */
    @Volatile var rawInput = false

    /** When the person last spoke over the assistant. Screens that read on their own (the text reader) must not go on to the next part. */
    @Volatile var lastBargeInAt = 0L

    /** False while the app is not on screen (it may still be listening through the background service). */
    @Volatile var appVisible = true

    /** Ruta de la pantalla que está al frente. Solo esa pantalla recibe lo que se dice. */
    @Volatile var activeRoute: String? = null

    /** Lo que se dice, solo mientras la pantalla de [route] está al frente. */
    fun speechFor(route: String): Flow<RecognizedSpeech> = recognizedSpeech.filter { activeRoute == route }

    /** Called by the background service's "Detener" button: same as saying "para". */
    fun stopByUser() = stopAssistant()

    /**
     * "silencio" / "espera" only quiet the assistant; "para" / "detente" stop it completely.
     * They must be the whole phrase: "para" is also an everyday word ("para qué sirve").
     */
    private fun handleControl(text: String): Boolean {
        when (VoiceText.normalize(text)) {
            in SILENCE_PHRASES -> mute()
            in STOP_PHRASES -> stopAssistant()
            else -> return false
        }
        return true
    }

    /** Same as saying "silencio": stays quiet until "hola". Also called from a double tap on the mic, for hands-only use. */
    fun mute() {
        _mode.value = AssistantMode.MUTED
        silenceSpeech()
        cues.play(FeedbackCues.Cue.NAVIGATE)
        mainHandler.post { resumeWakeWord() }
    }

    private fun stopAssistant() {
        silenceSpeech()
        speak("Hasta luego.", force = true)
        _mode.value = AssistantMode.STOPPED
        stopListening()
        pauseWakeWord()
    }

    // ---- Voice profile (Personalización) ------------------------------------------------------------

    /** The phone's Spanish voices, so the person can pick one. Empty until the speech engine is ready. */
    fun spanishVoices(): List<VoiceOption> {
        val tts = textToSpeech ?: return emptyList()
        if (!ttsReady) return emptyList()
        return runCatching {
            tts.voices.orEmpty()
                .filter { it.locale.language == "es" }
                .sortedBy { it.name }
                .mapIndexed { index, voice ->
                    val country = voice.locale.getDisplayCountry(Locale("es")).ifBlank { "genérica" }
                    VoiceOption(voice.name, "Voz ${index + 1} · $country", voice.isNetworkConnectionRequired)
                }
        }.getOrDefault(emptyList())
    }

    /** [voiceName] empty means "automatic". [pitch] is 0.5 (low) to 2.0 (high). */
    fun applyVoiceProfile(voiceName: String, pitch: Float) {
        currentVoiceName = voiceName
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        if (ttsReady) applyProfile()
    }

    private fun applyProfile() {
        val tts = textToSpeech ?: return
        tts.setPitch(currentPitch)
        if (currentVoiceName.isEmpty()) {
            selectSpanishVoice()
        } else {
            tts.voices.orEmpty().firstOrNull { it.name == currentVoiceName }?.let { tts.voice = it }
        }
    }

    private fun silenceSpeech() {
        chunksLeft.set(0)
        stopBargeInDetection()
        listenAfterSpeech = false
        pendingSpeech.clear()
        textToSpeech?.stop()
        if (_state.value is VoiceState.Speaking) {
            _state.value = VoiceState.Idle
        }
    }

    private fun startBargeInDetection() {
        stopBargeInDetection()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(BARGE_IN_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                BARGE_IN_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Barge-in unavailable: ${e.message}")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        val effects = listOfNotNull(
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId) else null,
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId) else null,
        )
        effects.forEach { it.enabled = true }

        val token = AtomicBoolean(true)
        bargeInToken = token
        thread(name = "cecapi-barge-in", isDaemon = true) {
            val frame = ShortArray(BARGE_IN_SAMPLE_RATE * BARGE_IN_FRAME_MS / 1000)
            var loudMs = 0
            var peak = 0.0
            var sinceLogMs = 0
            var settleSum = 0.0
            var settleFrames = 0
            var threshold = BARGE_IN_RMS_THRESHOLD.toDouble()
            val startedAt = SystemClock.elapsedRealtime()
            try {
                record.startRecording()
                while (token.get()) {
                    val read = record.read(frame, 0, frame.size)
                    if (read <= 0) break
                    var sum = 0.0
                    for (i in 0 until read) sum += frame[i].toDouble() * frame[i]
                    val rms = sqrt(sum / read)
                    // The first moments measure the room's noise (a classroom is never silent). Speech only
                    // interrupts when it is clearly louder than that, so the kids' chatter does not cut the assistant off.
                    if (SystemClock.elapsedRealtime() - startedAt < BARGE_IN_SETTLE_MS) {
                        settleSum += rms
                        settleFrames++
                        continue
                    }
                    if (settleFrames > 0) {
                        threshold = maxOf(BARGE_IN_RMS_THRESHOLD.toDouble(), settleSum / settleFrames * BARGE_IN_NOISE_MARGIN)
                        settleFrames = 0
                    }
                    peak = maxOf(peak, rms)
                    sinceLogMs += BARGE_IN_FRAME_MS
                    if (sinceLogMs >= 1_000) {
                        Log.d(TAG, "rms peak=${peak.toInt()} threshold=${threshold.toInt()}")
                        peak = 0.0
                        sinceLogMs = 0
                    }
                    loudMs = if (rms > threshold) loudMs + BARGE_IN_FRAME_MS else maxOf(0, loudMs - BARGE_IN_FRAME_MS / 2)
                    if (loudMs >= BARGE_IN_HOLD_MS) {
                        if (token.getAndSet(false)) mainHandler.post { onBargeIn(token) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Barge-in stopped: ${e.message}")
            } finally {
                runCatching { record.stop() }
                record.release()
                effects.forEach { it.release() }
            }
        }
    }


    private fun stopBargeInDetection() {
        bargeInToken?.set(false)
        bargeInToken = null
    }

    /** The user talked over the assistant: shut it up and listen to what they are saying. */
    private fun onBargeIn(@Suppress("UNUSED_PARAMETER") token: AtomicBoolean) {
        Log.d(TAG, "Barge-in: user spoke over the assistant")
        lastBargeInAt = System.currentTimeMillis()
        silenceSpeech()
        mainHandler.postDelayed({ startListening() }, MIC_HANDOFF_MS)
    }

    /**
     * Starts listening for a single utterance. Caller must have already
     * requested RECORD_AUDIO at the Activity/Compose layer; this class does
     * not handle permission prompts since that is UI-scoped, not engine-scoped.
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            val message = "El reconocimiento de voz no está disponible en este teléfono."
            _state.value = VoiceState.Error(message)
            cues.play(FeedbackCues.Cue.ERROR)
            speak(message)
            return
        }
        activate() // tapping the mic always brings the assistant back after "silencio" or "para"
        pauseWakeWord()
        silenceSpeech()
        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening
                // The user cannot see the screen: a beep and a buzz say "I'm listening, talk now".
                cues.play(FeedbackCues.Cue.LISTENING)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                Log.w(TAG, "Recognition error $error")
                _state.value = VoiceState.Idle
                // One long buzz: the mic closed without a usable answer.
                cues.play(FeedbackCues.Cue.NO_AUDIO)
                // Never fail silently: the user cannot see the screen, so say what went wrong.
                recognitionErrorMessage(error)?.let { speak(it) } ?: resumeWakeWord()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                val score = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                _state.value = VoiceState.Idle
                if (!text.isNullOrBlank() && (score == null || score >= COMMAND_MIN_CONFIDENCE)) {
                    // Two quick pulses: "I heard you".
                    cues.play(FeedbackCues.Cue.HEARD)
                    dispatchSpeech(text)
                    resumeWakeWord()
                } else if (score != null && score < COMMAND_MIN_CONFIDENCE) {
                    // Probably the room, not the person: better to ask again than to run a wrong command.
                    speak("Hay mucho ruido. Repite, por favor, un poco más cerca del teléfono.")
                } else {
                    speak(recognitionErrorMessage(SpeechRecognizer.ERROR_NO_MATCH).orEmpty())
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }

    /** What to tell the user for a speech-recognizer error code, or null if it should stay silent. */
    private fun recognitionErrorMessage(error: Int): String? = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "No te escuché. Toca el micrófono para intentarlo otra vez."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            "No pude reconocer tu voz porque la conexión a internet falló. Intenta de nuevo."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Necesito permiso para usar el micrófono. Actívalo en los ajustes de la aplicación."
        SpeechRecognizer.ERROR_AUDIO -> "Hubo un problema con el micrófono. Intenta de nuevo."
        // CLIENT and BUSY happen when we cancel or restart the recognizer ourselves: not the user's problem.
        SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> null
        else -> "No pude escucharte. Intenta de nuevo."
    }

    fun stopListening() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (_state.value is VoiceState.Listening) {
            _state.value = VoiceState.Idle
        }
    }

    /**
     * Turns wake-word listening on with [words] (e.g. "hola" and the assistant's name).
     * Caller must already hold RECORD_AUDIO. Call again to change the words.
     */
    fun startWakeWord(words: Collection<String>, openMic: Boolean = false) {
        wakeWords = words.map(VoiceText::normalize)
            .filter { it.isNotBlank() }
            .toSet()
        this.openMic = openMic
        if (wakeWords.isEmpty()) {
            stopWakeWord()
        } else {
            resumeWakeWord()
        }
    }

    fun stopWakeWord() {
        wakeWords = emptySet()
        openMic = false
        pauseWakeWord()
    }

    private fun resumeWakeWord(delayMs: Long = 400) {
        mainHandler.removeCallbacks(wakeRestart)
        if (wakeWords.isNotEmpty() && _mode.value != AssistantMode.STOPPED) mainHandler.postDelayed(wakeRestart, delayMs)
    }

    private fun pauseWakeWord() {
        mainHandler.removeCallbacks(wakeRestart)
        if (Looper.myLooper() == Looper.getMainLooper()) releaseWakeRecognizer() else mainHandler.post { releaseWakeRecognizer() }
    }

    private fun releaseWakeRecognizer() {
        wakeRecognizer?.cancel()
        wakeRecognizer?.destroy()
        wakeRecognizer = null
    }

    private fun listenForWakeWord() {
        if (wakeWords.isEmpty() || _mode.value == AssistantMode.STOPPED) return
        val busy = _state.value is VoiceState.Speaking || _state.value is VoiceState.Listening
        if (busy || phoneIsInCall() || !SpeechRecognizer.isRecognitionAvailable(context)) {
            resumeWakeWord(if (busy) 800 else 3_000)
            return
        }
        releaseWakeRecognizer()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        wakeRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onError(error: Int) {
                if (wakeRecognizer !== recognizer) return
                val isNetworkError = error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_SERVER
                // Without internet the online recognizer fails immediately, every time: restarting at the usual
                // 300ms would poll like that forever and drain the battery. Back off, doubling each miss, only
                // for network trouble; a normal "nobody said anything" (NO_MATCH/timeout) keeps restarting fast
                // so "hola" still feels responsive.
                if (isNetworkError) {
                    consecutiveWakeNetworkErrors++
                } else {
                    consecutiveWakeNetworkErrors = 0
                }
                val delay = if (isNetworkError) {
                    (300L shl (consecutiveWakeNetworkErrors - 1).coerceAtMost(6)).coerceAtMost(WAKE_NETWORK_BACKOFF_MAX_MS)
                } else {
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 5_000L
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1_500L
                        else -> 300L
                    }
                }
                resumeWakeWord(delay)
            }

            override fun onResults(results: Bundle?) {
                if (wakeRecognizer !== recognizer) return
                consecutiveWakeNetworkErrors = 0
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                // "silencio" and "para" work without saying "hola" first.
                if (heard.any { handleControl(it) }) return
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                for ((index, text) in heard.withIndex()) {
                    val end = wakeWordEnd(text) ?: continue
                    // Background noise and TV voices often produce a confident-looking "hola" with a low score.
                    if (scores != null && scores.getOrElse(index) { 1f } < WAKE_MIN_CONFIDENCE) continue
                    onWakeWordHeard(text.substring(end).trim { !it.isLetterOrDigit() })
                    return
                }
                // Open mic (e.g. the login screen): the user is mid-task, so anything they say is a command.
                val spoken = if (openMic && _mode.value == AssistantMode.ACTIVE) heard.firstOrNull { it.isNotBlank() } else null
                if (spoken != null) {
                    releaseWakeRecognizer()
                    cues.play(FeedbackCues.Cue.HEARD)
                    dispatchSpeech(spoken.trim())
                    resumeWakeWord(1_500)
                    return
                }
                resumeWakeWord()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            },
        )
    }

    /** "hola" alone answers "¿En qué te puedo ayudar?"; "hola iniciar sesión" runs the command right away. */
    private fun onWakeWordHeard(remainder: String) {
        releaseWakeRecognizer()
        cues.play(FeedbackCues.Cue.HEARD, withSound = false)
        activate() // "hola" or the assistant's name ends a "silencio"
        if (remainder.isBlank()) {
            // Only the wake word: answer, then open the mic for what the user wants.
            speak(wakePrompt(), listenAfter = true)
        } else {
            dispatchSpeech(remainder)
            resumeWakeWord(1_500)
        }
    }

    /** Index in [raw] right after the first wake word found, or null. [fold] keeps indexes 1:1 with [raw]. */
    private fun wakeWordEnd(raw: String): Int? {
        val folded = VoiceText.fold(raw)
        for (regex in wakePatterns) {
            val match = regex.find(folded)
            if (match != null) return match.range.last + 1
        }
        return null
    }

    /** During a call or a ringing phone the mic belongs to the call: do not listen for "hola". */
    private fun phoneIsInCall(): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return false
        return audio.mode == android.media.AudioManager.MODE_IN_CALL ||
            audio.mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
            audio.mode == android.media.AudioManager.MODE_RINGTONE
    }

    private companion object {
        const val TAG = "CecapiVoice"
        const val WAKE_MIN_CONFIDENCE = 0.6f
        const val COMMAND_MIN_CONFIDENCE = 0.4f
        val SENTENCE_END = Regex("(?<=[.!?…])\\s+")

        // Whole phrases only ("para" also appears inside everyday sentences).
        val SILENCE_PHRASES = setOf("silencio", "espera", "esperame", "callate", "un momento", "un segundo")
        val STOP_PHRASES = setOf("para", "detente", "alto", "basta", "para ya", "ya para", "hasta luego", "adios", "apagate")

        // Barge-in tuning. RMS is on 16-bit PCM (0..32767); normal speech close to the phone is
        // in the low thousands. Watch "rms peak" in logcat (tag CecapiVoice) to retune per device.
        const val BARGE_IN_SAMPLE_RATE = 16_000
        const val BARGE_IN_FRAME_MS = 50
        const val BARGE_IN_SETTLE_MS = 500L
        const val BARGE_IN_HOLD_MS = 300
        const val BARGE_IN_RMS_THRESHOLD = 1_800
        const val BARGE_IN_NOISE_MARGIN = 2.5
        const val MIC_HANDOFF_MS = 300L

        /** Longest wait between wake-word restarts while the online recognizer keeps failing on the network. */
        const val WAKE_NETWORK_BACKOFF_MAX_MS = 30_000L
    }

    fun release() {
        stopWakeWord()
        stopListening()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}

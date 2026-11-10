package com.cecapi.app.feature.modulo6_aprendizaje

import com.cecapi.app.core.navigation.CecapiDestinations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class ActivityMode { AUDIO, VIBRATION }

/** One exercise, whether it is a sound or a vibration pattern. */
data class ActivityItem(
    val id: Long,
    val title: String,
    val instruction: String,
    val answer: String,
    val sound: EjercicioEntity? = null,
    val vibration: EjercicioVibracionEntity? = null,
)

data class ActivitiesUiState(
    val mode: ActivityMode = ActivityMode.AUDIO,
    val level: Int = 1,
    val items: List<ActivityItem> = emptyList(),
    val index: Int = 0,
    val feedback: String? = null,
    val correct: Boolean? = null,
    /** The instruction, the sound or the vibration is playing: answers are not taken yet. */
    val busy: Boolean = false,
    /** The index already graded this round, so repeating the same answer does not add points again. */
    val answeredIndex: Int = -1,
    /** Correct answers so far in this round (sounds or vibrations, whichever is selected). */
    val correctCount: Int = 0,
    /** All the exercises were done: the score is shown and the person can repeat or leave. */
    val finished: Boolean = false,
    val finalMessage: String? = null,
) {
    val current: ActivityItem? get() = items.getOrNull(index)
}

/**
 * Actividades: ear training. A sound (or a vibration pattern) plays and the person says where it came from
 * or what it felt like. Answers are checked, saved for the signed-in user, and the level goes up with points.
 * It also works without signing in; results are simply not saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LearningViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val sessionRepository: SessionRepository,
    private val repository: LearningRepository,
    private val vibrationRepository: VibrationRepository,
    private val audioPlayer: AudioSpatialPlayer,
    private val vibrationEngine: VibrationEngine,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _state = MutableStateFlow(ActivitiesUiState())
    val state: StateFlow<ActivitiesUiState> = _state.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val mode = MutableStateFlow(ActivityMode.AUDIO)
    private val level = MutableStateFlow(1)
    private var exerciseJob: Job? = null

    // The highest level the person has reached (saved progress), to announce when it goes up.
    private var savedLevel = 1

    private val itemsFlow: Flow<List<ActivityItem>> = combine(mode, level) { m, l -> m to l }
        .flatMapLatest { (m, l) ->
            if (m == ActivityMode.AUDIO) {
                repository.observeEjerciciosPorNivel(l).map { list ->
                    list.map { ActivityItem(it.id, it.titulo, it.instruccion, it.respuestaCorrecta, sound = it) }
                }
            } else {
                vibrationRepository.observeEjerciciosDelNivel(1).map { list ->
                    list.map { ActivityItem(it.id, it.titulo, it.instruccion, it.respuestaCorrecta, vibration = it) }
                }
            }
        }

    init {
        viewModelScope.launch {
            repository.seedEjerciciosSiVacio()
            vibrationRepository.seedEjerciciosSiVacio()
            sessionRepository.currentUser.value?.let { user ->
                savedLevel = repository.observeNivel(user.id).first().nivelActual
                level.value = savedLevel
            }
            _state.value = _state.value.copy(level = level.value)
            voiceEngine.speak(
                "Actividades para entrenar el oído. Escucharás un sonido y dirás de dónde viene. " +
                    "Di vibración para practicar con vibraciones. " + CommandCatalog.hint("actividades"),
            )
            itemsFlow.collect { list -> onItemsLoaded(list) }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.LEARNING).collect { speech -> onSpeech(speech.text) }
        }
    }

    private suspend fun onItemsLoaded(list: List<ActivityItem>) {
        val previous = _state.value
        if (list.map { it.id } == previous.items.map { it.id } && previous.mode == mode.value) return
        _state.value = previous.copy(
            mode = mode.value,
            level = level.value,
            items = list,
            index = 0,
            feedback = null,
            correct = null,
            answeredIndex = -1,
            correctCount = 0,
            finished = false,
            finalMessage = null,
        )
        if (list.isEmpty()) return
        // Let the welcome finish before the first exercise.
        speakAndWait("")
        startExercise(intro = "${where()} ")
    }

    private fun where(): String {
        val s = _state.value
        val place = if (s.mode == ActivityMode.AUDIO) "Nivel ${s.level}" else "Vibración"
        return "$place, ejercicio ${s.index + 1} de ${s.items.size}."
    }

    /**
     * The commands this screen answers. A sentence that contains an answer ("el sonido vino de la izquierda",
     * "terminó en la derecha") is an answer, even if it also has a word that is a command elsewhere; so the
     * answer check comes before repeat, next and back. "Atrás" and "adelante" are answers in level 3, so they
     * are not commands here.
     */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val chosenLevel = LEVEL_COMMAND.find(text)?.groupValues?.let { it[1].ifEmpty { it[2] } }
        val awaitingAnswer = _state.value.current != null && !_state.value.busy
        when {
            CommandCatalog.isRequest(spoken) -> {
                stopStimulus()
                voiceEngine.speak(CommandCatalog.ACTIVITIES, listenAfter = true)
            }
            chosenLevel != null -> setLevel(levelNumber(chosenLevel))
            text in VIBRATION_ONLY || has("modo vibracion", "cambia a vibracion", "cambiar a vibracion", "pasa a vibracion", "ejercicios de vibracion") ->
                setMode(ActivityMode.VIBRATION)
            text in SOUNDS_ONLY || has("modo sonidos", "cambia a sonidos", "cambiar a sonidos", "pasa a sonidos", "ejercicios de sonidos") ->
                setMode(ActivityMode.AUDIO)
            _state.value.finished -> onFinalCommand(text)
            awaitingAnswer && categoriesIn(text).isNotEmpty() -> answer(spoken)
            has("repite", "repetir", "repiteme", "otra vez", "de nuevo", "escuchalo", "ponlo otra vez", "vuelve a poner", "vuelve a sonar", "no alcance a oir", "no escuche") -> repeat()
            has("siguiente", "proximo", "continua", "continuar", "sigue", "el que sigue", "otro ejercicio", "otro", "pasa al siguiente") -> next()
            has("volver", "vuelve", "salir", "menu", "regresar", "regresa", "terminar", "termina", "ya no quiero", "inicio") -> {
                stopStimulus()
                _back.tryEmit(Unit)
            }
            _state.value.busy -> Unit
            else -> answer(spoken)
        }
    }

    private fun levelNumber(word: String): Int = when (word) {
        "uno", "1", "primer" -> 1
        "dos", "2", "segundo" -> 2
        else -> 3
    }

    fun setMode(newMode: ActivityMode) {
        if (newMode == mode.value) return
        stopStimulus()
        mode.value = newMode
    }

    fun setLevel(newLevel: Int) {
        if (mode.value != ActivityMode.AUDIO) mode.value = ActivityMode.AUDIO
        if (newLevel == level.value) return
        stopStimulus()
        level.value = newLevel
    }

    /** Two quick taps on the mic silence the assistant, for someone using touch with their hands instead of voice. */
    fun onMicDoubleTap() {
        stopStimulus()
        voiceEngine.mute()
    }

    fun onMicTapped() {
        stopStimulus()
        voiceEngine.startListening()
    }

    fun onCommandsRequested() {
        stopStimulus()
        voiceEngine.speak(CommandCatalog.ACTIVITIES, listenAfter = true)
    }

    /** Plays the sound or the vibration again, without repeating the instruction. */
    fun repeat() {
        if (_state.value.current == null || _state.value.finished) return
        startExercise(intro = "")
    }

    fun next() {
        val s = _state.value
        if (s.items.isEmpty() || s.finished) return
        // After the last exercise the round ends: say the score instead of starting over.
        if (s.index + 1 >= s.items.size) {
            finish()
            return
        }
        _state.value = s.copy(index = s.index + 1, feedback = null, correct = null)
        startExercise(intro = "${where()} ")
    }

    /** The round is over: says the score aloud and listens for "repetir" or "salir". */
    private fun finish() {
        stopStimulus()
        val s = _state.value
        val message = "Terminaste. Acertaste ${s.correctCount} de ${s.items.size}. " +
            "Di repetir para intentarlo otra vez, o salir para volver al menú."
        _state.value = s.copy(finished = true, feedback = null, correct = null, finalMessage = message)
        voiceEngine.speak(message, listenAfter = true)
    }

    /** Starts the same level (or the vibrations) again from the first exercise, with the score at zero. */
    fun restartActivity() {
        val s = _state.value
        if (s.items.isEmpty()) return
        stopStimulus()
        _state.value = s.copy(
            index = 0,
            feedback = null,
            correct = null,
            answeredIndex = -1,
            correctCount = 0,
            finished = false,
            finalMessage = null,
        )
        startExercise(intro = "${where()} ")
    }

    fun leave() {
        stopStimulus()
        _back.tryEmit(Unit)
    }

    /** At the end, what the person says is "repetir" or "salir"; anything else gets the options again. */
    private fun onFinalCommand(text: String) {
        when {
            VoiceText.hasAny(text, "repite", "repetir", "repiteme", "otra vez", "de nuevo", "volver a intentar", "reiniciar", "empezar") ->
                restartActivity()
            VoiceText.hasAny(text, "salir", "volver", "vuelve", "menu", "regresar", "regresa", "terminar", "termina", "ya no quiero", "inicio") ->
                leave()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("Di repetir para intentarlo otra vez, o salir para volver.", listenAfter = true)
            }
        }
    }

    private fun startExercise(intro: String) {
        val item = _state.value.current ?: return
        stopStimulus()
        exerciseJob = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val talk = (intro + if (intro.isBlank()) "" else item.instruction).trim()
            if (talk.isNotEmpty()) speakAndWait(talk)
            // The person left the app while it was talking: nothing should play or listen behind their back.
            if (!voiceEngine.appVisible) return@launch stopStimulus()
            delay(300)
            item.sound?.let { audioPlayer.reproducir(it) }
            item.vibration?.let { vibrationEngine.reproducirPatron(it.patronMs.aPatronMs(), it.intensidad) }
            delay(500)
            _state.value = _state.value.copy(busy = false)
            if (voiceEngine.appVisible) voiceEngine.startListening()
        }
    }

    /** The app went to the background (home button, screen lock): stop the sound, the vibration and the exercise. */
    fun onAppStopped() = stopStimulus()

    private fun stopStimulus() {
        exerciseJob?.cancel()
        audioPlayer.detener()
        vibrationEngine.detener()
        _state.value = _state.value.copy(busy = false)
    }

    private suspend fun speakAndWait(text: String) {
        if (text.isNotBlank()) voiceEngine.speak(text)
        delay(250)
        withTimeoutOrNull(20_000) { voiceEngine.state.first { it !is VoiceState.Speaking } }
    }

    private fun answer(spoken: String) {
        val item = _state.value.current ?: return
        // Already graded: repeating the same answer must not add points again. "Repite" only plays the sound.
        if (_state.value.answeredIndex == _state.value.index) {
            voiceEngine.speak("Ya respondiste este ejercicio. Di siguiente para continuar, o repite para escucharlo otra vez.", listenAfter = true)
            return
        }
        val text = VoiceText.normalize(spoken)
        val categories = categoriesIn(text)
        val movement = movement(text)
        val expectsMovement = VoiceText.normalize(item.answer).startsWith("de ")
        val expectsMixed = VoiceText.normalize(item.answer) == "mixto"
        // Nothing that sounds like an answer: do not grade it, the person may simply have said something else.
        if (categories.isEmpty() && movement == 0) {
            cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
            voiceEngine.speak("No entendí tu respuesta. Dila con una sola palabra, o di repite, o siguiente.", listenAfter = true)
            return
        }
        // Several answers at once ("cerca, lejos, centro") would always be right by luck: ask for one.
        if (!expectsMovement && !expectsMixed && categories.size > 1) {
            cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
            voiceEngine.speak("Dime una sola respuesta.", listenAfter = true)
            return
        }
        val correct = matches(spoken, item.answer)
        val message = (if (correct) "Correcto" else "Incorrecto") + ", la respuesta era ${spokenAnswer(item.answer)}."
        cues.play(if (correct) FeedbackCues.Cue.SUCCESS else FeedbackCues.Cue.ERROR)
        // answeredIndex (checked above) guarantees each exercise is counted only once per round.
        _state.value = _state.value.copy(
            feedback = message,
            correct = correct,
            answeredIndex = _state.value.index,
            correctCount = _state.value.correctCount + if (correct) 1 else 0,
        )
        val isLast = _state.value.index == _state.value.items.size - 1
        val nextHint = if (isLast) "Di siguiente para ver tu puntaje" else "Di siguiente para continuar"
        viewModelScope.launch {
            val user = sessionRepository.currentUser.value
            var levelUp = ""
            if (user != null) {
                if (item.sound != null) {
                    repository.registrarResultado(user.id, item.id, correct)
                    val nowLevel = repository.observeNivel(user.id).first().nivelActual
                    if (nowLevel > savedLevel) {
                        savedLevel = nowLevel
                        levelUp = " Subiste al nivel $nowLevel."
                    }
                } else {
                    vibrationRepository.registrarResultado(user.id, item.id, correct)
                }
            }
            voiceEngine.speak(
                "$message$levelUp $nextHint, o repite para escucharlo otra vez.",
                listenAfter = true,
            )
        }
    }

    /** How the answer is said aloud: the code stores "centro", the recordings say "ambos lados". */
    private fun spokenAnswer(answer: String): String = if (VoiceText.normalize(answer) == "centro") "ambos lados" else answer

    /** Which kinds of answer the sentence contains. One kind is a clear answer; several is not. */
    private fun categoriesIn(text: String): Set<String> =
        ANSWER_WORDS.filterValues { words -> VoiceText.hasAny(text, words) }.keys

    /** +1 when the sound went from left to right, -1 from right to left, 0 when the sentence does not say. */
    private fun movement(text: String): Int {
        val left = text.indexOf("izquierd")
        val right = text.indexOf("derech")
        return when {
            left >= 0 && right >= 0 -> if (left < right) 1 else -1
            right >= 0 && VoiceText.hasAny(text, "hacia la derecha", "a la derecha", "hacia el lado derecho", "se fue a la derecha", "va a la derecha") -> 1
            left >= 0 && VoiceText.hasAny(text, "hacia la izquierda", "a la izquierda", "hacia el lado izquierdo", "se fue a la izquierda", "va a la izquierda") -> -1
            else -> 0
        }
    }

    /**
     * Every answer accepts the several ways people say it. "Centro" and "ambos lados" are the same answer.
     * Movement ("de izquierda a derecha") is judged by the order of the two sides or by where the sound went
     * ("hacia la derecha"). Any other answer is right only when it is the one kind of answer said.
     */
    private fun matches(spoken: String, expected: String): Boolean {
        val text = VoiceText.normalize(spoken)
        val want = VoiceText.normalize(expected)
        val categories = categoriesIn(text)
        return when (want) {
            "de izquierda a derecha" -> movement(text) > 0
            "de derecha a izquierda" -> movement(text) < 0
            "mixto" -> "mixto" in categories || categories == setOf("corto", "largo")
            else -> categories == setOf(want)
        }
    }

    override fun onCleared() {
        exerciseJob?.cancel()
        audioPlayer.detener()
        vibrationEngine.detener()
        super.onCleared()
    }

    private companion object {
        val LEVEL_COMMAND = Regex("(?:nivel (uno|dos|tres|1|2|3)|(primer|segundo|tercer) nivel)")

        // Changing the activity needs the bare word or an explicit phrase: "vibró corto" is an answer.
        val VIBRATION_ONLY = setOf("vibracion", "vibraciones", "vibrar", "la vibracion", "con vibracion", "vibrador")
        val SOUNDS_ONLY = setOf("sonidos", "sonido", "audio", "audios", "los sonidos", "con sonidos", "oido")

        /** Every way of saying each answer. Whole words only, so "acerca" is not "cerca". */
        val ANSWER_WORDS: Map<String, List<String>> = mapOf(
            "izquierda" to listOf("izquierda", "izquierdo", "lado izquierdo", "oido izquierdo"),
            "derecha" to listOf("derecha", "derecho", "lado derecho", "oido derecho"),
            "centro" to listOf(
                "centro", "ambos", "los dos", "medio", "en medio", "al centro", "de los dos lados", "por los dos lados",
                "los dos lados", "ambos lados", "ambos oidos", "los dos oidos", "de ambos lados", "parejo",
            ),
            "cerca" to listOf("cerca", "cercano", "cerquita", "pegado", "muy cerca", "fuerte", "de cerca"),
            "lejos" to listOf("lejos", "lejano", "distante", "alejado", "a lo lejos", "muy lejos", "bajito", "de lejos", "debil"),
            "enfrente" to listOf("enfrente", "frente", "adelante", "al frente", "de frente", "delante", "por delante"),
            "atras" to listOf("atras", "detras", "espalda", "de atras", "por atras", "por detras", "a mi espalda", "atrasito"),
            "corto" to listOf("corto", "cortos", "cortito", "breve", "breves", "rapido", "rapidos"),
            "largo" to listOf("largo", "largos", "prolongado", "extenso", "lento", "sostenido"),
            "mixto" to listOf("mixto", "mezcla", "mezclado", "combinado", "alternado", "variado", "corto y largo", "largo y corto"),
        )
    }
}

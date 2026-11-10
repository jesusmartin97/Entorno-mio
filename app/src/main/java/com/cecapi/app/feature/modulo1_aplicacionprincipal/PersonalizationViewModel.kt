package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.DisplayControl
import com.cecapi.app.core.voice.AddressStyle
import com.cecapi.app.core.voice.AssistantPreferences
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceOption
import com.cecapi.app.core.voice.VoiceProfile
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Personalización: how the assistant sounds (voice, speed, pitch), how it addresses the person
 * (tú or usted, the name to greet, the assistant's own name) and how sounds and vibration feel.
 * Everything is stored on the phone, so it works before anyone signs in.
 */
@HiltViewModel
class PersonalizationViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val deviceSettings: DeviceSettings,
    private val assistantPreferences: AssistantPreferences,
    private val cues: FeedbackCues,
    private val displayControl: DisplayControl,
) : ViewModel() {

    val speechRate: StateFlow<Float> = deviceSettings.speechRate.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val pitch: StateFlow<Float> = deviceSettings.pitch.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val voiceName: StateFlow<String> = deviceSettings.voiceName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val addressStyle: StateFlow<AddressStyle> = deviceSettings.addressStyle.stateIn(viewModelScope, SharingStarted.Eagerly, AddressStyle.TU)
    val preferredName: StateFlow<String> = deviceSettings.preferredName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val assistantName: StateFlow<String> = assistantPreferences.assistantName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val soundCues: StateFlow<Boolean> = deviceSettings.soundCues.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val vibrationCues: StateFlow<Boolean> = deviceSettings.vibrationCues.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val vibrationLevel: StateFlow<Int> = deviceSettings.vibrationLevel.stateIn(viewModelScope, SharingStarted.Eagerly, 2)
    val blackScreen: StateFlow<Boolean> = deviceSettings.blackScreen.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val minBrightness: StateFlow<Boolean> = deviceSettings.minBrightness.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.PERSONALIZATION).collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Personalización. Aquí eliges mi voz, cómo te llamo, cómo me llamas y cómo se sienten los avisos. " +
                "Mantén presionado cualquier control para que te explique para qué sirve. " +
                CommandCatalog.hint("personalización"),
            listenAfter = true,
        )
    }

    /** The commands this screen answers; each one does what the matching control on screen does. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val newAssistantName = ASSISTANT_NAME.find(spoken)?.groupValues?.get(1)
        val newUserName = USER_NAME.find(spoken)?.groupValues?.get(1)
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.PERSONALIZATION, listenAfter = true)
            newAssistantName != null -> onAssistantNameSaved(cleanName(newAssistantName))
            newUserName != null -> onPreferredNameSaved(cleanName(newUserName))
            has("usted") -> onAddressStyleChosen(AddressStyle.USTED)
            has("de tu", "tutea") -> onAddressStyleChosen(AddressStyle.TU)
            has("voz anterior") -> cycleVoice(-1)
            has("otra voz", "siguiente voz", "cambia la voz", "cambiar la voz", "cambia de voz") -> cycleVoice(1)
            has("prueba de voz", "probar voz", "prueba la voz", "escuchate", "di algo") -> onTestVoice()
            has("mas rapido", "mas veloz", "acelera", "habla mas rapido") -> onSpeechRateChanged((speechRate.value + 0.25f).coerceIn(0.5f, 2.0f))
            has("mas lento", "mas despacio", "despacio", "habla mas lento") -> onSpeechRateChanged((speechRate.value - 0.25f).coerceIn(0.5f, 2.0f))
            has("mas grave", "voz grave") -> onPitchChanged((pitch.value - 0.25f).coerceIn(0.5f, 2.0f))
            has("mas agudo", "voz aguda") -> onPitchChanged((pitch.value + 0.25f).coerceIn(0.5f, 2.0f))
            has("vibracion") && has("suave") -> onVibrationLevelChanged(1)
            has("vibracion") && has("fuerte") -> onVibrationLevelChanged(3)
            has("vibracion") && has("normal") -> onVibrationLevelChanged(2)
            has("vibracion") && onOff(text) != null -> onVibrationCuesChanged(onOff(text) == true)
            has("sonido") && onOff(text) != null -> onSoundCuesChanged(onOff(text) == true)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. " + CommandCatalog.hint("personalización"), listenAfter = true)
            }
        }
    }

    /** "Desactiva" contains "activa", so the negative words are checked first. */
    private fun onOff(text: String): Boolean? = when {
        listOf("desactiv*", "apaga", "quita", "sin", "no quiero").let { phrases -> VoiceText.hasAny(text, phrases) } -> false
        listOf("activ*", "enciende", "prende", "pon", "quiero").let { phrases -> VoiceText.hasAny(text, phrases) } -> true
        else -> null
    }

    private fun cleanName(raw: String): String =
        raw.trim().trimEnd('.', ',', '!', '?').replaceFirstChar { it.uppercase() }

    /** Moves to the next (or, with -1, the previous) Spanish voice the phone has. */
    private fun cycleVoice(step: Int) {
        val options = voices()
        if (options.isEmpty()) {
            voiceEngine.speak("No encontré otras voces en español en este teléfono.")
            return
        }
        val current = options.indexOfFirst { it.name == voiceName.value }
        onVoiceChosen(options[(current + step).mod(options.size)].name)
    }

    /** Call from the header button: reads out this screen's commands. */
    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.PERSONALIZATION, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    /** The phone's Spanish voices; empty until the speech engine has started, so the screen asks again. */
    fun voices(): List<VoiceOption> = voiceEngine.spanishVoices()

    fun onVoiceChosen(name: String) {
        voiceEngine.applyVoiceProfile(name, pitch.value)
        viewModelScope.launch { deviceSettings.setVoiceName(name) }
        voiceEngine.speak("Así sueno con esta voz.")
    }

    fun onSpeechRateChanged(rate: Float) {
        voiceEngine.applyVoiceSettings(rate, 1.0f) // apply first so the confirmation already uses the new speed
        viewModelScope.launch { deviceSettings.setSpeechRate(rate) }
        voiceEngine.speak(
            when {
                rate < 0.9f -> "Velocidad lenta."
                rate > 1.1f -> "Velocidad rápida."
                else -> "Velocidad normal."
            },
        )
    }

    fun onPitchChanged(value: Float) {
        voiceEngine.applyVoiceProfile(voiceName.value, value)
        viewModelScope.launch { deviceSettings.setPitch(value) }
        voiceEngine.speak(
            when {
                value < 0.9f -> "Tono grave."
                value > 1.1f -> "Tono agudo."
                else -> "Tono normal."
            },
        )
    }

    fun onTestVoice() {
        voiceEngine.speak("Así suena tu asistente con esta configuración.")
    }

    fun onAddressStyleChosen(style: AddressStyle) {
        voiceEngine.addressStyle = style // so the very next sentence already uses it
        viewModelScope.launch { deviceSettings.setAddressStyle(style) }
        voiceEngine.speak(style.pick("De acuerdo, te hablaré de tú.", "De acuerdo, le hablaré de usted."))
    }

    fun onPreferredNameSaved(name: String) {
        val clean = name.trim()
        viewModelScope.launch { deviceSettings.setPreferredName(clean) }
        voiceEngine.speak(
            if (clean.isEmpty()) {
                "Listo, te saludaré con el nombre de tu cuenta."
            } else {
                "Listo, te voy a llamar $clean."
            },
        )
    }

    fun onAssistantNameSaved(name: String) {
        val clean = name.trim()
        viewModelScope.launch { assistantPreferences.setAssistantName(clean) }
        voiceEngine.speak(
            if (clean.isEmpty()) {
                "Listo, ya no tengo nombre. Toca el micrófono para hablar conmigo."
            } else {
                "Listo, ahora me llamo $clean. Di $clean para hablar conmigo."
            },
        )
    }

    fun onSoundCuesChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setSoundCues(enabled) }
        voiceEngine.speak(if (enabled) "Sonidos activados." else "Sonidos desactivados.")
    }

    fun onVibrationCuesChanged(enabled: Boolean) {
        cues.vibrationEnabled = enabled
        viewModelScope.launch { deviceSettings.setVibrationCues(enabled) }
        if (enabled) cues.play(FeedbackCues.Cue.TAP)
        voiceEngine.speak(if (enabled) "Vibraciones activadas." else "Vibraciones desactivadas.")
    }

    fun onVibrationLevelChanged(level: Int) {
        cues.vibrationLevel = level // felt right away
        viewModelScope.launch { deviceSettings.setVibrationLevel(level) }
        cues.play(FeedbackCues.Cue.SUCCESS)
        voiceEngine.speak(
            when (level) {
                1 -> "Vibración suave."
                3 -> "Vibración fuerte."
                else -> "Vibración normal."
            },
        )
    }

    /** A ready-made way of talking for a child, an adult or an older person: speed, pitch and tú or usted. */
    fun onProfileChosen(profile: VoiceProfile) {
        viewModelScope.launch { deviceSettings.applyProfile(profile) }
        voiceEngine.applyVoiceSettings(profile.rate, 1.0f) // so the announcement already sounds the new way
        voiceEngine.addressStyle = profile.address
        voiceEngine.speak(profile.announcement)
    }

    /** El brillo mínimo que enciende la pantalla negra se apaga con ella; si ya estaba encendido antes, se queda. */
    private var brilloEncendidoPorPantallaNegra = false

    fun onBlackScreenChanged(enabled: Boolean) {
        displayControl.setBlackScreen(enabled)
        if (enabled) {
            brilloEncendidoPorPantallaNegra = !minBrightness.value
            displayControl.setMinBrightness(true)
        } else if (brilloEncendidoPorPantallaNegra) {
            brilloEncendidoPorPantallaNegra = false
            displayControl.setMinBrightness(false)
        }
    }

    fun onMinBrightnessChanged(enabled: Boolean) = displayControl.setMinBrightness(enabled)

    fun onTestCue(cue: FeedbackCues.Cue, description: String) {
        cues.play(cue)
        voiceEngine.speak(description)
    }

    private companion object {
        // "llámate Luna", "te llamas Luna", "tu nombre es Luna"
        val ASSISTANT_NAME = Regex(
            "(?:ll[aá]mate|te llam(?:as|es)|tu nombre (?:es|ser[aá]))\\s+(?:como\\s+)?(.+)",
            RegexOption.IGNORE_CASE,
        )

        // "llámame Ana", "me llamo Ana", "mi nombre es Ana"
        val USER_NAME = Regex(
            "(?:ll[aá]mame|me llamo|mi nombre es|que me llames)\\s+(?:como\\s+)?(.+)",
            RegexOption.IGNORE_CASE,
        )
    }
}

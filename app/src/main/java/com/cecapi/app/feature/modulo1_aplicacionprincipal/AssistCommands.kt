package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.voice.CommandAlternatives
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.HelpTopics
import com.cecapi.app.core.voice.ScreenContext
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceProfile
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo6_aprendizaje.LearningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Commands that let anyone, of any age, learn and use the app alone, by voice and without internet:
 * "tutorial" and "cómo funciona la cámara", "dónde estoy", "cómo voy" (the training level), talking slower or
 * faster, and ready-made profiles for a child, an adult or an older person.
 * Constructing this class (the activity injects it) registers it like [com.cecapi.app.core.voice.GlobalVoiceCommands].
 */
@Singleton
class AssistCommands @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val deviceSettings: DeviceSettings,
    private val screenContext: ScreenContext,
    private val sessionRepository: SessionRepository,
    private val learningRepository: LearningRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        voiceEngine.addSpeechInterceptor(::handle)
    }

    private fun handle(spoken: String): Boolean {
        val text = VoiceText.normalize(spoken)
        val profile = profileIn(text)
        return when {
            profile != null -> {
                scope.launch {
                    deviceSettings.applyProfile(profile)
                    // Applied by hand as well, so this very confirmation already sounds the new way.
                    voiceEngine.applyVoiceSettings(profile.rate, 1.0f)
                    voiceEngine.addressStyle = profile.address
                    voiceEngine.speak(profile.announcement)
                }
                true
            }
            VoiceText.hasAny(text, WHERE) -> {
                whereAmI()
                true
            }
            VoiceText.hasAny(text, PROGRESS) -> {
                progress()
                true
            }
            VoiceText.hasAny(text, SLOWER) -> {
                changeSpeed(-SPEED_STEP)
                true
            }
            VoiceText.hasAny(text, FASTER) -> {
                changeSpeed(SPEED_STEP)
                true
            }
            else -> tutorial(text)
        }
    }

    private fun profileIn(text: String): VoiceProfile? = when {
        VoiceText.hasAny(text, CHILD_WORDS) -> VoiceProfile.CHILD
        VoiceText.hasAny(text, SENIOR_WORDS) -> VoiceProfile.SENIOR
        VoiceText.hasAny(text, NORMAL_WORDS) -> VoiceProfile.NORMAL
        else -> null
    }

    private fun whereAmI() {
        val name = screenContext.spokenName()
        voiceEngine.speak(
            if (name == null) {
                "No sé en qué pantalla estás. Di menú para ir al menú principal."
            } else {
                "Estás en $name. " + CommandCatalog.hint("aquí")
            },
        )
    }

    /** The training level, read from what the person has done in Actividades. Needs a signed-in account. */
    private fun progress() {
        scope.launch {
            val user = sessionRepository.currentUser.value
            if (user == null) {
                voiceEngine.speak("Inicia sesión para que guarde tu avance. Sin cuenta puedes practicar, pero no se guarda.")
                return@launch
            }
            val level = learningRepository.observeNivel(user.id).first()
            voiceEngine.speak(
                "Vas en el nivel ${level.nivelActual}, con ${level.puntosTotales} puntos en este nivel. " +
                    "Sigue practicando en actividades para subir.",
            )
        }
    }

    private fun changeSpeed(delta: Float) {
        scope.launch {
            val current = deviceSettings.speechRate.first()
            val next = (current + delta).coerceIn(MIN_RATE, MAX_RATE)
            if (next == current) {
                voiceEngine.speak(if (delta < 0) "Ya hablo lo más despacio posible." else "Ya hablo lo más rápido posible.")
                return@launch
            }
            deviceSettings.setSpeechRate(next)
            voiceEngine.applyVoiceSettings(next, 1.0f) // so the answer already uses the new speed
            voiceEngine.speak(if (delta < 0) "Ahora hablo más despacio." else "Ahora hablo más rápido.")
        }
    }

    /** True when [text] asked how something works and it was answered. A bare "cómo funciona un motor" is not ours. */
    private fun tutorial(text: String): Boolean {
        if (!VoiceText.hasAny(text, TUTORIAL_WORDS)) return false
        val area = CommandAlternatives.areas.firstOrNull { it.key != "help" && VoiceText.hasAny(text, it.keywords) }
        val about = area?.let { HelpTopics.byArea[it.key] }
        val general = VoiceText.hasAny(text, ABOUT_THE_APP) || text.split(" ").size <= 3
        return when {
            about != null -> {
                voiceEngine.speak(about)
                true
            }
            general -> {
                voiceEngine.speak(HelpTopics.GENERAL)
                true
            }
            else -> false
        }
    }

    private companion object {
        const val SPEED_STEP = 0.25f
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f

        val WHERE = listOf(
            "donde estoy", "en donde estoy", "en que pantalla estoy", "que pantalla es esta", "en que parte estoy",
            "donde me encuentro",
        )
        val PROGRESS = listOf(
            "como voy", "mi progreso", "mi nivel", "en que nivel voy", "que nivel tengo", "como llevo mi progreso",
        )
        val SLOWER = listOf(
            "habla mas despacio", "habla mas lento", "hable mas despacio", "mas despacio por favor", "hablas muy rapido",
            "habla despacio",
        )
        val FASTER = listOf(
            "habla mas rapido", "hable mas rapido", "hablas muy despacio", "hablas muy lento", "mas rapido por favor",
        )
        val CHILD_WORDS = listOf(
            "perfil de nino", "perfil nino", "perfil de ninos", "perfil ninos", "perfil infantil", "modo nino",
            "modo ninos", "soy un nino", "soy una nina", "soy nina", "soy nino",
        )
        val SENIOR_WORDS = listOf(
            "perfil de persona mayor", "perfil persona mayor", "modo persona mayor", "perfil mayor", "perfil de adulto mayor",
            "perfil adulto mayor", "modo adulto mayor", "tercera edad", "soy una persona mayor", "soy adulto mayor",
        )
        val NORMAL_WORDS = listOf("perfil normal", "modo normal", "perfil de adulto", "perfil adulto", "perfil estandar")
        val TUTORIAL_WORDS = listOf(
            "tutorial", "como se usa", "como funciona", "como uso", "como empiezo", "ensename", "explicame como",
            "para que sirve", "que es esto",
        )
        val ABOUT_THE_APP = listOf("la aplicacion", "la app", "esto", "el asistente", "la aplicacion cecapi", "cecapi")
    }
}

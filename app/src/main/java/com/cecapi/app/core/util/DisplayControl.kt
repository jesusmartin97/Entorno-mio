package com.cecapi.app.core.util

import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The black screen and the minimum brightness, for people who do not use the picture.
 * Both are only drawn or applied while the app is showing (the brightness is set on the app's own
 * window, never on the phone's system setting), so leaving the app always brings the phone back to normal.
 * The person must always have a way out, so both switch off by voice on every screen, and the black
 * screen also switches off by holding a finger on it.
 */
@Singleton
class DisplayControl @Inject constructor(
    private val deviceSettings: DeviceSettings,
    private val voiceEngine: VoiceEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Turning the black screen or the minimum brightness back to normal must always work, even on a screen
        // that is capturing raw text (a username, a password, an answer in Documentos): otherwise the black
        // screen's own instructions ("di pantalla normal") would stop working the moment someone needs them most.
        voiceEngine.addCriticalSpeechInterceptor { spoken ->
            val text = VoiceText.normalize(spoken)
            val reply = when {
                VoiceText.hasAny(text, BLACK_OFF_PHRASES) -> {
                    scope.launch { deviceSettings.setBlackScreen(false) }
                    BLACK_OFF
                }
                VoiceText.hasAny(text, BRIGHTNESS_NORMAL_PHRASES) -> {
                    scope.launch { deviceSettings.setMinBrightness(false) }
                    BRIGHTNESS_NORMAL
                }
                else -> null
            }
            if (reply != null) {
                voiceEngine.activate() // "silencio" or "para" must not keep this safety exit from working
                voiceEngine.speak(reply, force = true)
            }
            reply != null
        }
    }

    fun setBlackScreen(enabled: Boolean, announce: Boolean = true) {
        scope.launch { deviceSettings.setBlackScreen(enabled) }
        if (announce) voiceEngine.speak(if (enabled) BLACK_ON else BLACK_OFF)
    }

    fun setMinBrightness(enabled: Boolean, announce: Boolean = true) {
        scope.launch { deviceSettings.setMinBrightness(enabled) }
        if (announce) voiceEngine.speak(if (enabled) BRIGHTNESS_MIN else BRIGHTNESS_NORMAL)
    }

    /** The spoken answer when [spoken] is a display command, or null when it is something else. */
    fun handle(spoken: String): String? {
        val text = VoiceText.normalize(spoken)
        return when {
            BLACK_OFF_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                scope.launch { deviceSettings.setBlackScreen(false) }
                BLACK_OFF
            }
            BLACK_ON_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                scope.launch { deviceSettings.setBlackScreen(true) }
                BLACK_ON
            }
            BRIGHTNESS_NORMAL_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                scope.launch { deviceSettings.setMinBrightness(false) }
                BRIGHTNESS_NORMAL
            }
            BRIGHTNESS_MIN_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                scope.launch { deviceSettings.setMinBrightness(true) }
                BRIGHTNESS_MIN
            }
            else -> null
        }
    }

    companion object {
        /** The lowest level that does not switch the screen off on phones that read 0 as "off". */
        const val MIN_BRIGHTNESS = 0.01f

        const val BLACK_ON =
            "Pantalla negra activada. Toca la pantalla una vez para hablarme. " +
                "Para volver, di pantalla normal, o mantén presionada la pantalla."
        const val BLACK_OFF = "Pantalla normal."
        const val BRIGHTNESS_MIN = "Brillo al mínimo. Para volver, di brillo normal."
        const val BRIGHTNESS_NORMAL = "Brillo normal."

        // The "off" phrases are checked first: "apaga la pantalla negra" also contains "pantalla negra".
        private val BLACK_OFF_PHRASES = listOf(
            "pantalla normal", "pantalla visible", "muestra la pantalla", "mostrar la pantalla", "muestrame la pantalla",
            "quita la pantalla negra", "quitar la pantalla negra", "desactiva la pantalla negra", "desactivar la pantalla negra",
            "apaga la pantalla negra", "sin pantalla negra", "enciende la pantalla", "prende la pantalla",
            "pantalla en normal", "salir de pantalla negra",
        )
        private val BLACK_ON_PHRASES = listOf(
            "pantalla negra", "pantalla en negro", "pon la pantalla en negro", "oscurece la pantalla", "pantalla oscura",
        )
        private val BRIGHTNESS_NORMAL_PHRASES = listOf(
            "brillo normal", "brillo automatico", "brillo maximo", "brillo alto", "sube el brillo", "subir el brillo",
            "quita el brillo minimo", "desactiva el brillo minimo",
        )
        private val BRIGHTNESS_MIN_PHRASES = listOf(
            "brillo minimo", "brillo bajo", "baja el brillo", "bajar el brillo", "brillo al minimo", "minimo brillo",
            "activa el brillo minimo",
        )
    }
}

package com.cecapi.app.core.util

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Media volume by voice, and spoken feedback whenever it changes (also with the phone's own
 * buttons), because a blind user cannot see the volume bar. It only touches the media stream,
 * the same one the volume buttons control while the app is open; never the ringer or silent mode.
 * By voice the volume never goes below the first step, so the assistant cannot silence itself.
 */
@Singleton
class VolumeControl @Inject constructor(
    @ApplicationContext context: Context,
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val announceCurrent = Runnable { voiceEngine.speak(levelText()) }

    /** Hasta cuándo no se anuncia el nivel porque el doble volumen ya está leyendo la pantalla. */
    @Volatile private var suprimirHasta = 0L

    private val maxIndex get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val currentIndex get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    /** Roughly 15% per voice command, at least one step. */
    private val step get() = maxOf(1, (maxIndex * 0.15f).roundToInt())

    /** Applies a volume command found in [spoken] and returns what to say, or null if it is not one. */
    fun handle(spoken: String): String? {
        val text = VoiceText.normalize(spoken)
        return when {
            listOf("volumen al maximo", "volumen maximo", "maximo volumen").let { phrases -> VoiceText.hasAny(text, phrases) } ->
                change(maxIndex, atLimit = "El volumen ya está al máximo.")
            listOf("volumen al minimo", "volumen minimo", "minimo volumen").let { phrases -> VoiceText.hasAny(text, phrases) } ->
                change(1, atLimit = "El volumen ya está al mínimo.")
            listOf("sube el volumen", "subir el volumen", "sube volumen", "subir volumen", "aumenta el volumen",
                "aumentar el volumen", "mas volumen", "mas fuerte", "volumen arriba", "alza el volumen")
                .let { phrases -> VoiceText.hasAny(text, phrases) } ->
                change((currentIndex + step).coerceAtMost(maxIndex), atLimit = "El volumen ya está al máximo.")
            listOf("baja el volumen", "bajar el volumen", "baja volumen", "bajar volumen", "disminuye el volumen",
                "menos volumen", "mas bajo", "volumen abajo")
                .let { phrases -> VoiceText.hasAny(text, phrases) } ->
                change((currentIndex - step).coerceAtLeast(1), atLimit = "El volumen ya está al mínimo.")
            listOf("cuanto volumen", "que volumen", "nivel de volumen", "como esta el volumen", "volumen actual")
                .let { phrases -> VoiceText.hasAny(text, phrases) } -> levelText()
            else -> null
        }
    }

    /** Current media volume as 0..100. */
    fun percent(): Int = (currentIndex * 100f / maxIndex).roundToInt()

    /** Sets the media volume from 1..100 % (never fully silent, so the assistant stays audible). */
    fun setPercent(percent: Int) {
        val index = (percent.coerceIn(0, 100) / 100f * maxIndex).roundToInt().coerceAtLeast(1)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
    }

    /**
     * El doble volumen abajo ya va a leer la pantalla. Cancela el anuncio pendiente del nivel y
     * evita que la segunda pulsación lo vuelva a programar, para que no tape la lectura.
     */
    fun suprimirAnuncio() {
        handler.removeCallbacks(announceCurrent)
        suprimirHasta = SystemClock.uptimeMillis() + SUPRESION_MS
    }

    /** The phone's volume buttons were pressed: say the new level once the presses settle. */
    fun onVolumeKey() {
        handler.removeCallbacks(announceCurrent)
        if (SystemClock.uptimeMillis() < suprimirHasta) return
        handler.postDelayed(announceCurrent, 600)
    }

    private fun change(target: Int, atLimit: String): String {
        if (target == currentIndex) return atLimit
        return try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            cues.play(FeedbackCues.Cue.TAP)
            levelText()
        } catch (e: SecurityException) {
            "No pude cambiar el volumen."
        }
    }

    fun levelText(): String {
        val index = currentIndex
        return when {
            index >= maxIndex -> "Volumen al máximo."
            index == 0 -> "El volumen está en silencio."
            else -> "Volumen al ${(index * 100f / maxIndex).roundToInt()} por ciento."
        }
    }

    private companion object {
        /** Cubre la segunda pulsación y el retardo de 600 ms del anuncio normal. */
        const val SUPRESION_MS = 1_200L
    }
}

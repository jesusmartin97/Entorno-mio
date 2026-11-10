package com.cecapi.app.core.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's sound and vibration language. A blind user cannot see what happened, so every
 * important event has its own short pattern. Keep the patterns distinct from each other and the
 * same in every screen: the user learns them once.
 *
 * Each cue vibrates and, where it helps, also beeps. Both can be turned off in Configuración.
 */
@Singleton
class FeedbackCues @Inject constructor(
    @ApplicationContext context: Context,
) {
    enum class Cue(val pattern: LongArray, val tone: Int?, val toneMs: Int = 150) {
        /** The mic just opened: talk now. */
        LISTENING(longArrayOf(0, 90), ToneGenerator.TONE_PROP_BEEP, 120),

        /** The phrase was captured. */
        HEARD(longArrayOf(0, 50, 70, 50), null),

        /** The mic closed without hearing anything usable. */
        NO_AUDIO(longArrayOf(0, 250), null),

        /** It worked: signed in, account created. */
        SUCCESS(longArrayOf(0, 60, 60, 140), ToneGenerator.TONE_PROP_ACK, 200),

        /** It failed: wrong credentials, something could not be done. */
        ERROR(longArrayOf(0, 120, 80, 120, 80, 120), ToneGenerator.TONE_PROP_NACK, 300),

        /** Needs attention: no internet, low battery. */
        WARNING(longArrayOf(0, 200, 100, 200), ToneGenerator.TONE_PROP_PROMPT, 250),

        /** Sign-in blocked. The longest and heaviest pattern. */
        LOCKED(longArrayOf(0, 500), ToneGenerator.TONE_PROP_NACK, 500),

        /** A phrase was heard but not understood. */
        NOT_UNDERSTOOD(longArrayOf(0, 40, 60, 40), ToneGenerator.TONE_PROP_BEEP2, 150),

        /** Changing screen or opening a module. */
        NAVIGATE(longArrayOf(0, 40), null),

        /** A notification arrived (heard as a heads-up, never its content). */
        INCOMING(longArrayOf(0, 50, 60, 50, 60, 50), ToneGenerator.TONE_PROP_BEEP2, 120),

        /** A small confirmation: a setting changed, the volume moved. */
        TAP(longArrayOf(0, 25), null),
    }

    @Volatile var soundEnabled = true
    @Volatile var vibrationEnabled = true

    /** 1 soft, 2 normal, 3 strong. Only phones with amplitude control can tell them apart. */
    @Volatile var vibrationLevel = 2

    private val handler = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun play(cue: Cue, withSound: Boolean = true) {
        if (vibrationEnabled) {
            runCatching {
                vibrator?.takeIf { it.hasVibrator() }?.vibrate(effectFor(cue.pattern))
            }
        }
        val tone = cue.tone
        if (soundEnabled && withSound && tone != null) {
            runCatching {
                val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
                generator.startTone(tone, cue.toneMs)
                handler.postDelayed({ generator.release() }, cue.toneMs + 150L)
            }
        }
    }

    /** The pattern at the chosen strength: silent gaps stay at 0, each buzz gets the level's amplitude. */
    private fun effectFor(pattern: LongArray): VibrationEffect {
        val canScale = vibrator?.hasAmplitudeControl() == true
        if (!canScale) return VibrationEffect.createWaveform(pattern, -1)
        val amplitude = when (vibrationLevel) {
            1 -> 70
            3 -> 255
            else -> 150
        }
        val amplitudes = IntArray(pattern.size) { index -> if (index % 2 == 0) 0 else amplitude }
        return VibrationEffect.createWaveform(pattern, amplitudes, -1)
    }
}

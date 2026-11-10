package com.cecapi.app.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Opens the system's "text-to-speech output" settings, where the person picks or installs a
 * speech engine. Some phones ship with none chosen by default even if one is installed, which
 * leaves TextToSpeech unable to start at all — this sends the person straight to the fix instead
 * of just naming the problem.
 *
 * There is no public SDK constant for this screen (neither [android.provider.Settings] nor
 * [android.speech.tts.TextToSpeech.Engine] declare one); this is the Settings app's own action
 * for it, stable across Android versions. A handful of heavily customized phones (some ColorOS,
 * MIUI builds) may not register it, so this falls back to doing nothing rather than crashing.
 */
fun openTtsSettings(context: Context) {
    try {
        context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Nothing to fall back to on this phone; the banner text already explains the problem.
    }
}

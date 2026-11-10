package com.cecapi.app.core.voice

import com.cecapi.app.core.util.DisplayControl
import com.cecapi.app.core.util.PhoneStatusReader
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.notifications.NotificationReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Commands that mean the same thing on every screen, so no screen has to implement them:
 * phone status (battery, wifi, time, date...), volume, notifications, black screen and brightness.
 * They are answered before the phrase reaches the screen's own ViewModel. "Silencio" and "para"
 * are handled even earlier, inside the [VoiceEngine].
 * Constructing this class (the activity injects it) is what registers it.
 */
@Singleton
class GlobalVoiceCommands @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val phoneStatusReader: PhoneStatusReader,
    private val volumeControl: VolumeControl,
    private val notificationReader: NotificationReader,
    private val displayControl: DisplayControl,
    private val commandHelp: CommandHelp,
) {
    init {
        voiceEngine.addSpeechInterceptor(::handle)
    }

    private fun handle(text: String): Boolean {
        // "Otras formas de decirlo" answers itself (it speaks and may ask which area).
        if (commandHelp.handle(text)) return true
        // Display commands first: the way out of the black screen must always work.
        val reply = displayControl.handle(text)
            ?: phoneStatusReader.answer(text)
            ?: volumeControl.handle(text)
            ?: notificationReader.handle(text)
            ?: return false
        voiceEngine.speak(reply)
        return true
    }
}

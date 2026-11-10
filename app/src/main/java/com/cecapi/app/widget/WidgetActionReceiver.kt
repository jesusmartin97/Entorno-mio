package com.cecapi.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cecapi.app.core.util.PhoneStatusReader
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.notifications.NotificationReader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Runs the widget's "Estado" button: speaks the phone status without opening the app. */
class WidgetActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Services {
        fun voiceEngine(): VoiceEngine
        fun phoneStatusReader(): PhoneStatusReader
        fun feedbackCues(): FeedbackCues
        fun notificationReader(): NotificationReader
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS && intent.action != ACTION_NOTIFICATIONS) return
        val wantsNotifications = intent.action == ACTION_NOTIFICATIONS
        val services = EntryPointAccessors.fromApplication(context.applicationContext, Services::class.java)
        // Keep the process alive while the phrase is spoken; a receiver normally gets only a few seconds.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                services.feedbackCues().play(FeedbackCues.Cue.TAP)
                services.voiceEngine().speak(
                    if (wantsNotifications) services.notificationReader().summary() else services.phoneStatusReader().shortReport(),
                )
                delay(400)
                withTimeoutOrNull(9_000) { services.voiceEngine().state.first { it !is VoiceState.Speaking } }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STATUS = "com.cecapi.app.widget.ACTION_STATUS"
        const val ACTION_NOTIFICATIONS = "com.cecapi.app.widget.ACTION_NOTIFICATIONS"
    }
}

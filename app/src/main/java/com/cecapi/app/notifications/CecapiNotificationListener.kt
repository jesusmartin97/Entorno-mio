package com.cecapi.app.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cecapi.app.core.voice.AssistantMode
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives every notification once the person has switched on "Acceso a notificaciones".
 * It only remembers them in [NotificationInbox]; reading them aloud happens when the person asks,
 * except for a short heads-up ("Nueva notificación de WhatsApp", never the content) that can be
 * turned off in Configuración.
 */
@AndroidEntryPoint
class CecapiNotificationListener : NotificationListenerService() {

    @Inject lateinit var inbox: NotificationInbox
    @Inject lateinit var voiceEngine: VoiceEngine
    @Inject lateinit var cues: FeedbackCues
    @Inject lateinit var deviceSettings: DeviceSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = toItem(sbn) ?: return
        val isNewMessage = inbox.lastText(item.key) != item.text
        inbox.add(item)
        if (isNewMessage) scope.launch { announce(item) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        inbox.remove(sbn.key)
    }

    /** Turns a raw notification into something readable, or null when it is noise. */
    private fun toItem(sbn: StatusBarNotification): NotificationItem? {
        val notification = sbn.notification
        if (sbn.packageName == packageName) return null
        val ignoredFlags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE
        if (notification.flags and ignoredFlags != 0) return null
        if (notification.category in NOISY_CATEGORIES) return null

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty().trim()
        if (title.isEmpty() && text.isEmpty()) return null

        return NotificationItem(
            key = sbn.key,
            appName = appLabel(sbn),
            title = title,
            text = text,
            postedAt = sbn.postTime,
        )
    }

    private fun appLabel(sbn: StatusBarNotification): String {
        val packageManager = packageManager
        val fromExtras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sbn.notification.extras.getParcelable("android.appInfo", ApplicationInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            sbn.notification.extras.getParcelable<ApplicationInfo>("android.appInfo")
        }
        return fromExtras?.loadLabel(packageManager)?.toString()
            ?: runCatching { packageManager.getApplicationInfo(sbn.packageName, 0).loadLabel(packageManager).toString() }.getOrNull()
            ?: sbn.packageName.substringAfterLast('.')
    }

    /** A short heads-up, only when it will not talk over a call, do-not-disturb, or the assistant itself. */
    private suspend fun announce(item: NotificationItem) {
        if (!deviceSettings.announceNotifications.first()) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inCall = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audio.mode == AudioManager.MODE_RINGTONE
        val doNotDisturb = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        if (inCall || doNotDisturb) return

        // "silencio": vibration only. "para": nothing at all until the app is opened again.
        if (voiceEngine.mode.value == AssistantMode.STOPPED) return
        cues.play(FeedbackCues.Cue.INCOMING, withSound = voiceEngine.mode.value == AssistantMode.ACTIVE)
        if (voiceEngine.state.value is VoiceState.Idle) {
            voiceEngine.speak("Nueva notificación de ${item.appName}.")
        }
    }

    private companion object {
        val NOISY_CATEGORIES = setOf(
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_STATUS,
        )
    }
}

package com.cecapi.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cecapi.app.MainActivity
import com.cecapi.app.R
import com.cecapi.app.core.voice.AssistantMode
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.WakeWordController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the assistant listening for "hola" after the app is closed. Android only allows this with a
 * foreground service and a notification that cannot be swiped away, so the person always sees
 * that the mic is in use and can stop it with "para" or the notification's "Detener" button.
 * It is optional (Configuración) and never starts by itself: the app starts it while it is visible.
 */
@AndroidEntryPoint
class ListeningService : Service() {

    @Inject lateinit var wakeWordController: WakeWordController
    @Inject lateinit var voiceEngine: VoiceEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            voiceEngine.stopByUser()
            if (!running) stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            if (!startInForeground()) {
                stopSelf()
                return START_NOT_STICKY
            }
            running = true
            wakeWordController.acquire()
            wakeWordController.setBackgroundService(true)
            // "para" (or the notification button) ends the service; it comes back only when the app is opened.
            scope.launch {
                voiceEngine.mode.first { it == AssistantMode.STOPPED }
                stopSelf()
            }
        }
        // Not sticky: Android 12+ forbids restarting a microphone service from the background.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running) {
            wakeWordController.release()
            wakeWordController.setBackgroundService(false)
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(): Boolean = try {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Asistente escuchando", NotificationManager.IMPORTANCE_LOW),
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java).setAction(ACTION_STOP), flags)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_mic)
            .setContentTitle("Asistente activo")
            .setContentText("Di el nombre del asistente. Di \"para\" para detenerlo.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Detener", stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        true
    } catch (e: Exception) {
        // Missing mic permission, or Android refused to start it from here.
        Log.w(TAG, "Could not start the listening service: ${e.message}")
        false
    }

    companion object {
        private const val TAG = "CecapiListening"
        private const val CHANNEL_ID = "assistant_listening"
        private const val NOTIFICATION_ID = 4101
        const val ACTION_STOP = "com.cecapi.app.service.ACTION_STOP"
    }
}

/** Starts and stops [ListeningService] according to the person's setting. */
@Singleton
class BackgroundListening @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceSettings: DeviceSettings,
) {
    /** Call while the app is visible: starts the service if the setting is on, stops it if it is off. */
    suspend fun syncWithSetting() {
        if (deviceSettings.backgroundListening.first()) start() else stop()
    }

    fun start() {
        val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasMic) return
        runCatching { ContextCompat.startForegroundService(context, Intent(context, ListeningService::class.java)) }
    }

    fun stop() {
        context.stopService(Intent(context, ListeningService::class.java))
    }
}

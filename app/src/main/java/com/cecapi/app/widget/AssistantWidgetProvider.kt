package com.cecapi.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cecapi.app.MainActivity
import com.cecapi.app.R

/**
 * Home-screen widget with two big buttons: "Hablar" opens the assistant already listening, and
 * "Estado" reads out the time, battery and connection without opening the app at all.
 * The widget itself holds no state, so it never needs periodic updates.
 */
class AssistantWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_assistant)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val talk = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_LISTEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        views.setOnClickPendingIntent(R.id.widget_talk, PendingIntent.getActivity(context, 0, talk, flags))

        // Explicit intent to a receiver that is not exported: only this widget can trigger it.
        val status = Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_STATUS)
        views.setOnClickPendingIntent(R.id.widget_status, PendingIntent.getBroadcast(context, 1, status, flags))
        val notifications = Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_NOTIFICATIONS)
        views.setOnClickPendingIntent(R.id.widget_notifications, PendingIntent.getBroadcast(context, 2, notifications, flags))
        return views
    }
}

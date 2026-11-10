package com.cecapi.app.feature.modulo10_accesibilidad

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Comprueba si el servicio de accesibilidad está encendido y lleva a los ajustes del teléfono. */
object AccesibilidadActiva {

    fun estaActivo(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val esperado = ComponentName(context, AccesibilidadServicio::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val servicio = info.resolveInfo.serviceInfo
                ComponentName(servicio.packageName, servicio.name) == esperado
            }
    }

    /** Abre la pantalla de accesibilidad del sistema, donde se enciende CECAPI. */
    fun abrirAjustes(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

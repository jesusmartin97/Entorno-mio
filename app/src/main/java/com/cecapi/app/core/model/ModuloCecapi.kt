package com.cecapi.app.core.model

import androidx.compose.ui.graphics.Color
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.theme.ModuleAiAccent
import com.cecapi.app.core.theme.ModuleCameraAccent
import com.cecapi.app.core.theme.ModuleDocumentsAccent
import com.cecapi.app.core.theme.ModuleEnvironmentAccent
import com.cecapi.app.core.theme.ModuleLearningAccent
import com.cecapi.app.core.theme.ModuleVoiceAccent

/**
 * The six functional modules that sit behind the Módulo 1 (Aplicación Principal)
 * dashboard. Stored by [storageCode] in `permisos_modulos.modulo_codigo` so a
 * CECAPI administrator can enable/disable a module per user without a migration.
 */
enum class ModuloCecapi(
    val storageCode: String,
    val route: String,
    val title: String,
    val subtitle: String,
    val voicePrompt: String,
    val accent: Color,
) {
    ASISTENTE_VOZ(
        storageCode = "VOICE_ASSISTANT",
        route = CecapiDestinations.VOICE_ASSISTANT,
        title = "Asistente de Voz IA",
        subtitle = "Comando por voz para todo el sistema",
        voicePrompt = "Abriendo el asistente de voz.",
        accent = ModuleVoiceAccent,
    ),
    ASISTENTE_INTELIGENTE(
        storageCode = "AI_ASSISTANT",
        route = CecapiDestinations.AI_ASSISTANT,
        title = "Asistente Inteligente",
        subtitle = "Preguntas, dudas y apoyo",
        voicePrompt = "Abriendo el asistente inteligente.",
        accent = ModuleAiAccent,
    ),
    LECTOR_DOCUMENTOS(
        storageCode = "DOCUMENT_READER",
        route = CecapiDestinations.DOCUMENT_READER,
        title = "Cámara Inteligente",
        subtitle = "Leer documentos y texto",
        voicePrompt = "Abriendo la cámara inteligente para leer texto.",
        accent = ModuleCameraAccent,
    ),
    CENTRO_SOLICITUDES(
        storageCode = "REQUESTS",
        route = CecapiDestinations.REQUESTS,
        title = "Documentos",
        subtitle = "Solicitudes CECAPI",
        voicePrompt = "Abriendo el centro de solicitudes.",
        accent = ModuleDocumentsAccent,
    ),
    CENTRO_APRENDIZAJE(
        storageCode = "LEARNING",
        route = CecapiDestinations.LEARNING,
        title = "Actividades",
        subtitle = "Mejora auditiva",
        voicePrompt = "Abriendo el centro de aprendizaje.",
        accent = ModuleLearningAccent,
    ),
    ASISTENTE_ENTORNO(
        storageCode = "ENVIRONMENT",
        route = CecapiDestinations.ENVIRONMENT,
        title = "Asistente del Entorno",
        subtitle = "Describe lo que está enfrente",
        voicePrompt = "Abriendo el asistente del entorno.",
        accent = ModuleEnvironmentAccent,
    ),
    ;

    companion object {
        fun fromStorageCode(code: String): ModuloCecapi? = entries.find { it.storageCode == code }
    }
}

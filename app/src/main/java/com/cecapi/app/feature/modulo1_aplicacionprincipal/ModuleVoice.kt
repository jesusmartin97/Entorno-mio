package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.MenuItem
import com.cecapi.app.core.ui.toMenuItem
import com.cecapi.app.core.voice.VoiceText

/**
 * The main menu and the voice phrases that open its entries, shared by the home and dashboard screens.
 *
 * The menu is Cámara, Documentos, Personalización and Configuración. The voice assistant and the
 * smart assistant are the microphone itself, so they are not cards; Entorno and the text reader are
 * two modes of Cámara; Actividades is left out for now. Their code stays in the project.
 */
object ModuleVoice {

    fun menu(available: Collection<ModuloCecapi>): List<MenuItem> = buildList {
        if (ModuloCecapi.LECTOR_DOCUMENTOS in available || ModuloCecapi.ASISTENTE_ENTORNO in available) add(MenuItem.Camera)
        if (ModuloCecapi.CENTRO_SOLICITUDES in available) add(ModuloCecapi.CENTRO_SOLICITUDES.toMenuItem())
        if (ModuloCecapi.CENTRO_APRENDIZAJE in available) add(ModuloCecapi.CENTRO_APRENDIZAJE.toMenuItem())
        add(MenuItem.Chats)
        add(MenuItem.Personalization)
        add(MenuItem.Settings)
    }

    // What people actually say for each entry, most specific first.
    private val keywords: List<Pair<String, List<String>>> = listOf(
        MenuItem.SETTINGS_KEY to listOf("configuracion", "ajustes"),
        MenuItem.PERSONALIZATION_KEY to listOf("personalizacion", "personalizar"),
        MenuItem.CHATS_KEY to listOf("chats", "chat", "conversaciones", "historial"),
        ModuloCecapi.CENTRO_APRENDIZAJE.storageCode to listOf("actividades", "actividad", "ejercicios", "entrenar", "entrenamiento"),
        MenuItem.CAMERA_KEY to listOf("camara", "lector"),
        ModuloCecapi.CENTRO_SOLICITUDES.storageCode to listOf("documentos", "solicitudes", "solicitud"),
    )

    fun isListRequest(spoken: String): Boolean {
        val text = VoiceText.normalize(spoken)
        return listOf("modulos", "opciones", "menu").let { phrases -> VoiceText.hasAny(text, phrases) }
    }

    /** The menu entry named in [spoken], or null. */
    fun matchKey(spoken: String, menu: List<MenuItem>): String? {
        val text = VoiceText.normalize(spoken)
        return keywords.firstOrNull { (key, words) -> menu.any { it.key == key } && VoiceText.hasAny(text, words) }?.first
    }

    /**
     * Phrases that already say which camera mode is wanted ("qué hay enfrente", "lee este texto"),
     * so the person does not have to go through the Cámara screen first.
     */
    fun directCameraRoute(spoken: String): String? {
        val text = VoiceText.normalize(spoken)
        return when {
            listOf("enfrente", "entorno", "describe", "descripcion").let { phrases -> VoiceText.hasAny(text, phrases) } -> CecapiDestinations.ENVIRONMENT
            listOf("leer texto", "lee este", "lee esto", "leer documento", "lee el texto").let { phrases -> VoiceText.hasAny(text, phrases) } ->
                CecapiDestinations.DOCUMENT_READER
            else -> null
        }
    }

    fun spokenList(menu: List<MenuItem>): String {
        val names = menu.map { it.title }
        val list = when (names.size) {
            0 -> "ninguno"
            1 -> names.first()
            else -> names.dropLast(1).joinToString(", ") + " y " + names.last()
        }
        return "El menú tiene: $list. Di el nombre de uno para abrirlo."
    }
}

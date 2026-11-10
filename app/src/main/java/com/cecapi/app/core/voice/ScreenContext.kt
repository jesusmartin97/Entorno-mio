package com.cecapi.app.core.voice

import com.cecapi.app.core.navigation.CecapiDestinations
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which screen is showing, so a command that works everywhere ("dónde estoy") can say it.
 * The activity updates [route] every time the navigation changes.
 */
@Singleton
class ScreenContext @Inject constructor() {
    @Volatile
    var route: String? = null

    /** The screen's name as it is said aloud, or null when the route is unknown. */
    fun spokenName(): String? = when (route) {
        CecapiDestinations.HOME -> "el inicio"
        CecapiDestinations.LOGIN -> "iniciar sesión"
        CecapiDestinations.REGISTER -> "crear cuenta"
        CecapiDestinations.DASHBOARD -> "el menú principal"
        CecapiDestinations.SETTINGS -> "configuración"
        CecapiDestinations.PERSONALIZATION -> "personalización"
        CecapiDestinations.CHATS -> "chats"
        CecapiDestinations.CAMERA_HUB -> "la cámara"
        CecapiDestinations.DOCUMENT_READER -> "el lector de texto"
        CecapiDestinations.ENVIRONMENT -> "la descripción de lo que hay enfrente"
        CecapiDestinations.REQUESTS -> "documentos"
        CecapiDestinations.LEARNING -> "actividades"
        CecapiDestinations.VOICE_ASSISTANT -> "el asistente de voz"
        CecapiDestinations.AI_ASSISTANT -> "el asistente inteligente"
        else -> null
    }
}

package com.cecapi.app.core.navigation

/** Central route registry so every module refers to the same route strings. */
object CecapiDestinations {
    const val HOME = "home"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val PERSONALIZATION = "personalization"
    const val CHATS = "chats"

    /** The "Cámara" card: one entry that leads to reading text or describing what is in front. */
    const val CAMERA_HUB = "camera_hub"

    const val VOICE_ASSISTANT = "voice_assistant"
    const val AI_ASSISTANT = "ai_assistant"
    const val DOCUMENT_READER = "document_reader"
    const val REQUESTS = "requests"
    const val LEARNING = "learning"
    const val ENVIRONMENT = "environment"

    /** Módulo 10: encender el servicio de accesibilidad y elegir el perfil de gestos. */
    const val ACCESIBILIDAD = "accesibilidad"

    /** Only for administrador, directivo and educador — never shown or reachable for alumno/usuario. */
    const val GESTION = "gestion"

    /** Only for alumno and usuario — the opposite of GESTION: see their educador, report a problem. */
    const val SOPORTE = "soporte"
    const val EMERGENCIAS = "emergencias"
    const val REPORTAR_INCIDENTE = "reportar_incidente"

    /** Individual and institution standings in Actividades. Open to anyone signed in, every role. */
    const val RANKING = "ranking"

    /** Each person's own: name, apodo, whether Clasificación shows one or the other, and password. */
    const val EDIT_ACCOUNT = "edit_account"
}

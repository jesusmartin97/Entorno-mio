package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.voice.VoiceText

/** Voice phrases for ending the session or leaving the app, shared by the home and dashboard screens. */
object SessionCommands {

    private val logoutPhrases = listOf(
        "cerrar sesion", "cierra sesion", "cerrar mi sesion", "cierra mi sesion", "terminar sesion",
        "termina sesion", "salir de mi cuenta", "salir de la cuenta", "cerrar cuenta", "logout",
    )

    private val exitPhrases = listOf(
        "cerrar la aplicacion", "cierra la aplicacion", "cerrar aplicacion", "cerrar la app", "cierra la app",
        "salir de la aplicacion", "salir de la app", "salir de aqui", "apagar la aplicacion", "cerrar todo",
    )

    fun isLogout(spoken: String): Boolean = VoiceText.normalize(spoken).let { text -> logoutPhrases.let { phrases -> VoiceText.hasAny(text, phrases) } }

    fun isExitApp(spoken: String): Boolean = VoiceText.normalize(spoken).let { text -> exitPhrases.let { phrases -> VoiceText.hasAny(text, phrases) } }
}

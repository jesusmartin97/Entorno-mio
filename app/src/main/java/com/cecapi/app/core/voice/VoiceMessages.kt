package com.cecapi.app.core.voice

/** Phrases the assistant says in more than one place, so they stay identical everywhere. */
object VoiceMessages {
    const val WAKE_PROMPT = "¿En qué te puedo ayudar?"
    const val WAKE_PROMPT_USTED = "¿En qué le puedo ayudar?"

    /** Said when a phrase is heard outside the app and only the app itself could act on it. */
    const val OPEN_APP = "Eso lo hago dentro de la aplicación. Ábrela para continuar."

    const val MIC_DENIED =
        "Sin el permiso del micrófono no puedo escucharte. Actívalo en los ajustes de la aplicación."

    /**
     * For modules that only make sense with an account — a history with nothing in it to show, because
     * nobody has signed in. Says why, not just "no": the camera and las actividades work without one, this
     * one specifically needs it to have something to keep.
     */
    const val NEEDS_LOGIN =
        "Esto es tu historial, así que necesita una cuenta para guardarse. Crear una es gratis y rápido: " +
            "di crear cuenta. Si ya tienes una, di iniciar sesión."
}

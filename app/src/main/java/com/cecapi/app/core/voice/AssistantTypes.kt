package com.cecapi.app.core.voice

/**
 * What the assistant is allowed to do right now.
 * - [ACTIVE]: talks and listens normally.
 * - [MUTED]: said "silencio" or "espera": stays quiet, but still hears "hola" or its name to come back.
 * - [STOPPED]: said "para": does nothing at all until the person opens the app again.
 */
enum class AssistantMode { ACTIVE, MUTED, STOPPED }

/** How the assistant talks to the person. */
enum class AddressStyle {
    TU,
    USTED,
    ;

    /** Picks the wording for this style: `style.pick("¿Cómo estás?", "¿Cómo está?")`. */
    fun pick(tu: String, usted: String): String = if (this == USTED) usted else tu
}

/** One of the phone's Spanish voices, in words the person can choose from. */
data class VoiceOption(val name: String, val label: String, val needsInternet: Boolean)

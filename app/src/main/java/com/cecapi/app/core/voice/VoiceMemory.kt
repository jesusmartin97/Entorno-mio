package com.cecapi.app.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last thing the user asked for, so they can say "repite la solicitud anterior"
 * without having to phrase it again. Kept in memory only, shared by every screen.
 */
@Singleton
class VoiceMemory @Inject constructor() {

    /** The last command the user gave (never the "repite" command itself). */
    @Volatile
    var lastRequest: String? = null
        private set

    fun remember(request: String) {
        lastRequest = request
    }

    enum class Repeat {
        /** Do the previous request again. */
        REQUEST,

        /** Say the previous answer again. */
        RESPONSE,
    }

    /** What the user wants repeated, or null if [spoken] is not a repeat request. */
    fun repeatKind(spoken: String): Repeat? {
        val text = VoiceText.normalize(spoken)
        val wantsRepeat = listOf(
            "repite", "repetir", "otra vez", "de nuevo", "lo mismo", "solicitud anterior", "comando anterior",
        ).let { phrases -> VoiceText.hasAny(text, phrases) }
        val aboutAnswer = listOf("dijiste", "respuesta", "no escuche", "que dices").let { phrases -> VoiceText.hasAny(text, phrases) }
        return when {
            aboutAnswer && (wantsRepeat || "dijiste" in text || "que dices" in text) -> Repeat.RESPONSE
            wantsRepeat -> Repeat.REQUEST
            else -> null
        }
    }
}

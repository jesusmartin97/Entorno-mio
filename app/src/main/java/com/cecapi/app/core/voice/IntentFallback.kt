package com.cecapi.app.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/** What the AI backend sent back for a phrase the app did not understand. */
data class AiReply(
    /** A command the app already knows ("sube el volumen"): it is run as if the person had said it. */
    val command: String? = null,
    /** A short answer to read aloud. Kept brief on purpose: the person hears it and cannot skim. */
    val answer: String? = null,
    /** True when there is much more to say; the app offers "dime más". */
    val hasMore: Boolean = false,
)

/**
 * Where the AI team plugs in as the "cerebro temporal". When the assistant does not understand a phrase
 * and there is internet, [resolver] gets the phrase (and whether the person asked to go deeper) and returns
 * either a known command or a short answer. Returning null means "I could not help either".
 * No resolver is registered yet.
 */
@Singleton
class IntentFallback @Inject constructor() {
    /**
     * Off until the person turns it on in Configuración: nothing they say is sent to a server by default, and
     * the AI providers' terms do not allow minors. While it is off, [resolver] reads as null, so every caller
     * behaves as if there were no AI at all.
     */
    @Volatile
    var enabled: Boolean = false

    @Volatile
    private var registered: (suspend (text: String, deep: Boolean) -> AiReply?)? = null

    var resolver: (suspend (text: String, deep: Boolean) -> AiReply?)?
        get() = if (enabled) registered else null
        set(value) {
            registered = value
        }

    /** The last question that received an answer, so "dime más" can ask for the deep version of it. */
    @Volatile
    var lastQuestion: String? = null

    /** The question to go deeper on if [spoken] is "dime más" / "explícame a fondo" and there is one; else null. */
    fun deepQuestionFor(spoken: String): String? {
        if (resolver == null) return null
        return if (wantsMore(spoken)) lastQuestion else null
    }

    /** "dime más", "explícame a fondo"... shared with other short-answer sources, like the Wikipedia lookup. */
    fun wantsMore(spoken: String): Boolean = VoiceText.hasAny(VoiceText.normalize(spoken), DEEP_PHRASES)

    private companion object {
        val DEEP_PHRASES = listOf(
            "dime mas", "cuentame mas", "explicame mas", "quiero saber mas", "a fondo", "en detalle",
            "profundiza", "amplia", "investiga",
        )
    }
}

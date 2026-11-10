package com.cecapi.app.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Otras formas de decirlo": tells the person the other ways of saying commands, either everything at once
 * or one area at a time. It works on every screen and offline, because the answers are written in
 * [CommandAlternatives]. It asks which area when the person did not say one, and listens for the answer.
 * Constructing this class registers nothing by itself: [GlobalVoiceCommands] calls [handle].
 */
@Singleton
class CommandHelp @Inject constructor(
    private val voiceEngine: VoiceEngine,
) {
    // After asking "which area?", the next phrase is the answer instead of a normal command.
    @Volatile private var waitingUntil = 0L

    /** True when [spoken] was for the help (it has already spoken its answer). */
    fun handle(spoken: String): Boolean {
        val text = VoiceText.normalize(spoken)
        if (VoiceText.hasAny(text, GENERAL_WORDS)) {
            waitingUntil = 0L
            say(CommandAlternatives.GENERAL_LIST)
            return true
        }
        val waiting = System.currentTimeMillis() < waitingUntil
        waitingUntil = 0L
        val asked = VoiceText.hasAny(text, TRIGGERS)
        if (!waiting && !asked) return false

        val area = areaIn(text)
        return when {
            area != null -> {
                say(area.text + " " + MORE)
                true
            }
            VoiceText.hasAny(text, ALL_WORDS) && (asked || waiting) -> {
                say(CommandAlternatives.all)
                true
            }
            waiting && VoiceText.hasAny(text, CANCEL_WORDS) -> {
                say("De acuerdo.")
                true
            }
            asked -> {
                waitingUntil = System.currentTimeMillis() + WAIT_MS
                voiceEngine.speak(
                    "¿De qué área quieres conocer otras formas de decirlo? Di todas, para escucharlo todo, " +
                        "o el nombre de una: ${CommandAlternatives.spokenNames}.",
                    listenAfter = true,
                )
                true
            }
            // Waiting, but the person said something else: it was probably a normal command.
            else -> false
        }
    }

    private fun say(text: String) = voiceEngine.speak(text)

    private fun areaIn(text: String): CommandAlternatives.Area? =
        CommandAlternatives.areas.firstOrNull { VoiceText.hasAny(text, it.keywords) }

    private companion object {
        const val WAIT_MS = 30_000L
        const val MORE = "Di otras formas de decirlo y el nombre de otra área, para escuchar más."

        val TRIGGERS = listOf(
            "otras formas", "otra forma de decir", "otras maneras", "otra manera de decir", "mas formas", "mas maneras",
            "distintas formas", "diferentes formas", "formas de decir", "sinonimos", "como mas puedo decir",
            "que mas puedo decir", "de que otra forma", "de que otra manera", "alternativas",
        )
        val GENERAL_WORDS = listOf(
            "comandos generales", "lista general", "comandos de toda la aplicacion", "comandos en todas partes",
            "lista de comandos generales",
        )
        val ALL_WORDS =listOf("todas", "todos", "todo", "completo", "completa", "de golpe", "de una vez")
        val CANCEL_WORDS = listOf("cancelar", "cancela", "nada", "olvidalo", "ya no", "no gracias", "ninguna")
    }
}

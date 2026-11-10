package com.cecapi.app.feature.modulo3_asistenteinteligente

import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.voice.AiReply
import com.cecapi.app.core.voice.IntentFallback
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires the Módulo 3 AI backend into [IntentFallback], the one place Home and Dashboard turn when they do
 * not understand a phrase. Without this class nothing ever calls `intentFallback.resolver = ...`, so the
 * getter always reads as null — the AI switch in Configuración turned a gate on that had nothing behind it,
 * and every search that should have reached the AI just said "no entendí" instead.
 * Constructing this class (the activity injects it) registers it, the same way
 * [com.cecapi.app.feature.modulo1_aplicacionprincipal.AssistCommands] registers itself.
 */
@Singleton
class AiResolverSetup @Inject constructor(
    private val intentFallback: IntentFallback,
    private val api: AiAssistantApi,
) {
    init {
        intentFallback.resolver = ::resolve
    }

    /**
     * The backend's `/entender` may name a module (from its fixed phrase list, never guessed from free AI
     * text). It is handed back as that module's title, so the app's own matchers decide what to open and
     * still check the person's permissions. [deep] asks the same question again with a request for more
     * detail; that deeper answer does not offer "dime más" a second time, so the person is never stuck
     * asking for more forever.
     */
    private suspend fun resolve(text: String, deep: Boolean): AiReply? {
        val pregunta = if (deep) "Explica con más detalle: $text" else text
        val entendido = api.understand(pregunta, emptyList()).getOrNull() ?: return null
        entendido.moduleCode?.let { code ->
            val modulo = ModuloCecapi.fromStorageCode(code) ?: return null
            return AiReply(command = modulo.title)
        }
        val respuesta = entendido.answer ?: return null
        return AiReply(answer = respuesta, hasMore = !deep)
    }
}

package com.cecapi.app.notifications

import com.cecapi.app.core.voice.VoiceText
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice commands about notifications. [handle] returns what to say, or null when the phrase is
 * about something else. Reading aloud only ever happens because the person asked for it.
 */
@Singleton
class NotificationReader @Inject constructor(
    private val inbox: NotificationInbox,
    private val access: NotificationAccess,
) {
    fun handle(spoken: String): String? {
        val text = VoiceText.normalize(spoken)
        if ("notificacion" !in text && "notificaciones" !in text) return null
        return when {
            listOf("activar", "activa", "dar acceso", "permiso", "habilitar").let { phrases -> VoiceText.hasAny(text, phrases) } -> enableAccess()
            !access.isEnabled() -> NEEDS_ACCESS
            listOf("borra", "limpia", "limpiar", "elimina").let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                inbox.clear()
                "Listo, borré mi lista de notificaciones. Las de tu teléfono siguen ahí."
            }
            "todas" in text -> readAll()
            listOf("ultima", "nueva", "siguiente", "lee", "leeme", "leer").let { phrases -> VoiceText.hasAny(text, phrases) } -> readNext()
            else -> summary()
        }
    }

    /** "3 notificaciones sin leer: 2 de WhatsApp y 1 de Mensajes." */
    fun summary(): String {
        if (!access.isEnabled()) return NEEDS_ACCESS
        val unread = inbox.unread()
        if (unread.isEmpty()) return "No tienes notificaciones nuevas."
        val byApp = unread.groupingBy { it.appName }.eachCount().entries
            .joinToString(separator = ", ", limit = 4, truncated = "y otras") { (app, n) -> "$n de $app" }
        val noun = if (unread.size == 1) "notificación sin leer" else "notificaciones sin leer"
        return "Tienes ${unread.size} $noun: $byApp. Di léeme la última, o léelas todas."
    }

    private fun readNext(): String {
        val next = inbox.unread().firstOrNull() ?: return "No tienes notificaciones nuevas."
        inbox.markRead(listOf(next.key))
        val left = inbox.unread().size
        return describe(next) + if (left > 0) " Quedan $left más. Di siguiente notificación." else ""
    }

    private fun readAll(): String {
        val unread = inbox.unread()
        if (unread.isEmpty()) return "No tienes notificaciones nuevas."
        val batch = unread.take(MAX_BATCH)
        inbox.markRead(batch.map { it.key })
        val rest = unread.size - batch.size
        return batch.joinToString(" ") { describe(it) } + if (rest > 0) " Quedan $rest más." else ""
    }

    private fun describe(item: NotificationItem): String = buildString {
        append("De ${item.appName}. ")
        if (item.title.isNotEmpty()) append(item.title.trimEnd('.', ':')).append(". ")
        if (item.text.isNotEmpty() && item.text != item.title) append(item.text.take(MAX_TEXT))
        if (item.text.length > MAX_TEXT) append("…")
    }.trim()

    private fun enableAccess(): String {
        if (access.isEnabled()) return "El acceso a notificaciones ya está activado."
        access.openSettings()
        return "Voy a abrir los ajustes. Busca CECAPI en la lista y actívalo. Después regresa a la aplicación."
    }

    private companion object {
        const val MAX_BATCH = 5
        const val MAX_TEXT = 280
        const val NEEDS_ACCESS =
            "Para leer tus notificaciones necesito un permiso. Di activar notificaciones y te llevo a los ajustes."
    }
}

package com.cecapi.app.notifications

import javax.inject.Inject
import javax.inject.Singleton

/** One notification, reduced to what is worth reading aloud. */
data class NotificationItem(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val read: Boolean = false,
)

/**
 * The notifications the listener has seen, newest first. Memory only: nothing is written to disk
 * and nothing leaves the phone, and it empties when the app process ends.
 */
@Singleton
class NotificationInbox @Inject constructor() {
    private val items = mutableListOf<NotificationItem>()

    @Synchronized
    fun add(item: NotificationItem) {
        items.removeAll { it.key == item.key }
        items.add(0, item)
        while (items.size > MAX_ITEMS) items.removeAt(items.lastIndex)
    }

    @Synchronized
    fun remove(key: String) {
        items.removeAll { it.key == key }
    }

    @Synchronized
    fun all(): List<NotificationItem> = items.toList()

    @Synchronized
    fun unread(): List<NotificationItem> = items.filter { !it.read }

    @Synchronized
    fun markRead(keys: Collection<String>) {
        for (i in items.indices) if (items[i].key in keys) items[i] = items[i].copy(read = true)
    }

    @Synchronized
    fun clear() = items.clear()

    /** The text previously seen for [key], to tell a real new message from a re-post of the same one. */
    @Synchronized
    fun lastText(key: String): String? = items.firstOrNull { it.key == key }?.text

    private companion object {
        const val MAX_ITEMS = 20
    }
}

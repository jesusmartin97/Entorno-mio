package com.cecapi.app.core.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes one person's account and everything that hangs from it: their chats, results, saved documents and
 * descriptions (the database deletes those with the account), and the photos of theirs that the reader and
 * the environment assistant kept on the phone. Nothing is kept: this is the "borrar mi cuenta y mis datos" the
 * privacy rules ask for. Photos are deleted first, because their paths live in rows that go with the account.
 */
@Singleton
class AccountEraser @Inject constructor(
    private val database: AppDatabase,
) {
    suspend fun erase(userId: Long) = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        PHOTO_TABLES.forEach { table ->
            database.query(SimpleSQLiteQuery("SELECT ruta_imagen FROM $table WHERE usuario_id = ?", arrayOf<Any>(userId)))
                .use { cursor ->
                    while (cursor.moveToNext()) cursor.getString(0)?.let(paths::add)
                }
        }
        paths.forEach { runCatching { File(it).delete() } }
        // Every table that belongs to a person points to `usuarios` with ON DELETE CASCADE.
        database.openHelper.writableDatabase.execSQL("DELETE FROM usuarios WHERE id = ?", arrayOf<Any>(userId))
        // The delete went around Room's own queries, so tell the screens that watch the tables.
        database.invalidationTracker.refreshVersionsAsync()
    }

    private companion object {
        val PHOTO_TABLES = listOf("documentos_escaneados", "escaneos_entorno")
    }
}

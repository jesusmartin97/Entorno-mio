package com.cecapi.app.core.util

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What the phone has left and what this app takes up. Sizes are in bytes. */
data class StorageUsage(
    val freeBytes: Long,
    val totalBytes: Long,
    val appBytes: Long,
    val photoCount: Int,
    val photoBytes: Long,
    val oldPhotoCount: Int,
    val oldPhotoBytes: Long,
    /** Temporary files the system and the libraries keep; safe to delete. */
    val cacheBytes: Long = 0L,
) {
    val lowSpace: Boolean get() = freeBytes < StorageReport.LOW_SPACE_BYTES
}

/**
 * The storage check: free space on the phone, what the app itself uses, and the scanned photos that
 * the text reader and the environment assistant keep. Old photos can be deleted; nothing is deleted
 * unless the caller asks for it, and callers must ask the person first.
 */
@Singleton
class StorageReport @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val photoFolders: List<File>
        get() = PHOTO_FOLDER_NAMES.map { File(context.filesDir, it) }

    suspend fun read(): StorageUsage = withContext(Dispatchers.IO) {
        val stat = StatFs(context.filesDir.path)
        val photos = photoFolders.flatMap { it.walkTopDown().filter(File::isFile).toList() }
        val cutoff = System.currentTimeMillis() - OLD_PHOTO_MILLIS
        val old = photos.filter { it.lastModified() < cutoff }
        val databases = context.getDatabasePath("cecapi").parentFile
        StorageUsage(
            freeBytes = stat.availableBytes,
            totalBytes = stat.totalBytes,
            appBytes = folderSize(context.filesDir) + folderSize(context.cacheDir) + (databases?.let(::folderSize) ?: 0L),
            photoCount = photos.size,
            photoBytes = photos.sumOf { it.length() },
            oldPhotoCount = old.size,
            oldPhotoBytes = old.sumOf { it.length() },
            cacheBytes = cacheFolders.sumOf { folderSize(it) },
        )
    }

    /** Deletes the scanned photos older than [OLD_PHOTO_DAYS] days. Returns how many files went and how many bytes came back. */
    suspend fun deleteOldPhotos(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - OLD_PHOTO_MILLIS
        var count = 0
        var bytes = 0L
        photoFolders.flatMap { it.walkTopDown().filter(File::isFile).toList() }
            .filter { it.lastModified() < cutoff }
            .forEach { file ->
                val size = file.length()
                if (file.delete()) {
                    count++
                    bytes += size
                }
            }
        count to bytes
    }

    private val cacheFolders: List<File>
        get() = listOfNotNull(context.cacheDir, context.externalCacheDir)

    /** Free space on the phone right now; cheap enough to ask before saving a photo. */
    fun freeBytesNow(): Long = StatFs(context.filesDir.path).availableBytes

    /** True when there is so little room that saving a photo could make the phone struggle. */
    fun criticallyLow(): Boolean = freeBytesNow() < CRITICAL_SPACE_BYTES

    /** Empties the temporary files. Returns the bytes given back. Nothing the person made lives here. */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) { deleteCacheFiles(olderThan = Long.MAX_VALUE) }

    /** Deletes only the temporary files older than [STALE_CACHE_DAYS] days; runs quietly at startup. */
    suspend fun cleanStaleCache(): Long = withContext(Dispatchers.IO) {
        deleteCacheFiles(olderThan = System.currentTimeMillis() - STALE_CACHE_MILLIS)
    }

    private fun deleteCacheFiles(olderThan: Long): Long {
        var freed = 0L
        cacheFolders.forEach { root ->
            // Children first, so folders that end up empty can go too. The cache root itself stays.
            root.walkBottomUp().filter { it != root }.forEach { entry ->
                if (entry.isFile && entry.lastModified() < olderThan) {
                    val size = entry.length()
                    if (entry.delete()) freed += size
                } else if (entry.isDirectory && entry.list().isNullOrEmpty()) {
                    entry.delete()
                }
            }
        }
        return freed
    }

    /** "1,2 GB" or "35 MB": short enough to be said out loud. */
    fun format(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format(Locale("es"), "%.1f gigabytes", mb / 1024.0)
        } else {
            String.format(Locale("es"), "%.0f megabytes", mb)
        }
    }

    /** The report as a sentence for the assistant to say. */
    fun describe(usage: StorageUsage): String = buildString {
        append("Al teléfono le quedan ${format(usage.freeBytes)} libres de ${format(usage.totalBytes)}. ")
        append("La aplicación ocupa ${format(usage.appBytes)}. ")
        append("Guarda ${usage.photoCount} fotos que ocupan ${format(usage.photoBytes)}. ")
        if (usage.cacheBytes > 0) append("La memoria temporal ocupa ${format(usage.cacheBytes)}. ")
        if (usage.lowSpace) append("Queda poco espacio. Te conviene liberar espacio. ")
    }

    private fun folderSize(folder: File): Long =
        if (!folder.exists()) 0L else folder.walkTopDown().filter(File::isFile).sumOf { it.length() }

    companion object {
        /** Below this much free space the app warns the person. */
        const val LOW_SPACE_BYTES = 500L * 1024 * 1024

        const val OLD_PHOTO_DAYS = 30

        /** Below this the app does not save new photos: the phone needs room to keep working. */
        const val CRITICAL_SPACE_BYTES = 100L * 1024 * 1024

        /** Temporary files older than this are cleaned automatically at startup. */
        const val STALE_CACHE_DAYS = 3
        private const val STALE_CACHE_MILLIS = STALE_CACHE_DAYS * 24L * 60 * 60 * 1000
        private const val OLD_PHOTO_MILLIS = OLD_PHOTO_DAYS * 24L * 60 * 60 * 1000

        // Where the reader and Entorno save their photos (inside the app's private files folder).
        private val PHOTO_FOLDER_NAMES = listOf("cecapi_docs", "cecapi_entorno")
    }
}

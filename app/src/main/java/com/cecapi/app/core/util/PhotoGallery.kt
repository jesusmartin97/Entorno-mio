package com.cecapi.app.core.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a photo from the app's private folder into the phone's gallery, in a "Nauren" folder.
 * Android 10 and newer write to the gallery without a storage permission; Android 8 and 9 need
 * WRITE_EXTERNAL_STORAGE, which the caller must have granted. Returns false when the copy fails.
 */
suspend fun saveImageToGallery(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val name = "foto_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, path, name)
        } else {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Nauren")
                .apply { mkdirs() }
            val target = File(folder, name)
            File(path).copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg"), null)
            true
        }
    }.getOrDefault(false)
}

private fun saveWithMediaStore(context: Context, path: String, name: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Nauren")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    val copied = resolver.openOutputStream(uri)?.use { output ->
        File(path).inputStream().use { input -> input.copyTo(output) }
        true
    } ?: false
    if (!copied) {
        resolver.delete(uri, null, null)
        return false
    }
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return true
}

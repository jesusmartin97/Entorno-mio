package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Captura para el Asistente del Entorno. La foto se queda en memoria: no se crea ningún archivo,
 * no se copia a la carpeta de la app y no se toca la galería.
 */
fun ImageCapture.capturarEnMemoria(
    context: Context,
    onFailed: () -> Unit,
    onCaptured: (Bitmap) -> Unit,
) {
    val ejecutor = Executors.newSingleThreadExecutor()
    takePicture(
        ejecutor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = try {
                    val original = image.toBitmap()
                    val girada = girarSegunCamara(original, image.imageInfo.rotationDegrees.toFloat())
                    reducir(girada, LADO_MAXIMO)
                } catch (e: Exception) {
                    null
                } finally {
                    image.close()
                    ejecutor.shutdown()
                }
                if (bitmap == null) {
                    ContextCompat.getMainExecutor(context).execute(onFailed)
                } else {
                    ContextCompat.getMainExecutor(context).execute { onCaptured(bitmap) }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                ejecutor.shutdown()
                ContextCompat.getMainExecutor(context).execute(onFailed)
            }
        },
    )
}

/** Decodifica la imagen elegida de la galería directamente en memoria, orientada y a un tamaño razonable. */
suspend fun bitmapDeUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val fuente = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(fuente) { decodificador, info, _ ->
                val lado = max(info.size.width, info.size.height)
                if (lado > LADO_MAXIMO) {
                    val escala = LADO_MAXIMO.toFloat() / lado
                    decodificador.setTargetSize(
                        (info.size.width * escala).toInt().coerceAtLeast(1),
                        (info.size.height * escala).toInt().coerceAtLeast(1),
                    )
                }
                decodificador.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodificarConExif(context, uri)
        }
    }.getOrNull()?.let { reducir(it, LADO_MAXIMO) }
}

private fun decodificarConExif(context: Context, uri: Uri): Bitmap? {
    val bordes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bordes) }
    if (bordes.outWidth <= 0 || bordes.outHeight <= 0) return null

    var muestreo = 1
    while (max(bordes.outWidth, bordes.outHeight) / muestreo > LADO_MAXIMO) muestreo *= 2
    val original = context.contentResolver.openInputStream(uri)?.use { entrada ->
        BitmapFactory.decodeStream(entrada, null, BitmapFactory.Options().apply { inSampleSize = muestreo })
    } ?: return null

    val grados = runCatching {
        context.contentResolver.openInputStream(uri)?.use { entrada ->
            when (ExifInterface(entrada).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    return girarSegunCamara(original, grados)
}

private fun girarSegunCamara(bitmap: Bitmap, grados: Float): Bitmap {
    if (grados == 0f) return bitmap
    val girada = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(grados) },
        true,
    )
    if (girada !== bitmap) bitmap.recycle()
    return girada
}

private fun reducir(bitmap: Bitmap, ladoMaximo: Int): Bitmap {
    val lado = max(bitmap.width, bitmap.height)
    if (lado <= ladoMaximo) return bitmap
    val escala = ladoMaximo.toFloat() / lado
    val reducida = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * escala).toInt().coerceAtLeast(1),
        (bitmap.height * escala).toInt().coerceAtLeast(1),
        true,
    )
    if (reducida !== bitmap) bitmap.recycle()
    return reducida
}

private const val LADO_MAXIMO = 1280

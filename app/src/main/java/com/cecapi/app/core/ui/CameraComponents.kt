package com.cecapi.app.core.ui

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** One background thread for live frame analysis, shared by every viewfinder so none is leaked. */
private val analysisExecutor by lazy { Executors.newSingleThreadExecutor() }

/**
 * The live camera picture shared by the text reader and the environment assistant. Calls [onReady]
 * with the capture use case once the camera is running, or with null if the camera cannot start.
 * [analyzer], when given, also runs on every preview frame (e.g. the text reader's live framing
 * guidance) — [CameraViewfinder] only wires it in; the caller owns its lifecycle (creating it with
 * `remember` and closing it in a `DisposableEffect`), since this component has no idea what kind of
 * analyzer it is or what closing it means.
 */
@Composable
fun CameraViewfinder(
    hasPermission: Boolean,
    onReady: (ImageCapture?) -> Unit,
    modifier: Modifier = Modifier,
    analyzer: ImageAnalysis.Analyzer? = null,
    onCameraReady: (androidx.camera.core.Camera?) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    Box(
        modifier = modifier.background(CecapiSurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { p ->
                            p.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder().build()
                        // Its own background thread, not the main one: ML Kit's analyze() is called many
                        // times a second and scheduling it on the main executor can visibly stutter the UI
                        // on a slower phone. KEEP_ONLY_LATEST drops a queued frame if one is still being
                        // analyzed, so the hint is never behind by several frames' worth of lag.
                        val analysis = analyzer?.let {
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .apply { setAnalyzer(analysisExecutor, it) }
                        }
                        try {
                            cameraProvider.unbindAll()
                            val camera = try {
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    *listOfNotNull(preview, capture, analysis).toTypedArray(),
                                )
                            } catch (e: Exception) {
                                // Some phones cannot run preview + capture + analysis together: keep the
                                // camera working without the live framing hints rather than fail outright.
                                if (analysis == null) throw e
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    capture,
                                )
                            }
                            onCameraReady(camera)
                            onReady(capture)
                        } catch (_: Exception) {
                            // Camera unavailable (emulator without a virtual camera, etc.)
                            onCameraReady(null)
                            onReady(null)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )
        } else {
            Text(
                "Se necesita permiso de cámara. Actívalo en los ajustes de la aplicación.",
                color = CecapiTextMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp),
            )
        }
    }
}

/** Four corner marks that show where to point the phone. */
@Composable
fun FramingGuide(modifier: Modifier = Modifier, color: Color = CecapiAccent) {
    Canvas(modifier = modifier) {
        val length = size.minDimension * 0.14f
        val stroke = 6.dp.toPx()
        val width = size.width
        val height = size.height

        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * length, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + dy * length), stroke, StrokeCap.Round)
        }

        corner(0f, 0f, 1f, 1f)
        corner(width, 0f, -1f, 1f)
        corner(0f, height, 1f, -1f)
        corner(width, height, -1f, -1f)
    }
}

/**
 * Header de cada pantalla: etiqueta, botón "Volver" y título, como tres elementos sueltos, sin
 * ninguna caja grande envolviéndolos juntos. "Volver" es un botón normal (una pastilla con su
 * propio fondo, como cualquier botón), no una tarjeta especial ni algo metido dentro de un marco.
 * Tampoco lleva un botón de comandos aparte: eso se pide por voz, como recuerda el pie del
 * micrófono (MicPad).
 */
@Composable
fun ScreenTopBar(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    onCommands: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "—— $eyebrow ——",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = eyebrow },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = CecapiTextPrimary,
                textAlign = TextAlign.Center,
            )
        }
        VolverPill(onClick = onBack)
    }
}

@Composable
fun VolverPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    help: String = "Volver. Regresa a la pantalla anterior.",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CecapiSurfaceElevated)
            .border(1.dp, CecapiBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CecapiTextPrimary, modifier = Modifier.size(26.dp))
        Text("Volver", style = MaterialTheme.typography.titleMedium, color = CecapiTextPrimary)
    }
}

/** The big round shutter button. [help] is also what a long press explains. */
@Composable
fun CaptureButton(
    processing: Boolean,
    enabled: Boolean,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 104.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(4.dp, CecapiTextPrimary, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) CecapiAccent else CecapiSurfaceElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help),
        contentAlignment = Alignment.Center,
    ) {
        if (processing) {
            CircularProgressIndicator(color = CecapiBackground, modifier = Modifier.size(size * 0.38f))
        } else {
            Icon(
                Icons.Filled.Camera,
                contentDescription = null,
                tint = CecapiBackground,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

/** Takes a picture into the app's private folder [folder] and reports where it was saved. */
fun ImageCapture.capturePhoto(
    context: Context,
    folder: String,
    onFailed: () -> Unit,
    onSaved: (uri: Uri, path: String) -> Unit,
) {
    val photoFile = File(context.filesDir, "$folder/scan_${System.currentTimeMillis()}.jpg")
        .apply { parentFile?.mkdirs() }
    takePicture(
        ImageCapture.OutputFileOptions.Builder(photoFile).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSaved(output.savedUri ?: Uri.fromFile(photoFile), photoFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) = onFailed()
        },
    )
}

/**
 * Copies a picture the person chose from their gallery into the app's private folder [folder], so the rest of
 * the app treats it like a photo it took itself and never depends on access to the person's media.
 * Returns null when the picture cannot be read.
 */
suspend fun copyPickedImage(context: Context, source: Uri, folder: String): Pair<Uri, String>? =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, "$folder/pick_${System.currentTimeMillis()}.jpg")
                .apply { parentFile?.mkdirs() }
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied == null) null else Uri.fromFile(file) to file.absolutePath
        }.getOrNull()
    }

/**
 * The photo just taken (or chosen from the gallery), shown on its own instead of the live camera feed — a
 * separate "this is the picture you're looking at" area, once there is a picture to look at, for whoever has
 * some usable vision. Decoded downsampled off the main thread, since a full-resolution photo is too big to
 * hold as a Compose ImageBitmap comfortably.
 */
@Composable
fun CapturedPhotoPreview(path: String, modifier: Modifier = Modifier) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })
            }.getOrNull()
        }
    }
    Box(modifier = modifier.background(CecapiSurfaceElevated), contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "La foto que tomaste",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            CircularProgressIndicator(color = CecapiAccent)
        }
    }
}

/** What people say to choose a picture from their own gallery instead of taking one. */
val GALLERY_WORDS = arrayOf(
    "galeria", "mis fotos", "abre mis fotos", "elige una foto", "elegir una foto", "elijo una foto",
    "escoge una foto", "selecciona una foto", "seleccionar foto", "de mi telefono", "de mi celular",
    "imagen guardada", "foto guardada", "fotos guardadas",
)

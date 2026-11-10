package com.cecapi.app.feature.modulo7_entorno

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.ui.CameraViewfinder
import com.cecapi.app.core.ui.CaptureButton
import com.cecapi.app.core.ui.FramingGuide
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.voice.VoiceState
import kotlinx.coroutines.launch

@Composable
fun EnvironmentScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: EnvironmentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) viewModel.onCameraPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val flashSoloParaFotos by viewModel.flashSoloParaFotos.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.cancelarFlashSilencioso() }
    }

    val takePhoto: () -> Unit = {
        val capture = imageCapture
        if (capture == null) {
            viewModel.onCaptureFailed()
        } else {
            if (flashSoloParaFotos) camera?.cameraControl?.enableTorch(true)
            capture.capturarEnMemoria(
                context = context,
                onFailed = {
                    if (flashSoloParaFotos) camera?.cameraControl?.enableTorch(false)
                    viewModel.onCaptureFailed()
                },
                onCaptured = { bitmap ->
                    if (flashSoloParaFotos) camera?.cameraControl?.enableTorch(false)
                    viewModel.onFotoCapturada(bitmap)
                },
            )
        }
    }

    LaunchedEffect(Unit) { viewModel.captureRequests.collect { takePhoto() } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    // Selector del sistema: la app solo ve la imagen elegida y la decodifica en memoria.
    val coroutineScope = rememberCoroutineScope()
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            viewModel.onPickCancelled()
        } else {
            coroutineScope.launch {
                val bitmap = bitmapDeUri(context, uri)
                if (bitmap == null) viewModel.onPickFailed() else viewModel.onImagenElegida(bitmap)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pickRequests.collect {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val foto = uiState.foto
        if (foto == null) {
            CameraViewfinder(
                hasPermission = hasCameraPermission,
                onReady = { imageCapture = it },
                onCameraReady = { camera = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (hasCameraPermission && foto == null) {
            FramingGuide(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f)
                    .aspectRatio(3f / 4f),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTopBar(
                eyebrow = "CÁMARA",
                title = "Qué hay enfrente",
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
                leadingIcon = if (flashSoloParaFotos) {
                    {
                        Icon(
                            imageVector = Icons.Filled.FlashOn,
                            contentDescription = "Flash activado para las próximas fotos",
                            tint = androidx.compose.ui.graphics.Color(0xFFFFD54F),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
            )

            // La foto tiene su propio espacio, separado de los botones: así no se encima con ellos.
            if (foto != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CecapiSurfaceElevated),
                ) {
                    Image(
                        bitmap = foto.asImageBitmap(),
                        contentDescription = "La foto que tomaste",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (uiState.descripcion != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CecapiBackground.copy(alpha = 0.82f))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TopAction(
                        icon = Icons.Filled.Replay,
                        label = "Repetir",
                        help = "Repetir. Vuelve a decir la última descripción.",
                        onClick = viewModel::repetir,
                    )
                }
            }

            val listening = voiceState is VoiceState.Listening
            if (foto != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopAction(
                        icon = Icons.Filled.AddAPhoto,
                        label = "Tomar otra foto",
                        help = "Tomar otra foto. Vuelve a la cámara para tomar una nueva foto. También puedes decir: tomar otra foto.",
                        onClick = viewModel::tomarOtraFoto,
                    )
                    TopAction(
                        icon = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = if (listening) "Escuchando" else "Hablar",
                        help = "Micrófono. Tócalo para hablar — di qué hay enfrente, o lo que necesites.",
                        onClick = viewModel::onMicTapped,
                    )
                }
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Mis fotos",
                    help = "Mis fotos. Abre el selector de fotos de tu teléfono para que elijas una imagen que ya tienes " +
                        "y te la describa. La aplicación solo ve la foto que tú elijas y no la guarda. También puedes decir: elige una foto.",
                    onClick = viewModel::pedirGaleria,
                )
                CaptureButton(
                    processing = uiState.isProcessing,
                    enabled = imageCapture != null && !uiState.isProcessing,
                    help = "Botón de captura. Toma una foto con cuenta regresiva de tres segundos para describir " +
                        "lo que hay enfrente. También puedes decir: qué hay enfrente.",
                    onClick = viewModel::pedirFotoConCuenta,
                )
                // Mismo ancho que "Mis fotos" para que el obturador quede centrado.
                TopAction(
                    icon = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (listening) "Escuchando" else "Hablar",
                    help = "Micrófono. Tócalo para hablar — di qué hay enfrente, o lo que necesites.",
                    onClick = viewModel::onMicTapped,
                )
            }
        }
    }
}

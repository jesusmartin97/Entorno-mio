package com.cecapi.app.feature.modulo4_lectordocumentos

import com.cecapi.app.core.theme.ModuleSection
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextPrimary
import com.cecapi.app.core.theme.ModuleCameraAccent
import com.cecapi.app.core.theme.ModuleLearningAccent
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.VolverPill
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.CameraViewfinder
import com.cecapi.app.core.ui.CaptureButton
import com.cecapi.app.core.ui.FramingGuide
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.capturePhoto
import com.cecapi.app.core.ui.copyPickedImage
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState
import kotlinx.coroutines.launch

@Composable
fun DocumentReaderScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: DocumentReaderViewModel = hiltViewModel(),
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

    // Lives on the screen (not the ViewModel): it holds an ML Kit recognizer that must close with the
    // camera, not survive configuration changes. Told "así está bien, toca el botón" and where to move the
    // phone, said out loud, since there is no viewfinder to look at for someone who cannot see it.
    val framingAnalyzer = remember { TextFramingAnalyzer(onHint = viewModel::onFramingHint) }
    DisposableEffect(Unit) { onDispose { framingAnalyzer.close() } }

    val takePhoto: () -> Unit = {
        val capture = imageCapture
        if (capture == null) {
            viewModel.onCaptureFailed()
        } else {
            capture.capturePhoto(
                context = context,
                folder = "cecapi_docs",
                onFailed = viewModel::onCaptureFailed,
            ) { uri, path -> viewModel.onPhotoCaptured(uri, path) }
        }
    }

    LaunchedEffect(Unit) { viewModel.captureRequests.collect { takePhoto() } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    // The system picture picker: the person chooses one picture and the app sees only that one.
    val coroutineScope = rememberCoroutineScope()
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onSavePhoto() else viewModel.onSavePhotoDenied()
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            viewModel.onPickCancelled()
        } else {
            coroutineScope.launch {
                val copy = copyPickedImage(context, uri, "cecapi_docs")
                if (copy == null) viewModel.onPickFailed() else viewModel.onPhotoCaptured(copy.first, copy.second)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pickRequests.collect {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val hayDocumento = uiState.parrafos.isNotEmpty()

    if (hayDocumento) {
        // Panel de lectura: fondo negro plano (ya no se ve la foto capturada detrás), encabezado
        // compacto (no el ScreenTopBar grande de las demás pantallas) y un botón grande por acción,
        // tal cual el panel de lectura del rediseño de Figma Make.
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Leer texto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = CecapiTextPrimary)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Párrafo ${uiState.parrafoActual + 1} de ${uiState.parrafos.size}.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = CecapiTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                VolverPill(
                    onClick = onBack,
                    help = "Volver al menú. Sale del lector de texto.",
                )
                BigActionCard(
                    icon = if (uiState.estaLeyendo) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = if (uiState.estaLeyendo) "PAUSAR" else "CONTINUAR",
                    tint = CecapiAccent,
                    help = if (uiState.estaLeyendo) {
                        "Pausar. Detiene la lectura hasta que digas continúa."
                    } else {
                        "Continuar. Sigue leyendo el párrafo donde te quedaste."
                    },
                    onClick = if (uiState.estaLeyendo) viewModel::pausarLectura else viewModel::continuarLectura,
                )
                BigActionCard(
                    icon = Icons.Filled.Replay,
                    label = "REPETIR",
                    tint = RepetirTint,
                    help = "Repetir. Lee el documento otra vez desde el principio.",
                    onClick = viewModel::repetirLectura,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ControlFlecha(
                        icon = Icons.Filled.SkipPrevious,
                        label = "ANTERIOR",
                        tint = FlechaTint,
                        help = "Párrafo anterior. Vuelve un párrafo atrás.",
                        onClick = viewModel::anteriorParrafo,
                        flechaALaDerecha = false,
                        modifier = Modifier.weight(1f),
                    )
                    ControlFlecha(
                        icon = Icons.Filled.SkipNext,
                        label = "SIGUIENTE",
                        tint = FlechaTint,
                        help = "Párrafo siguiente. Salta al siguiente párrafo.",
                        onClick = viewModel::siguienteParrafo,
                        flechaALaDerecha = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                BigBtn(
                    icon = Icons.Filled.Save,
                    label = "GUARDAR FOTO",
                    section = Sections.Camera,
                    onClick = {
                        val needsStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsStorage) storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.onSavePhoto()
                    },
                )
                BigBtn(
                    icon = Icons.Filled.AddAPhoto,
                    label = "TOMAR OTRA FOTO",
                    section = OtraFotoSection,
                    size = BigBtnSize.Hero,
                    onClick = viewModel::onRetakePhoto,
                )
            }
        }
    } else {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraViewfinder(
            hasPermission = hasCameraPermission,
            onReady = { imageCapture = it },
            modifier = Modifier.fillMaxSize(),
            analyzer = framingAnalyzer,
        )

        if (hasCameraPermission) {
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
                    title = "Leer texto",
                    onBack = onBack,
                    onCommands = viewModel::onCommandsRequested,
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SuggestionChip(label = "toma la foto", onClick = viewModel::pedirCaptura)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopAction(
                        icon = Icons.Filled.PhotoLibrary,
                        label = "Mis fotos",
                        help = "Mis fotos. Abre el selector de fotos de tu teléfono para que elijas una imagen que ya tienes. " +
                            "La aplicación solo ve la foto que tú elijas. También puedes decir: elige una foto.",
                        onClick = viewModel::pedirGaleria,
                    )
                    CaptureButton(
                        processing = uiState.isProcessing,
                        enabled = imageCapture != null && !uiState.isProcessing,
                        help = "Botón de captura. Tócalo para prepararte y tócalo otra vez para tomar la foto. " +
                            "También puedes decir: toma la foto.",
                        onClick = { if (viewModel.onBotonCapturaPresionado()) takePhoto() },
                        size = 136.dp,
                    )
                    // Mismo ancho que "Mis fotos" para que el obturador quede centrado, pero ahora es
                    // un micrófono real — no solo un espacio vacío — para no depender de que alguien
                    // adivine que ya está escuchando.
                    val listening = voiceState is VoiceState.Listening
                    TopAction(
                        icon = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = if (listening) "Escuchando" else "Hablar",
                        help = "Micrófono. Tócalo para hablar — toca la foto, o describe lo que necesitas.",
                        tint = if (listening) CecapiAccent else CecapiTextPrimary,
                        onClick = viewModel::onMicTapped,
                    )
                }
            }
        }
    }
}

/** One full-width, same-size button: a big icon badge plus a big bold label, easy to find and tap. */
private val RepetirTint = Color(0xFFF59E0B)
private val FlechaTint = Color(0xFF38BDF8)
private val OtraFotoSection = ModuleSection(Color(0xFFA78BFA), Color(0xFF2A2340))

/** Control con flecha: la flecha va del lado izquierdo o derecho según la dirección, y el texto del otro lado. */
@Composable
private fun ControlFlecha(
    icon: ImageVector,
    label: String,
    tint: Color,
    help: String,
    onClick: () -> Unit,
    flechaALaDerecha: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val flecha: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .border(2.dp, tint, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!flechaALaDerecha) flecha()
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            modifier = Modifier.weight(1f),
            textAlign = if (flechaALaDerecha) TextAlign.Start else TextAlign.End,
        )
        if (flechaALaDerecha) flecha()
    }
}

@Composable
private fun BigActionCard(
    icon: ImageVector,
    label: String,
    tint: Color,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .border(2.dp, tint, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val iconShape = RoundedCornerShape(15.dp)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(iconShape)
                .background(tint.copy(alpha = 0.2f))
                .border(2.dp, tint, iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CecapiTextPrimary)
    }
}

package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MenuItem
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.OfflineBanner
import com.cecapi.app.core.util.openTtsSettings
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState

@Composable
fun HomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToModule: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val assistantName by viewModel.assistantName.collectAsState()
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.onMicTapped() else viewModel.onMicPermissionDenied()
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is HomeNavEvent.GoToLogin -> onNavigateToLogin()
                is HomeNavEvent.GoToRegister -> onNavigateToRegister()
                is HomeNavEvent.GoToModule -> onNavigateToModule(event.route)
                HomeNavEvent.GoToSettings -> onNavigateToSettings()
                HomeNavEvent.ExitApp -> (context as? Activity)?.finishAffinity()
            }
        }
    }

    // Ask for the mic on entry so "hola" works from the first launch, without needing a tap.
    val entryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (!granted) viewModel.onMicPermissionDenied()
    }
    LaunchedEffect(Unit) {
        if (!hasMicPermission) entryPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Home stays alive under Login/Register; only react to speech while it is actually showing.
    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }

    // "Hola" (or the assistant's name) opens the mic, but only while this screen is showing.
    DisposableEffect(hasMicPermission) {
        viewModel.setWakeWordEnabled(hasMicPermission)
        onDispose { viewModel.setWakeWordEnabled(false) }
    }

    val listening = voiceState is VoiceState.Listening
    val menu = viewModel.menu

    // The mic bar is fixed over the scrolling content (like the Figma prototype), not inline
    // in the scroll flow, so it is always reachable no matter how far the person has scrolled.
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
            .padding(bottom = 250.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "VOZACCESO",
                style = CecapiEyebrowStyle,
                color = CecapiTextMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Hola, estoy",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (listening) "escuchándote." else "esperándote.",
                style = MaterialTheme.typography.headlineLarge,
                color = CecapiAccent,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "En colaboración con CECAPI",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigBtn(
                    icon = Icons.Filled.Lock,
                    label = "Iniciar sesión",
                    variant = BigBtnVariant.Accent,
                    half = true,
                    onClick = onNavigateToLogin,
                    modifier = Modifier.weight(1f),
                )
                BigBtn(
                    icon = Icons.Filled.PersonAdd,
                    label = "Crear cuenta",
                    half = true,
                    onClick = viewModel::onCrearCuentaSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = CecapiBorder, thickness = 1.dp)
            // Cámara: una sola tarjeta (antes había dos, "Cámara Inteligente" y "Asistente del
            // Entorno", que se veían como duplicadas). Lleva al mismo hub que ya elige entre leer
            // texto o describir el entorno. Clasificación, Actividades y Chats piden cuenta, así
            // que no se muestran aquí — solo aparecen ya con la sesión iniciada.
            BigBtn(
                icon = Icons.Filled.CameraAlt,
                label = "Cámara",
                section = Sections.Camera,
                onClick = { viewModel.onMenuItemSelected(MenuItem.Camera) },
            )
            menu.firstOrNull { it.key == "REQUESTS" }?.let { documentos ->
                BigBtn(
                    icon = Icons.Filled.Description,
                    label = documentos.title,
                    section = Sections.Documents,
                    onClick = { viewModel.onMenuItemSelected(documentos) },
                )
            }
            BigBtn(
                icon = Icons.Filled.Tune,
                label = "Personalización",
                section = Sections.Personalization,
                onClick = { viewModel.onMenuItemSelected(MenuItem.Personalization) },
            )
            BigBtn(
                icon = Icons.Filled.Settings,
                label = "Configuración",
                section = Sections.Settings,
                onClick = viewModel::onSettingsSelected,
            )
            BigBtn(
                icon = Icons.AutoMirrored.Filled.Help,
                label = "Ayuda y soporte",
                section = Sections.Ayuda,
                onClick = viewModel::onAyudaSelected,
            )
        }

        // What the voice says is not repeated on screen. Only what needs attention stays visible.
        if (!hasMicPermission) {
            NoticeBanner(VoiceMessages.MIC_DENIED, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
        } else if (voiceState is VoiceState.Error) {
            val error = voiceState as VoiceState.Error
            NoticeBanner(
                error.message,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
                actionLabel = if (error.openTtsSettings) "Elegir motor de voz" else null,
                onAction = if (error.openTtsSettings) {
                    { openTtsSettings(context) }
                } else null,
            )
        }
        if (!isOnline) {
            OfflineBanner(modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
        }

    }

        // The microphone is the main control of the whole app: fixed over the scrolling
        // content above, so it is always reachable no matter how far the person scrolled.
        MicPad(
            onDoubleTap = viewModel::onMicDoubleTap,
            listening = listening,
            hint = when {
                listening -> "Escuchando…"
                !hasMicPermission -> "Toca aquí para dar permiso"
                assistantName.isBlank() -> "Toca aquí para hablar"
                else -> "Di \"$assistantName\" o toca aquí"
            },
            onClick = {
                if (hasMicPermission) {
                    viewModel.onMicTapped()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

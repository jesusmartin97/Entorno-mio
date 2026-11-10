package com.cecapi.app.feature.modulo2_asistentevoz

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.voice.VoiceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceAssistantScreen(
    onBack: () -> Unit,
    onNavigateToModule: (String) -> Unit,
    viewModel: VoiceAssistantViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val recentCommands by viewModel.recentCommands.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect(onNavigateToModule)
    }

    val listening = voiceState is VoiceState.Listening

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).padding(bottom = 230.dp)) {
        ScreenTopBar(
            eyebrow = "ASISTENTE DE VOZ",
            title = "Comando por voz",
            onBack = onBack,
            onCommands = { viewModel.onMicTapped() },
        )

        VoiceCaptionBubble(
            text = (voiceState as? VoiceState.Speaking)?.text ?: "Di el nombre de un módulo para abrirlo.",
        )

        Text(
            "HISTORIAL DE COMANDOS",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        )

        if (recentCommands.isEmpty()) {
            Text(
                "Aún no has dado ningún comando.",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
        } else {
            val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es", "MX")) }
            LazyColumn {
                items(recentCommands, key = { it.id }) { comando ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "\"${comando.textoComando}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            formatter.format(Date(comando.fechaHora)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CecapiTextMuted,
                        )
                    }
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di el nombre de un módulo, o toca aquí",
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

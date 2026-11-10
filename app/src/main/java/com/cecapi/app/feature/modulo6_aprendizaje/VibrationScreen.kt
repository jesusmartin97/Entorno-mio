package com.cecapi.app.feature.modulo6_aprendizaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.VoiceMicButton
import com.cecapi.app.core.voice.VoiceState

@Composable
fun VibrationScreen(
    onBack: () -> Unit = {},
    viewModel: VibrationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // El ViewModel avisa cuando la persona dice (o toca) "Salir" al final.
    LaunchedEffect(Unit) { viewModel.salir.collect { onBack() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    val onMicClick = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        ScreenTopBar(
            eyebrow = "ACTIVIDADES",
            title = "Vibración",
            onBack = onBack,
            onCommands = {},
        )

        val ejercicio = uiState.ejercicioActual
        when {
            // ---- Pantalla de puntaje final -------------------------------------------------
            uiState.terminado -> {
                Text(
                    uiState.mensajeFinal ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CecapiSuccess,
                    modifier = Modifier.padding(top = 32.dp),
                )

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    VoiceMicButton(
                        isListening = voiceState is VoiceState.Listening,
                        onClick = onMicClick,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        "\"Repetir\"",
                        color = CecapiAccent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = viewModel::onRepetirActividad)
                            .padding(vertical = 8.dp),
                    )
                    Text(
                        "\"Salir\"",
                        color = CecapiAccent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = viewModel::onSalir)
                            .padding(vertical = 8.dp),
                    )
                }
            }

            ejercicio == null -> {
                Text(
                    "No hay ejercicios disponibles todavía.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }

            // ---- Ejercicio en curso --------------------------------------------------------
            else -> {
                Text(
                    "Ejercicio ${uiState.indiceActual + 1} de ${uiState.ejercicios.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )

                VoiceCaptionBubble(
                    text = (voiceState as? VoiceState.Speaking)?.text ?: ejercicio.instruccion,
                    modifier = Modifier.padding(top = 16.dp),
                )

                // No se muestra el titulo del ejercicio porque revela la respuesta ("Patron corto...").
                val intensidad = when (ejercicio.intensidad) {
                    1 -> "suave"
                    2 -> "media"
                    else -> "fuerte"
                }
                Text(
                    "Intensidad: $intensidad",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )

                if (uiState.reproduciendo) {
                    Text(
                        "Vibrando...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiAccent,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    VoiceMicButton(
                        isListening = voiceState is VoiceState.Listening,
                        onClick = onMicClick,
                    )
                }

                Text(
                    "\"Repetir vibración\"",
                    color = CecapiAccent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = viewModel::onRepetirVibracion)
                        .padding(top = 4.dp, bottom = 12.dp),
                )

                uiState.feedback?.let { feedback ->
                    Text(
                        feedback,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (uiState.feedbackCorrecto) CecapiSuccess else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "\"Siguiente ejercicio\"",
                        color = CecapiAccent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = viewModel::onSiguienteEjercicio)
                            .padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

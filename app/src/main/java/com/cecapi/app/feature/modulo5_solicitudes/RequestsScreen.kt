package com.cecapi.app.feature.modulo5_solicitudes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.voice.VoiceState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh

@Composable
fun RequestsScreen(
    onBack: () -> Unit = {},
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val plantillas by viewModel.plantillas.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    val listening = voiceState is VoiceState.Listening
    val enPregunta = uiState.plantillaSeleccionada != null

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .padding(bottom = 230.dp),
    ) {
        ScreenTopBar(
            eyebrow = "DOCUMENTOS",
            title = "Solicitudes",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        when {
            uiState.textoGenerado != null -> {
                VoiceCaptionBubble(text = uiState.textoGenerado!!, modifier = Modifier.padding(top = 20.dp))
                BigBtn(
                    icon = Icons.Filled.Refresh,
                    label = "Crear otra solicitud",
                    variant = BigBtnVariant.Accent,
                    onClick = viewModel::onReiniciar,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }

            enPregunta -> {
                var respuestaTexto by remember(uiState.indiceActual) { mutableStateOf("") }

                VoiceCaptionBubble(
                    text = (voiceState as? VoiceState.Speaking)?.text ?: uiState.preguntaActual.orEmpty(),
                    modifier = Modifier.padding(top = 20.dp),
                )

                OutlinedTextField(
                    value = respuestaTexto,
                    onValueChange = { respuestaTexto = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    placeholder = { Text("O escribe tu respuesta aquí") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
                )
                BigBtn(
                    icon = Icons.Filled.ArrowForward,
                    label = "Continuar",
                    variant = BigBtnVariant.Accent,
                    enabled = respuestaTexto.isNotBlank(),
                    onClick = { viewModel.onAnswerProvided(respuestaTexto) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            else -> {
                Text(
                    "PLANTILLAS DISPONIBLES",
                    style = CecapiEyebrowStyle,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(plantillas, key = { it.id }) { plantilla ->
                        BigBtn(
                            icon = Icons.Filled.Description,
                            label = plantilla.titulo,
                            sub = plantilla.descripcion,
                            section = Sections.Documents,
                            onClick = { viewModel.onPlantillaSelected(plantilla) },
                        )
                    }
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else if (enPregunta) "Toca aquí para responder" else "Toca aquí para hablar",
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

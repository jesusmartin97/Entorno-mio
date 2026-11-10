package com.cecapi.app.feature.modulo1_aplicacionprincipal

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.voice.VoiceState

@Composable
fun ReportarIncidenteScreen(
    onBack: () -> Unit,
    viewModel: ReportarIncidenteViewModel = hiltViewModel(),
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    val destinos = remember { viewModel.destinatarios() }
    var mensaje by remember { mutableStateOf("") }
    var anonimo by remember { mutableStateOf<Boolean?>(null) }
    var destino by remember { mutableStateOf<RolUsuario?>(null) }

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.enviado.collect { onBack() } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 230.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTopBar(
                eyebrow = "REPORTAR",
                title = "Reportar un incidente",
                onBack = viewModel::onBack,
                onCommands = {},
            )

            if (destinos.isEmpty()) {
                NoticeBanner("Como administrador no envías reportes: los recibes en Gestión.")
            } else {
                OutlinedTextField(
                    value = mensaje,
                    onValueChange = { mensaje = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Qué pasó *") },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                )

                Text("¿CON TU NOMBRE O ANÓNIMO? *", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigBtn(
                        icon = Icons.Filled.Person,
                        label = "Con mi nombre",
                        variant = if (anonimo == false) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                        half = true,
                        onClick = { anonimo = false },
                        modifier = Modifier.weight(1f),
                    )
                    BigBtn(
                        icon = Icons.Filled.Person,
                        label = "Anónimo",
                        variant = if (anonimo == true) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                        half = true,
                        onClick = { anonimo = true },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("¿A QUIÉN LO ENVÍAS? *", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                destinos.forEach { rol ->
                    BigBtn(
                        icon = Icons.Filled.Person,
                        label = etiquetaDestino(rol),
                        variant = if (destino == rol) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                        size = com.cecapi.app.core.ui.BigBtnSize.Secondary,
                        onClick = { destino = rol },
                    )
                }

                BigBtn(
                    icon = Icons.Filled.Check,
                    label = "Enviar reporte",
                    enabled = mensaje.isNotBlank() && anonimo != null && destino != null,
                    onClick = { destino?.let { viewModel.onEnviar(mensaje, it, anonimo == true) } },
                )
            }
        }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Toca aquí para hablar",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun etiquetaDestino(rol: RolUsuario): String = when (rol) {
    RolUsuario.EDUCADOR -> "A un educador"
    RolUsuario.DIRECTIVO -> "A un directivo"
    RolUsuario.ADMINISTRADOR -> "A un administrador"
    else -> rol.codigo
}

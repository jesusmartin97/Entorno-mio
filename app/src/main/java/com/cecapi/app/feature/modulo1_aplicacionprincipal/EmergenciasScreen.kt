package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.voice.VoiceState

@Composable
fun EmergenciasScreen(
    onBack: () -> Unit,
    viewModel: EmergenciasViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val contactos by viewModel.contactos.collectAsState()
    val listening = voiceState is VoiceState.Listening
    var verContactos by remember { mutableStateOf(false) }
    var ubicacionNegada by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var relacion by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val permisoUbicacion = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ubicacionNegada = !granted
        if (granted) compartirUbicacion(context)
    }

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
                eyebrow = "EMERGENCIAS",
                title = "Emergencias",
                onBack = viewModel::onBack,
                onCommands = {},
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigBtn(
                    icon = Icons.Filled.Phone,
                    label = "Llamar a mi contacto",
                    section = Sections.Emergency,
                    size = BigBtnSize.Secondary,
                    half = true,
                    onClick = { verContactos = true },
                    modifier = Modifier.weight(1f),
                )
                BigBtn(
                    icon = Icons.Filled.LocationOn,
                    label = "Enviar mi ubicación",
                    section = Sections.Emergency,
                    size = BigBtnSize.Secondary,
                    half = true,
                    onClick = {
                        val tiene = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                        if (tiene) compartirUbicacion(context) else permisoUbicacion.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (ubicacionNegada) {
                Text(
                    "Necesito permiso de ubicación para compartirla. Puedes dártelo en los ajustes del teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
            }

            BigBtn(
                icon = Icons.Filled.Warning,
                label = "Llamar al 911",
                variant = BigBtnVariant.Danger,
                onClick = { llamar(context, "911") },
            )

            if (verContactos) {
                Text("MIS CONTACTOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                if (contactos.isEmpty()) {
                    Text(
                        "Todavía no tienes contactos. Agrega uno abajo.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CecapiTextMuted,
                    )
                }
                contactos.forEach { contacto ->
                    BigBtn(
                        icon = Icons.Filled.Phone,
                        label = "Llamar a ${contacto.nombre}",
                        section = Sections.Emergency,
                        size = BigBtnSize.Secondary,
                        onClick = { llamar(context, contacto.telefono) },
                    )
                    BigBtn(
                        icon = Icons.Filled.Close,
                        label = "Quitar a ${contacto.nombre}",
                        size = BigBtnSize.Secondary,
                        onClick = { viewModel.onBorrarContacto(contacto) },
                    )
                }
            }

            Text("AGREGAR CONTACTO", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            Text(
                "Te recomendamos guardar a un adulto y a una persona de tu confianza fuera de la escuela.",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre *") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
            )
            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Teléfono *") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
            )
            OutlinedTextField(
                value = relacion,
                onValueChange = { relacion = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Relación (opcional)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
            )
            BigBtn(
                icon = Icons.Filled.Check,
                label = "Guardar contacto",
                enabled = nombre.isNotBlank() && telefono.isNotBlank(),
                onClick = {
                    viewModel.onAgregarContacto(nombre, telefono, relacion)
                    nombre = ""
                    telefono = ""
                    relacion = ""
                },
            )
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

/** Marca el número: el teléfono abre el marcador y la persona confirma la llamada. */
private fun llamar(context: Context, numero: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")))
}

@SuppressLint("MissingPermission")
private fun compartirUbicacion(context: Context) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val ubicacion = lm?.let {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstNotNullOfOrNull { proveedor -> runCatching { it.getLastKnownLocation(proveedor) }.getOrNull() }
    }
    val texto = if (ubicacion != null) {
        "Estoy aquí: https://maps.google.com/?q=${ubicacion.latitude},${ubicacion.longitude}"
    } else {
        "No pude obtener mi ubicación en este momento."
    }
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    context.startActivity(Intent.createChooser(envio, "Enviar mi ubicación"))
}

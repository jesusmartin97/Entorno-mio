package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.theme.CecapiBorder
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Close
import com.cecapi.app.core.ui.BigBtnSize
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.voice.VoiceState

private enum class VistaAyuda { Menu, Preguntas, Manual, Reportes, Escribir, Responder }

/** Ayuda y soporte: problemas con la app, información y ayuda, y mis reportes. Reportar y ver reportes piden cuenta. */
@Composable
fun SoporteScreen(
    onBack: () -> Unit,
    viewModel: SoporteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    var vista by remember { mutableStateOf(VistaAyuda.Menu) }
    var mensajeRespondido by remember { mutableStateOf<IncidenciaEntity?>(null) }
    val conCuenta = viewModel.hayCuenta()
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 230.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTopBar(
                eyebrow = "AYUDA",
                title = "Ayuda y soporte",
                onBack = { if (vista == VistaAyuda.Menu) onBack() else vista = VistaAyuda.Menu },
                onCommands = viewModel::onCommandsRequested,
            )

            when (vista) {
                VistaAyuda.Menu -> if (state.rol == RolUsuario.ADMINISTRADOR) {
                    MensajesParaAdmin(
                        mensajes = viewModel.mensajesDeSoporte.collectAsState().value,
                        onResolver = viewModel::marcarResuelto,
                        onResponder = {
                            mensajeRespondido = it
                            vista = VistaAyuda.Responder
                        },
                        onEliminar = viewModel::eliminarMensaje,
                        onPreguntas = { vista = VistaAyuda.Preguntas },
                        onManual = { vista = VistaAyuda.Manual },
                    )
                } else {
                    Text("PROBLEMAS CON LA APP", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    BigBtn(
                        icon = Icons.Filled.ReportProblem,
                        label = "Reportar un problema técnico",
                        section = Sections.Ayuda,
                        enabled = !state.esperandoMensaje,
                        onClick = { if (conCuenta) vista = VistaAyuda.Escribir },
                    )
                    BigBtn(
                        icon = Icons.Filled.ReportProblem,
                        label = "Reportar con mi voz",
                        section = Sections.Ayuda,
                        enabled = !state.esperandoMensaje,
                        onClick = { if (conCuenta) viewModel.pedirMensaje() },
                    )
                    if (!conCuenta) {
                        NoticeBanner("Para reportar un problema o ver tus reportes, inicia sesión.")
                    }

                    Text("INFORMACIÓN Y AYUDA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    BigBtn(
                        icon = Icons.Filled.Info,
                        label = "Preguntas frecuentes",
                        section = Sections.Ayuda,
                        onClick = { vista = VistaAyuda.Preguntas },
                    )
                    BigBtn(
                        icon = Icons.AutoMirrored.Filled.Help,
                        label = "Manual de uso",
                        section = Sections.Ayuda,
                        onClick = { vista = VistaAyuda.Manual },
                    )

                    if (conCuenta) {
                        BigBtn(
                            icon = Icons.Filled.List,
                            label = "Mis reportes enviados",
                            section = Sections.Ayuda,
                            onClick = { vista = VistaAyuda.Reportes },
                        )
                    }
                }

                VistaAyuda.Responder -> {
                    var respuesta by remember { mutableStateOf("") }
                    val mensaje = mensajeRespondido
                    if (mensaje != null) {
                        BloqueTexto(titulo = mensaje.mensaje, cuerpo = "Escribe tu respuesta abajo.")
                        OutlinedTextField(
                            value = respuesta,
                            onValueChange = { respuesta = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Respuesta *") },
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                        )
                        BigBtn(
                            icon = Icons.Filled.Check,
                            label = "Enviar respuesta",
                            enabled = respuesta.isNotBlank(),
                            onClick = {
                                viewModel.responder(mensaje, respuesta)
                                vista = VistaAyuda.Menu
                            },
                        )
                    }
                }

                VistaAyuda.Escribir -> {
                    var texto by remember { mutableStateOf("") }
                    Text("REPORTAR UN PROBLEMA TÉCNICO", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Qué pasó *") },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                    )
                    BigBtn(
                        icon = Icons.Filled.Check,
                        label = "Enviar reporte",
                        enabled = texto.isNotBlank(),
                        onClick = {
                            viewModel.enviarEscrito(texto)
                            vista = VistaAyuda.Menu
                        },
                    )
                }

                VistaAyuda.Preguntas -> {
                    Text("PREGUNTAS FRECUENTES", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    PreguntasFrecuentes.forEach { (pregunta, respuesta) ->
                        BloqueTexto(titulo = pregunta, cuerpo = respuesta)
                    }
                }

                VistaAyuda.Manual -> {
                    Text("MANUAL DE USO", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    ManualDeUso.forEach { (titulo, cuerpo) ->
                        BloqueTexto(titulo = titulo, cuerpo = cuerpo)
                    }
                }

                VistaAyuda.Reportes -> {
                    Text("MIS REPORTES ENVIADOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    if (state.misIncidencias.isEmpty()) {
                        Text(
                            "Todavía no has enviado reportes.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CecapiTextMuted,
                        )
                    }
                    state.misIncidencias.take(10).forEach { incidencia ->
                        BloqueTexto(
                            titulo = incidencia.mensaje,
                            cuerpo = (incidencia.respuesta?.let { "Respuesta: $it\n" } ?: "") + if (incidencia.resuelta) "Resuelto" else "En espera",
                            colorCuerpo = if (incidencia.resuelta) CecapiAccent else CecapiTextMuted,
                        )
                    }
                }
            }
        }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di reportar un problema, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BloqueTexto(
    titulo: String,
    cuerpo: String,
    colorCuerpo: androidx.compose.ui.graphics.Color = CecapiTextMuted,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CecapiSurface)
            .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(cuerpo, style = MaterialTheme.typography.bodyLarge, color = colorCuerpo)
    }
}

@Composable
private fun MensajesParaAdmin(
    mensajes: List<IncidenciaEntity>,
    onResolver: (IncidenciaEntity) -> Unit,
    onResponder: (IncidenciaEntity) -> Unit,
    onEliminar: (IncidenciaEntity) -> Unit,
    onPreguntas: () -> Unit,
    onManual: () -> Unit,
) {
    var aEliminar by remember { mutableStateOf<Long?>(null) }
    if (mensajes.isNotEmpty()) {
        Text("MENSAJES DE LAS PERSONAS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    }
    mensajes.take(20).forEach { mensaje ->
        BloqueTexto(
            titulo = mensaje.mensaje,
            cuerpo = (mensaje.respuesta?.let { "Respondido: $it\n" } ?: "") + if (mensaje.resuelta) "Resuelto" else "En espera",
            colorCuerpo = if (mensaje.resuelta) CecapiAccent else CecapiTextMuted,
        )
        BigBtn(icon = Icons.Filled.Check, label = "Responder", size = BigBtnSize.Secondary, onClick = { onResponder(mensaje) })
        if (!mensaje.resuelta) {
            BigBtn(icon = Icons.Filled.Check, label = "Marcar resuelto", size = BigBtnSize.Secondary, onClick = { onResolver(mensaje) })
        }
        if (aEliminar == mensaje.id) {
            BigBtn(icon = Icons.Filled.Close, label = "Confirmar eliminar", variant = BigBtnVariant.Danger, size = BigBtnSize.Secondary, onClick = {
                aEliminar = null
                onEliminar(mensaje)
            })
        } else {
            BigBtn(icon = Icons.Filled.Close, label = "Eliminar", variant = BigBtnVariant.Danger, size = BigBtnSize.Secondary, onClick = { aEliminar = mensaje.id })
        }
    }
    Text("INFORMACIÓN Y AYUDA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    BigBtn(icon = Icons.Filled.Info, label = "Preguntas frecuentes", section = Sections.Ayuda, onClick = onPreguntas)
    BigBtn(icon = Icons.AutoMirrored.Filled.Help, label = "Manual de uso", section = Sections.Ayuda, onClick = onManual)
}

private val PreguntasFrecuentes = listOf(
    "¿Cómo llamo al asistente?" to "Di hola o el nombre del asistente, o toca el micrófono. Para usar la palabra hola, actívala en Configuración, en Privacidad.",
    "¿Cómo cambio el nombre del asistente?" to "Di llámate y el nombre que quieras, o hazlo en Personalización.",
    "¿Qué funciona sin internet?" to "Los comandos básicos funcionan sin internet: abrir módulos, leer texto con la cámara y las actividades. Las preguntas a la IA necesitan internet.",
    "¿Mis fotos se guardan en la app?" to "No. La app no guarda tus fotos. Solo se guardan en tu galería si tú decides guardarlas.",
    "¿Quién ve mis datos?" to "Tu correo y tu fecha de nacimiento no se muestran a otras personas.",
    "¿Cómo me doy de baja?" to "En Mi cuenta, toca Darme de baja. Si perteneces a una institución, la baja la confirma tu educador o directivo.",
    "¿Qué hago en una emergencia?" to "En Emergencias puedes llamar a tu contacto o al 911, y enviar tu ubicación.",
    "¿Cómo reporto un problema?" to "En Ayuda y soporte, toca Reportar un problema técnico. Necesitas iniciar sesión.",
)

private val ManualDeUso = listOf(
    "Inicio" to "Di hola o toca el micrófono para empezar. Desde el inicio puedes entrar a la cámara, documentos, actividades, personalización y configuración.",
    "Micrófono" to "Toca una vez para hablar. Dos toques seguidos lo silencian.",
    "Detener" to "Di para para detener todo lo que el asistente esté diciendo.",
    "Volver" to "Cada pantalla tiene arriba el botón Volver para regresar al menú.",
    "Comandos" to "En cualquier pantalla, di lista de comandos para escuchar lo que puedes decir ahí.",
)

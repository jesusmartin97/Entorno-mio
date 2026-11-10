package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun EditAccountScreen(
    onBack: () -> Unit,
    viewModel: EditAccountViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    var editando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmarBaja by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.guardado.collect { editando = false } }
    LaunchedEffect(Unit) { viewModel.errorMensaje.collect { error = it } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 230.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTopBar(
                eyebrow = "CUENTA",
                title = "Mi cuenta",
                onBack = if (editando) ({ editando = false }) else viewModel::onBack,
                onCommands = viewModel::onCommandsRequested,
            )

            if (!editando) {
                TarjetaComoMeVen(
                    nombre = user?.nombreCompleto.orEmpty(),
                    apodo = user?.apodo,
                    rol = user?.rol.orEmpty(),
                    institucion = user?.origen.orEmpty(),
                    solicitud = user?.institucionSolicitada,
                    correo = user?.email,
                    fechaNacimiento = user?.fechaNacimiento,
                )
                BigBtn(
                    icon = Icons.Filled.Edit,
                    label = "Editar datos",
                    variant = BigBtnVariant.Accent,
                    onClick = { editando = true },
                )
            } else {
                FormularioDatos(
                    usuarioNombre = user?.nombreCompleto.orEmpty(),
                    usuarioApodo = user?.apodo.orEmpty(),
                    usuarioUsaApodo = user?.usarApodoRanking ?: false,
                    usuarioCorreo = user?.email.orEmpty(),
                    usuarioFecha = user?.fechaNacimiento,
                    error = error,
                    onGuardar = { nombre, apodo, usaApodo, actual, nueva, correo, fecha ->
                        viewModel.onGuardarPerfil(nombre, apodo, usaApodo, actual, nueva, correo, fecha)
                    },
                    onCancelar = { editando = false },
                )
            }

            if (!editando) {
                if (user?.rol != RolUsuario.ADMINISTRADOR.codigo) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val solicitud = user?.institucionSolicitada
                    if (user?.origen.isNullOrBlank() && solicitud == null) {
                        BigBtn(
                            icon = Icons.Filled.Badge,
                            label = "Pedir unirme a ${InstitucionRegistro.CECAPI.etiqueta}",
                            onClick = { viewModel.onPedirInstitucion(InstitucionRegistro.CECAPI.origen) },
                        )
                    } else if (solicitud != null) {
                        NoticeBanner("Solicitud enviada a $solicitud. Mientras tanto eres usuario.")
                    }

                    if (user?.rol == RolUsuario.ALUMNO.codigo && !user?.origen.isNullOrBlank()) {
                        BigBtn(
                            icon = Icons.Filled.Person,
                            label = if (user?.solicitaCambioEducador == true) "Cambio de educador pedido" else "Pedir cambio de educador",
                            onClick = viewModel::onPedirCambioEducador,
                        )
                    }
                    if (!user?.origen.isNullOrBlank() && user?.rol != RolUsuario.ADMINISTRADOR.codigo) {
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = if (user?.solicitaBaja == true) "Baja pedida, falta confirmación" else "Pedir mi baja",
                            variant = BigBtnVariant.Danger,
                            onClick = viewModel::onPedirBajaDeMenor,
                        )
                    } else if (!confirmarBaja) {
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = "Darme de baja",
                            variant = BigBtnVariant.Danger,
                            onClick = { confirmarBaja = true },
                        )
                    } else {
                        NoticeBanner("¿Borro tu cuenta y todo lo que guardaste? No se puede deshacer.")
                        var contrasenaBaja by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = contrasenaBaja,
                            onValueChange = { contrasenaBaja = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Tu contraseña actual *") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = "Confirmar baja",
                            variant = BigBtnVariant.Danger,
                            enabled = contrasenaBaja.isNotBlank(),
                            onClick = { viewModel.onDarseDeBaja(contrasenaBaja) },
                        )
                    }
                }
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

@Composable
private fun TarjetaComoMeVen(
    nombre: String,
    apodo: String?,
    rol: String,
    institucion: String,
    solicitud: String?,
    correo: String?,
    fechaNacimiento: Long?,
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
        Text("CÓMO ME VEN", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
        Text(nombre, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        if (!apodo.isNullOrBlank()) {
            Text("Apodo: $apodo", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Text("Rol: ${rol.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(
            when {
                institucion.isNotBlank() -> "Institución: $institucion (validado)"
                solicitud != null -> "Solicitud pendiente en $solicitud. Por ahora eres usuario."
                else -> "Usuario, sin institución."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = CecapiTextMuted,
        )
        if (!correo.isNullOrBlank()) {
            Text("Correo: $correo", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        if (fechaNacimiento != null) {
            Text("Fecha de nacimiento: ${fechaTexto(fechaNacimiento)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            "Tu correo y tu fecha de nacimiento no se muestran a otras personas.",
            style = MaterialTheme.typography.bodyMedium,
            color = CecapiTextMuted,
        )
    }
}

@Composable
private fun FormularioDatos(
    usuarioNombre: String,
    usuarioApodo: String,
    usuarioUsaApodo: Boolean,
    usuarioCorreo: String,
    usuarioFecha: Long?,
    error: String?,
    onGuardar: (String, String, Boolean, String, String, String, Long?) -> Unit,
    onCancelar: () -> Unit,
) {
    var nombre by remember(usuarioNombre) { mutableStateOf(usuarioNombre) }
    var apodo by remember(usuarioApodo) { mutableStateOf(usuarioApodo) }
    var usaApodo by remember(usuarioUsaApodo) { mutableStateOf(usuarioUsaApodo) }
    var correo by remember(usuarioCorreo) { mutableStateOf(usuarioCorreo) }
    val inicial = remember(usuarioFecha) { usuarioFecha?.let { fechaPartes(it) } }
    var dia by remember(usuarioFecha) { mutableStateOf(inicial?.get(0).orEmpty()) }
    var mes by remember(usuarioFecha) { mutableStateOf(inicial?.get(1).orEmpty()) }
    var anio by remember(usuarioFecha) { mutableStateOf(inicial?.get(2).orEmpty()) }
    var nueva by remember { mutableStateOf("") }
    var actual by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val fecha = fechaDesde(dia, mes, anio)
    val listo = nueva.length >= 4 && actual.isNotBlank() && fecha != null

    Text("EDITAR DATOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    Text("Los campos con * son obligatorios.", style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)

    OutlinedTextField(
        value = nombre,
        onValueChange = { nombre = it },
        modifier = Modifier.fillMaxWidth().voiceHint("Tu nombre completo."),
        label = { Text("Nombre completo *") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
    )
    OutlinedTextField(
        value = apodo,
        onValueChange = { apodo = it },
        modifier = Modifier.fillMaxWidth().voiceHint("Tu apodo. Es opcional."),
        label = { Text("Apodo (opcional)") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigBtn(
            icon = Icons.Filled.Badge,
            label = "Mi nombre",
            variant = if (!usaApodo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
            half = true,
            onClick = { usaApodo = false },
            modifier = Modifier.weight(1f),
        )
        BigBtn(
            icon = Icons.Filled.Face,
            label = "Mi apodo",
            variant = if (usaApodo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
            half = true,
            onClick = { usaApodo = true },
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        value = correo,
        onValueChange = { correo = it },
        modifier = Modifier.fillMaxWidth().voiceHint("Correo, opcional."),
        label = { Text("Correo (opcional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
    )
    Text("FECHA DE NACIMIENTO *", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = dia,
            onValueChange = { dia = it.filter(Char::isDigit).take(2) },
            modifier = Modifier.weight(1f),
            label = { Text("Día") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = mes,
            onValueChange = { mes = it.filter(Char::isDigit).take(2) },
            modifier = Modifier.weight(1f),
            label = { Text("Mes") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = anio,
            onValueChange = { anio = it.filter(Char::isDigit).take(4) },
            modifier = Modifier.weight(1.4f),
            label = { Text("Año") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    OutlinedTextField(
        value = nueva,
        onValueChange = { nueva = it.filter(Char::isDigit) },
        modifier = Modifier.fillMaxWidth().voiceHint("Nueva contraseña. Mínimo cuatro números."),
        label = { Text("Nueva contraseña * (mínimo 4)") },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
    )
    OutlinedTextField(
        value = actual,
        onValueChange = { actual = it.filter(Char::isDigit) },
        modifier = Modifier.fillMaxWidth().voiceHint("Tu contraseña actual, para confirmar los cambios."),
        label = { Text("Contraseña actual para confirmar *") },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
    )

    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigBtn(
            icon = Icons.Filled.Check,
            label = "Guardar",
            variant = BigBtnVariant.Accent,
            half = true,
            enabled = listo,
            onClick = { onGuardar(nombre, apodo, usaApodo, actual, nueva, correo, fecha) },
            modifier = Modifier.weight(1f),
        )
        BigBtn(
            icon = Icons.Filled.Close,
            label = "Cancelar",
            half = true,
            onClick = onCancelar,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun fechaDesde(dia: String, mes: String, anio: String): Long? {
    val d = dia.toIntOrNull() ?: return null
    val m = mes.toIntOrNull() ?: return null
    val a = anio.toIntOrNull() ?: return null
    return runCatching {
        LocalDate.of(a, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

private fun fechaPartes(millis: Long): List<String> {
    val f = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return listOf(f.dayOfMonth.toString(), f.monthValue.toString(), f.year.toString())
}

private fun fechaTexto(millis: Long): String {
    val f = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return "%02d/%02d/%d".format(f.dayOfMonth, f.monthValue, f.year)
}

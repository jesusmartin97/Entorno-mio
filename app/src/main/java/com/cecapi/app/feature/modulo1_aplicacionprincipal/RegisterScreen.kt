package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() else viewModel.onMicPermissionDenied() }

    LaunchedEffect(Unit) {
        viewModel.registerSucceeded.collect { onRegisterSuccess() }
    }
    LaunchedEffect(Unit) {
        viewModel.back.collect { onBack() }
    }

    val listening = voiceState is VoiceState.Listening

    // Mismo micrófono grande y fijo que en el resto de la app — un solo control, siempre en el
    // mismo lugar, para alguien que no ve dónde está un botón chico.
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 250.dp),
    ) {
        ScreenTopBar(
            eyebrow = "CREAR CUENTA",
            title = "Nueva cuenta",
            onBack = onBack,
            onCommands = { viewModel.onMicTapped() },
        )

        Text(
            text = "NOMBRE COMPLETO",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.nombreCompleto,
            onValueChange = viewModel::onNombreCompletoChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .voiceHint(
                    help = "Campo de nombre completo. Aquí va tu nombre y apellido, como quieres que te salude.",
                    focusLabel = "Campo de nombre completo. Escribe tu nombre y apellido.",
                ),
            textStyle = MaterialTheme.typography.titleLarge,
            placeholder = { Text("Tu nombre y apellido", style = MaterialTheme.typography.titleMedium) },
            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(28.dp)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
        )

        Text(
            text = "USUARIO",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.nombreUsuario,
            onValueChange = viewModel::onNombreUsuarioChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .voiceHint(
                    help = "Campo de usuario. Elige el nombre corto con el que vas a entrar. " +
                        "Se escribe en mayúsculas automáticamente.",
                    focusLabel = "Campo de usuario. Elige un nombre corto para entrar.",
                ),
            textStyle = MaterialTheme.typography.titleLarge,
            placeholder = { Text("Elige un nombre de usuario", style = MaterialTheme.typography.titleMedium) },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(28.dp)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
        )

        Text(
            text = "CONTRASEÑA",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.contrasena,
            onValueChange = { viewModel.onContrasenaChange(it.filter(Char::isDigit)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .voiceHint(
                    help = "Campo de contraseña. Elige una clave de al menos cuatro números. " +
                        "Se ve oculta y nunca la leo en voz alta.",
                    focusLabel = "Campo de contraseña. Elige una clave de al menos cuatro números.",
                ),
            textStyle = MaterialTheme.typography.titleLarge,
            placeholder = { Text("Mínimo cuatro números", style = MaterialTheme.typography.titleMedium) },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(28.dp)) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
        )

        OutlinedTextField(
            value = uiState.confirmarContrasena,
            onValueChange = { viewModel.onConfirmarContrasenaChange(it.filter(Char::isDigit)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(top = 12.dp)
                .voiceHint("Vuelve a escribir la contraseña para confirmarla."),
            label = { Text("Confirma tu contraseña") },
            textStyle = MaterialTheme.typography.titleLarge,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
        )

        Text(
            text = "INSTITUCIÓN",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InstitucionRegistro.entries.forEach { opcion ->
                BigBtn(
                    icon = Icons.Filled.School,
                    label = opcion.etiqueta,
                    variant = if (uiState.institucion == opcion) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                    half = true,
                    onClick = { viewModel.onInstitucionChange(opcion) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(
            text = "FECHA DE NACIMIENTO",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        // Tres campos numéricos en vez de un calendario pequeño: un selector de fecha estándar es
        // difícil de usar sin ver. Se escriben o se dictan como cualquier otro campo.
        var dia by remember { mutableStateOf("") }
        var mesNum by remember { mutableStateOf("") }
        var anio by remember { mutableStateOf("") }
        fun actualizarFecha() {
            val d = dia.toIntOrNull()
            val m = mesNum.toIntOrNull()
            val a = anio.toIntOrNull()
            if (d != null && m in 1..12 && a != null && a > 1900) {
                val cal = java.util.Calendar.getInstance()
                cal.clear()
                cal.set(a, m!! - 1, d)
                viewModel.onFechaNacimientoChange(cal.timeInMillis)
            }
        }
        val dateFieldStyle = MaterialTheme.typography.titleLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        val dateFieldShape = RoundedCornerShape(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = dia,
                onValueChange = { dia = it.filter(Char::isDigit).take(2); actualizarFecha() },
                modifier = Modifier.weight(1f).heightIn(min = 72.dp).voiceHint("Día de nacimiento."),
                textStyle = dateFieldStyle,
                label = { Text("Día") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = dateFieldShape,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
            )
            OutlinedTextField(
                value = mesNum,
                onValueChange = { mesNum = it.filter(Char::isDigit).take(2); actualizarFecha() },
                modifier = Modifier.weight(1f).heightIn(min = 72.dp).voiceHint("Mes de nacimiento, del uno al doce."),
                textStyle = dateFieldStyle,
                label = { Text("Mes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = dateFieldShape,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
            )
            OutlinedTextField(
                value = anio,
                onValueChange = { anio = it.filter(Char::isDigit).take(4); actualizarFecha() },
                modifier = Modifier.weight(1.3f).heightIn(min = 72.dp).voiceHint("Año de nacimiento, con cuatro números."),
                textStyle = dateFieldStyle,
                label = { Text("Año") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = dateFieldShape,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
            )
        }

        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = CecapiError,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        BigBtn(
            icon = Icons.Filled.Check,
            label = if (uiState.isSubmitting) "Creando…" else "Crear mi cuenta",
            variant = BigBtnVariant.Accent,
            enabled = !uiState.isSubmitting,
            onClick = viewModel::submit,
            modifier = Modifier.padding(top = 24.dp),
        )
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di tu nombre, o toca aquí",
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

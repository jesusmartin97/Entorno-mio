package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() else viewModel.onMicPermissionDenied() }

    LaunchedEffect(Unit) {
        viewModel.loginSucceeded.collect { onLoginSuccess() }
    }
    LaunchedEffect(Unit) {
        viewModel.navigateToRegister.collect { onNavigateToRegister() }
    }
    LaunchedEffect(Unit) {
        viewModel.back.collect { onBack() }
    }

    // Login stays alive under Register; only react to speech while it is actually showing.
    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }

    // "Hola" (or the assistant's name) opens the mic here too. Ask for the mic on entry if needed.
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val entryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (!granted) viewModel.onMicPermissionDenied()
    }
    LaunchedEffect(Unit) {
        if (!hasMicPermission) entryPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(hasMicPermission) {
        viewModel.setWakeWordEnabled(hasMicPermission)
        onDispose { viewModel.setWakeWordEnabled(false) }
    }

    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening

    // El micrófono grande y fijo abajo es siempre el mismo control, en el mismo lugar, en toda la
    // app — para alguien invidente eso importa más que el espacio que ocupa.
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 250.dp),
    ) {
        ScreenTopBar(
            eyebrow = "CUENTA",
            title = "Iniciar sesión",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        Text(
            text = "USUARIO",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .voiceHint(
                    help = "Campo de usuario. Aquí va el nombre con el que te registraron. " +
                        "Puedes escribirlo o dictarlo. Se escribe en mayúsculas automáticamente.",
                    focusLabel = "Campo de usuario. Escribe tu nombre de usuario.",
                ),
            textStyle = MaterialTheme.typography.titleLarge,
            placeholder = { Text("Tu nombre de usuario", style = MaterialTheme.typography.titleMedium) },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(28.dp)) },
            singleLine = true,
            // No autocorrect: the keyboard may swap the typed word for another when focus moves on.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CecapiAccent,
                unfocusedBorderColor = CecapiTextMuted,
            ),
        )

        Text(
            text = "CONTRASEÑA",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.onPasswordChange(it.filter(Char::isDigit)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .voiceHint(
                    help = "Campo de contraseña. Son números. Se ve oculta por seguridad. " +
                        "Puedes escribirla o dictarla, y nunca la leo en voz alta.",
                    focusLabel = "Campo de contraseña. Escribe tu contraseña.",
                ),
            textStyle = MaterialTheme.typography.titleLarge,
            placeholder = { Text("Tu contraseña", style = MaterialTheme.typography.titleMedium) },
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
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CecapiAccent,
                unfocusedBorderColor = CecapiTextMuted,
            ),
        )

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
            label = if (uiState.isSubmitting) "Entrando…" else "Ingresar",
            variant = BigBtnVariant.Accent,
            enabled = !uiState.isSubmitting,
            onClick = viewModel::submit,
            modifier = Modifier.padding(top = 24.dp),
        )
        BigBtn(
            icon = Icons.Filled.PersonAdd,
            label = "Crear cuenta",
            onClick = onNavigateToRegister,
            modifier = Modifier.padding(top = 14.dp),
        )
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di usuario o contraseña, o toca aquí",
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

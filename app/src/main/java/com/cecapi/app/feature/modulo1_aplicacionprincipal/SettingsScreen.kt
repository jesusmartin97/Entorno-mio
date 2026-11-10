package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.theme.CecapiBorder
import androidx.compose.foundation.border
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState
import kotlin.math.roundToInt

private enum class SettingsSub { VOLUMEN, NOTIFICACIONES, ALMACENAMIENTO, PRIVACIDAD, CUENTA }

/**
 * Configuración, como un vestíbulo con una tarjeta por tema en vez de una sola columna larga:
 * Volumen, Notificaciones, Almacenamiento y Privacidad (que agrupa escuchar fuera de la app, IA
 * y modo simple) abren su propia pantalla. Cuenta (cerrar sesión / borrar cuenta) solo aparece
 * con sesión iniciada. La voz y los nombres del asistente están en Personalización.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onNavigateToAccesibilidad: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val simpleMode by viewModel.simpleMode.collectAsState()
    val announceNotifications by viewModel.announceNotifications.collectAsState()
    val listenOutsideApp by viewModel.listenOutsideApp.collectAsState()
    val holaEnabled by viewModel.holaEnabled.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val storage by viewModel.storage.collectAsState()
    val pendingClean by viewModel.pendingClean.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()
    val pendingDeleteAccount by viewModel.pendingDeleteAccount.collectAsState()
    val awaitingDeletePassword by viewModel.awaitingDeletePassword.collectAsState()
    var deletePassword by remember { mutableStateOf("") }
    var deletePasswordVisible by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf<SettingsSub?>(null) }

    var volume by remember { mutableFloatStateOf(viewModel.currentVolumePercent().toFloat()) }
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening

    LaunchedEffect(Unit) { viewModel.loggedOut.collect { onLoggedOut() } }

    // The permission is granted in Android's own settings, so check again every time we come back.
    var notificationAccess by remember { mutableStateOf(viewModel.isNotificationAccessEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationAccess = viewModel.isNotificationAccessEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ asks before showing the always-visible "Asistente activo" notice; the service works either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onListenOutsideAppChanged(true)
    }

    // The switch and the voice command ("activa escuchar fuera de la aplicación") do the same thing.
    val changeListenOutside: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onListenOutsideAppChanged(enabled)
        }
    }
    LaunchedEffect(Unit) { viewModel.listenOutsideRequests.collect { changeListenOutside(it) } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (sub) {
            null -> {
                ScreenTopBar(eyebrow = "MENÚ", title = "Configuración", onBack = onBack, onCommands = viewModel::onCommandsRequested)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigBtn(
                        icon = Icons.Filled.VolumeUp,
                        label = "Volumen",
                        section = Sections.Settings,
                        half = true,
                        onClick = { sub = SettingsSub.VOLUMEN },
                        modifier = Modifier.weight(1f),
                    )
                    BigBtn(
                        icon = Icons.Filled.NotificationsActive,
                        label = "Notificaciones",
                        section = Sections.Settings,
                        half = true,
                        onClick = { sub = SettingsSub.NOTIFICACIONES },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigBtn(
                        icon = Icons.Filled.Storage,
                        label = "Almacenamiento",
                        section = Sections.Settings,
                        half = true,
                        onClick = { sub = SettingsSub.ALMACENAMIENTO },
                        modifier = Modifier.weight(1f),
                    )
                    BigBtn(
                        icon = Icons.Filled.Shield,
                        label = "Privacidad",
                        section = Sections.Settings,
                        half = true,
                        onClick = { sub = SettingsSub.PRIVACIDAD },
                        modifier = Modifier.weight(1f),
                    )
                }
                BigBtn(
                    icon = Icons.Filled.Accessibility,
                    label = "Accesibilidad",
                    section = Sections.Settings,
                    onClick = onNavigateToAccesibilidad,
                )
                if (user != null) {
                    BigBtn(
                        icon = Icons.Filled.Person,
                        label = "Cuenta",
                        section = Sections.Settings,
                        onClick = { sub = SettingsSub.CUENTA },
                    )
                }
            }

            SettingsSub.VOLUMEN -> {
                ScreenTopBar(eyebrow = "CONFIGURACIÓN", title = "Volumen", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                SliderRow(
                    label = "Volumen",
                    valueText = "${volume.roundToInt()}%",
                    value = volume,
                    range = 1f..100f,
                    steps = 0,
                    onChange = { volume = it },
                    onFinished = { viewModel.onVolumeChanged(volume.roundToInt()) },
                    help = "Volumen. Sube o baja el volumen de todo el teléfono, igual que los botones laterales. " +
                        "No baja hasta silencio para que siempre me escuches.",
                )
            }

            SettingsSub.NOTIFICACIONES -> {
                ScreenTopBar(eyebrow = "CONFIGURACIÓN", title = "Notificaciones", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CecapiSurface)
                        .border(1.dp, if (notificationAccess) CecapiAccent else CecapiError, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = if (notificationAccess) "Acceso concedido" else "Falta dar acceso",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (notificationAccess) CecapiAccent else CecapiError,
                    )
                }
                if (!notificationAccess) {
                    ActionButton(
                        "Dar acceso a notificaciones",
                        "Dar acceso a notificaciones. Abre los ajustes de Android, donde debes buscar CECAPI y activarlo.",
                        viewModel::onOpenNotificationSettings,
                    )
                }
                SwitchRow(
                    label = "Avisar cuando llegan",
                    description = "",
                    checked = announceNotifications,
                    onChange = viewModel::onAnnounceNotificationsChanged,
                    help = "Avisar cuando llegan. Cuando entra una notificación digo de qué aplicación es, " +
                        "pero nunca leo lo que dice si no me lo pides. No aviso durante llamadas ni con No molestar.",
                )
            }

            SettingsSub.ALMACENAMIENTO -> {
                ScreenTopBar(eyebrow = "CONFIGURACIÓN", title = "Almacenamiento", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                val usage = storage
                if (usage == null) {
                    Text("Calculando…", style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
                } else {
                    if (usage.lowSpace) NoticeBanner("Queda poco espacio en el teléfono. Conviene liberar espacio.")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CecapiSurface)
                            .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Libre: ${espacio(usage.freeBytes)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text("CECAPI usa: ${espacio(usage.appBytes)}", style = MaterialTheme.typography.titleLarge, color = CecapiAccent)
                    }
                }
                if (pendingClean) {
                    NoticeBanner("¿Borrar las fotos de más de un mes? Di sí o no.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigBtn(icon = Icons.Filled.Check, label = "Sí", variant = BigBtnVariant.Danger, half = true, onClick = viewModel::confirmClean, modifier = Modifier.weight(1f))
                        BigBtn(icon = Icons.Filled.Close, label = "No", half = true, onClick = viewModel::cancelClean, modifier = Modifier.weight(1f))
                    }
                } else {
                    ActionButton(
                        "Liberar espacio",
                        "Liberar espacio. Borra las fotos que la aplicación guardó hace más de un mes. " +
                            "Te pido confirmación antes de borrar y el texto que leí sigue guardado. También puedes decir: libera espacio.",
                        viewModel::askClean,
                    )
                    ActionButton(
                        "Vaciar memoria temporal",
                        "Vaciar memoria temporal. Borra archivos temporales que la aplicación y sus librerías guardan para ir más rápido. " +
                            "No borra tus chats ni nada que hayas hecho. También puedes decir: limpia la caché.",
                        viewModel::clearCache,
                    )
                    ActionButton(
                        "Escuchar el reporte",
                        "Escuchar el reporte. Te digo cuánto espacio libre queda y cuánto ocupa la aplicación. " +
                            "También puedes decir: cuánto espacio tengo.",
                        viewModel::speakStorage,
                    )
                }
            }

            SettingsSub.PRIVACIDAD -> {
                ScreenTopBar(eyebrow = "CONFIGURACIÓN", title = "Privacidad", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                SwitchRow(
                    label = "Escuchar fuera de la aplicación",
                    description = "Muestra un aviso fijo mientras escucha. Di \"para\" para detenerlo",
                    checked = listenOutsideApp,
                    onChange = changeListenOutside,
                    help = "Escuchar fuera de la aplicación. Sigo atento a mi nombre, y a hola si lo activaste, aunque cierres " +
                        "la aplicación. Mientras tanto verás un aviso fijo, y puedes detenerme diciendo para. " +
                        "Gasta más batería.",
                )
                SwitchRow(
                    label = "Responder a la palabra hola",
                    description = "Apagado por defecto. Siempre puedes decir el nombre del asistente o tocar el micrófono",
                    checked = holaEnabled,
                    onChange = viewModel::onHolaChanged,
                    help = "Responder a la palabra hola. Apagado, solo respondo a mi nombre o al toque del micrófono. " +
                        "Encendido, también respondo cuando digas hola.",
                )
                SwitchRow(
                    label = "Inteligencia artificial en internet",
                    description = "Todavía no está conectada. No la actives si quien usa la aplicación es menor de edad",
                    checked = aiEnabled,
                    onChange = viewModel::onAiEnabledChanged,
                    help = "Inteligencia artificial en internet. Apagada, ninguna de tus preguntas se envía a un servidor para interpretarla. " +
                        "Encendida, cuando no entienda una frase y haya internet, podrá enviarla a un servidor para interpretarla. " +
                        "Esto no cambia el reconocimiento de voz del teléfono, que sigue funcionando igual. " +
                        "Los proveedores de inteligencia artificial no permiten su uso con menores de edad. " +
                        "También puedes decir: activa la inteligencia artificial, o desactívala.",
                )
                SwitchRow(
                    label = "Modo simple",
                    description = "Menús más grandes y con menos opciones a la vez",
                    checked = simpleMode,
                    onChange = viewModel::onSimpleModeChanged,
                    help = "Modo simple. Muestra los menús más grandes y con menos opciones a la vez.",
                )
            }

            SettingsSub.CUENTA -> user?.let { current ->
                ScreenTopBar(eyebrow = "MENÚ", title = "Cuenta", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                Text(current.nombreCompleto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                BigBtn(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Cerrar sesión",
                    variant = BigBtnVariant.Danger,
                    onClick = viewModel::onLogout,
                )
                if (awaitingDeletePassword) {
                    NoticeBanner("Para borrar tu cuenta, dime o escribe tu contraseña.")
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it.filter(Char::isDigit) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .voiceHint("Campo de contraseña, para confirmar que quieres borrar tu cuenta. Se ve oculta por seguridad."),
                        placeholder = { Text("Tu contraseña") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { deletePasswordVisible = !deletePasswordVisible }) {
                                Icon(
                                    imageVector = if (deletePasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (deletePasswordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (deletePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.onDeletePasswordEntered(deletePassword)
                            deletePassword = ""
                        }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        BigBtn(
                            icon = Icons.Filled.Check,
                            label = "Confirmar",
                            variant = BigBtnVariant.Danger,
                            half = true,
                            onClick = { viewModel.onDeletePasswordEntered(deletePassword); deletePassword = "" },
                            modifier = Modifier.weight(1f),
                        )
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = "Cancelar",
                            half = true,
                            onClick = { deletePassword = ""; viewModel.cancelDeleteAccount() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else if (pendingDeleteAccount) {
                    NoticeBanner("¿Borrar tu cuenta y todo lo que guardaste? No se puede deshacer. Di sí, borrar, o no.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigBtn(icon = Icons.Filled.Check, label = "Sí, borrar", variant = BigBtnVariant.Danger, half = true, onClick = viewModel::confirmDeleteAccount, modifier = Modifier.weight(1f))
                        BigBtn(icon = Icons.Filled.Close, label = "No", half = true, onClick = viewModel::cancelDeleteAccount, modifier = Modifier.weight(1f))
                    }
                } else {
                    ActionButton(
                        "Borrar mi cuenta y mis datos",
                        "Borrar mi cuenta y mis datos. Elimina tu cuenta y todo lo que guardaste: chats, resultados, documentos y fotos. " +
                            "Te pido tu contraseña y luego confirmar; no se puede deshacer. También puedes decir: borra mi cuenta.",
                        viewModel::askDeleteAccount,
                    )
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di cuánto espacio tengo, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    help: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CecapiSurface)
            .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
            .voiceHint(help)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(valueText, style = MaterialTheme.typography.titleLarge, color = CecapiAccent)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = CecapiAccent, activeTrackColor = CecapiAccent),
            modifier = Modifier.semantics { contentDescription = "$label, $valueText" },
        )
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    help: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CecapiSurface)
            .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
            .clickable { onChange(!checked) }
            .heightIn(min = 76.dp)
            .voiceHint(help)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CecapiAccent, checkedTrackColor = CecapiAccent.copy(alpha = 0.5f)),
        )
    }
}

@Composable
internal fun ActionButton(text: String, help: String, onClick: () -> Unit) {
    BigBtn(icon = Icons.Filled.Check, label = text, onClick = onClick)
}


/** Tamaño corto para la pantalla: GB o MB. */
private fun espacio(bytes: Long): String =
    if (bytes >= 1_000_000_000L) "%.1f GB".format(bytes / 1_000_000_000.0) else "%d MB".format(bytes / 1_000_000)

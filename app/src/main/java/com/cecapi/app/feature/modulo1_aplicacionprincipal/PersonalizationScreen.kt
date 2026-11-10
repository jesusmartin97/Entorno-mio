package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiBorder
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiWarning
import com.cecapi.app.core.theme.ModuleSection
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.AddressStyle
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceOption
import com.cecapi.app.core.voice.VoiceProfile
import com.cecapi.app.core.voice.VoiceState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class PersonalizationSub { VOZ, VOZ_ELEGIR, COMO_ME_HABLAS, PERFIL, PANTALLA, AVISOS }

/**
 * Personalización, como un vestíbulo con una tarjeta por tema (igual que el resto de la app) en
 * vez de una sola columna larga con todos los controles apilados: cada tema abre su propia
 * pantalla, así cabe sin depender tanto del scroll aunque el micrófono ocupe espacio fijo abajo.
 */
@Composable
fun PersonalizationScreen(
    onBack: () -> Unit,
    viewModel: PersonalizationViewModel = hiltViewModel(),
) {
    val savedRate by viewModel.speechRate.collectAsState()
    val savedPitch by viewModel.pitch.collectAsState()
    val voiceName by viewModel.voiceName.collectAsState()
    val style by viewModel.addressStyle.collectAsState()
    val savedPreferred by viewModel.preferredName.collectAsState()
    val savedAssistant by viewModel.assistantName.collectAsState()
    val soundCues by viewModel.soundCues.collectAsState()
    val vibrationCues by viewModel.vibrationCues.collectAsState()
    val savedLevel by viewModel.vibrationLevel.collectAsState()
    val blackScreen by viewModel.blackScreen.collectAsState()
    val minBrightness by viewModel.minBrightness.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    var rate by remember(savedRate) { mutableFloatStateOf(savedRate) }
    var pitch by remember(savedPitch) { mutableFloatStateOf(savedPitch) }
    var level by remember(savedLevel) { mutableFloatStateOf(savedLevel.toFloat()) }
    var preferred by remember(savedPreferred) { mutableStateOf(savedPreferred) }
    var assistant by remember(savedAssistant) { mutableStateOf(savedAssistant) }
    var sub by remember { mutableStateOf<PersonalizationSub?>(null) }

    // The speech engine may still be starting when this screen opens: ask again for a few seconds.
    var voices by remember { mutableStateOf(emptyList<VoiceOption>()) }
    LaunchedEffect(Unit) {
        repeat(20) {
            voices = viewModel.voices()
            if (voices.isNotEmpty()) return@LaunchedEffect
            delay(300)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 230.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (sub) {
                null -> {
                    ScreenTopBar(eyebrow = "PERSONALIZACIÓN", title = "Mi experiencia", onBack = onBack, onCommands = viewModel::onCommandsRequested)
                    BigBtn(
                        icon = Icons.Filled.Mic,
                        label = "Mi voz",
                        sub = "Nombre, velocidad y tono",
                        section = Sections.Personalization,
                        variant = BigBtnVariant.Accent,
                        onClick = { sub = PersonalizationSub.VOZ },
                    )
                    BigBtn(
                        icon = Icons.Filled.Person,
                        label = "Cómo me hablas",
                        sub = "Trato y cómo te llamo",
                        section = Sections.Personalization,
                        onClick = { sub = PersonalizationSub.COMO_ME_HABLAS },
                    )
                    BigBtn(
                        icon = Icons.Filled.Visibility,
                        label = "Pantalla",
                        section = Sections.Personalization,
                        onClick = { sub = PersonalizationSub.PANTALLA },
                    )
                    BigBtn(
                        icon = Icons.Filled.NotificationsActive,
                        label = "Avisos y vibración",
                        sub = "Sonidos, vibración y alertas",
                        section = Sections.Personalization,
                        onClick = { sub = PersonalizationSub.AVISOS },
                    )
                }

                PersonalizationSub.VOZ_ELEGIR -> {
                    ScreenTopBar(eyebrow = "MI VOZ", title = "Elegir voz", onBack = { sub = PersonalizationSub.VOZ }, onCommands = viewModel::onCommandsRequested)
                    val paginas = listOf("" to "Automática") + voices.map { it.name to it.label }
                    val pager = rememberPagerState(
                        initialPage = paginas.indexOfFirst { it.first == voiceName }.coerceAtLeast(0),
                        pageCount = { paginas.size },
                    )
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(220.dp)) { pagina ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(CecapiSurface)
                                .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
                                .padding(20.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(paginas[pagina].second, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    BigBtn(
                        icon = Icons.Filled.Check,
                        label = "Usar esta voz",
                        variant = BigBtnVariant.Accent,
                        onClick = { viewModel.onVoiceChosen(paginas[pager.currentPage].first) },
                    )
                }

                PersonalizationSub.VOZ -> {
                    ScreenTopBar(eyebrow = "MI VOZ", title = "Nombre y ajustes", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                    val vozActual = voices.firstOrNull { it.name == voiceName }?.label ?: "Automática"
                    BigBtn(
                        icon = Icons.Filled.Mic,
                        label = "Voz: $vozActual",
                        section = Sections.Personalization,
                        onClick = { sub = PersonalizationSub.VOZ_ELEGIR },
                    )
                    SliderRow(
                        label = "Velocidad",
                        valueText = "${(rate * 4).roundToInt() / 4f}x",
                        value = rate,
                        range = 0.5f..2.0f,
                        steps = 5,
                        onChange = { rate = it },
                        onFinished = { viewModel.onSpeechRateChanged(rate) },
                        help = "Velocidad. Desliza a la izquierda para que hable más lento y a la derecha para que hable más rápido.",
                    )
                    SliderRow(
                        label = "Tono",
                        valueText = when {
                            pitch < 0.9f -> "Grave"
                            pitch > 1.1f -> "Agudo"
                            else -> "Normal"
                        },
                        value = pitch,
                        range = 0.5f..2.0f,
                        steps = 5,
                        onChange = { pitch = it },
                        onFinished = { viewModel.onPitchChanged(pitch) },
                        help = "Tono. Desliza a la izquierda para una voz más grave y a la derecha para una más aguda.",
                    )
                    ActionButton("Probar voz", "Probar voz. Me escuchas hablar con esta voz, velocidad y tono.") { viewModel.onTestVoice() }
                }

                PersonalizationSub.COMO_ME_HABLAS -> {
                    ScreenTopBar(eyebrow = "CÓMO ME HABLAS", title = "Tu forma de hablarme", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceRow(
                            label = "Tú",
                            detail = "Trato cercano",
                            selected = style == AddressStyle.TU,
                            help = "Tú. Te hablo de tú, con un trato cercano. Toca dos veces para elegirlo.",
                            onClick = { viewModel.onAddressStyleChosen(AddressStyle.TU) },
                            modifier = Modifier.weight(1f),
                        )
                        ChoiceRow(
                            label = "Usted",
                            detail = "Trato formal",
                            selected = style == AddressStyle.USTED,
                            help = "Usted. Le hablo de usted, con un trato formal. Toca dos veces para elegirlo.",
                            onClick = { viewModel.onAddressStyleChosen(AddressStyle.USTED) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = preferred,
                        onValueChange = { preferred = it.take(30) },
                        label = { Text("Cómo quieres que te llame") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .voiceHint(
                                help = "Cómo quieres que te llame. Escribe tu nombre o apodo y con ese te saludo al entrar. " +
                                    "Si lo dejas vacío uso el de tu cuenta.",
                                focusLabel = "Campo del nombre con el que te saludo. Escribe cómo quieres que te llame.",
                            ),
                    )
                    OutlinedTextField(
                        value = assistant,
                        onValueChange = { assistant = it.take(30) },
                        label = { Text("Nombre del asistente (opcional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .voiceHint(
                                help = "Nombre del asistente. Escribe el nombre con el que quieres llamarme. " +
                                    "Siempre puedes decir hola, y además responderé a este nombre.",
                                focusLabel = "Campo del nombre del asistente. Escribe cómo quieres llamarme.",
                            ),
                    )
                    ActionButton("Guardar", "Guardar. Guarda cómo te llamo y cómo me llamas.") {
                        viewModel.onPreferredNameSaved(preferred)
                        viewModel.onAssistantNameSaved(assistant)
                    }
                }

                PersonalizationSub.PERFIL -> {
                    ScreenTopBar(eyebrow = "PERFIL RÁPIDO", title = "¿Quién usa la app?", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                    BigBtn(
                        icon = Icons.Filled.ChildCare,
                        label = "Niño",
                        sub = "Voz clara, frases simples, ritmo lento",
                        section = Sections.Personalization,
                        onClick = { viewModel.onProfileChosen(VoiceProfile.CHILD) },
                    )
                    BigBtn(
                        icon = Icons.Filled.Person,
                        label = "Normal",
                        sub = "Configuración estándar equilibrada",
                        section = Sections.Personalization,
                        onClick = { viewModel.onProfileChosen(VoiceProfile.NORMAL) },
                    )
                    BigBtn(
                        icon = Icons.Filled.Elderly,
                        label = "Persona mayor",
                        sub = "Voz más pausada, trato de usted",
                        section = Sections.Personalization,
                        onClick = { viewModel.onProfileChosen(VoiceProfile.SENIOR) },
                    )
                }

                PersonalizationSub.PANTALLA -> {
                    ScreenTopBar(eyebrow = "PANTALLA", title = "Opciones de pantalla", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                    BigBtn(
                        icon = Icons.Filled.Visibility,
                        label = "Pantalla negra",
                        section = Sections.Personalization,
                        variant = if (blackScreen) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                        onClick = { viewModel.onBlackScreenChanged(!blackScreen) },
                    )
                    BigBtn(
                        icon = Icons.Filled.Tune,
                        label = "Brillo mínimo",
                        section = Sections.Personalization,
                        variant = if (minBrightness) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                        onClick = { viewModel.onMinBrightnessChanged(!minBrightness) },
                    )
                }

                PersonalizationSub.AVISOS -> {
                    ScreenTopBar(eyebrow = "AVISOS", title = "Sonidos y vibración", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                    SwitchRow(
                        label = "Sonidos",
                        description = "Pitidos al escuchar, al acertar y al fallar",
                        checked = soundCues,
                        onChange = viewModel::onSoundCuesChanged,
                        help = "Sonidos. Activa o desactiva los pitidos que te avisan cuando empiezo a escucharte, " +
                            "cuando algo sale bien y cuando algo falla.",
                    )
                    SwitchRow(
                        label = "Vibración",
                        description = "Vibra al escuchar, al acertar y al fallar",
                        checked = vibrationCues,
                        onChange = viewModel::onVibrationCuesChanged,
                        help = "Vibración. Activa o desactiva las vibraciones de la aplicación. No cambia la vibración de las llamadas.",
                    )
                    SliderRow(
                        label = "Fuerza de la vibración",
                        valueText = when (level.roundToInt()) {
                            1 -> "Suave"
                            3 -> "Fuerte"
                            else -> "Normal"
                        },
                        value = level,
                        range = 1f..3f,
                        steps = 1,
                        onChange = { level = it },
                        onFinished = { viewModel.onVibrationLevelChanged(level.roundToInt()) },
                        help = "Fuerza de la vibración. Elige suave, normal o fuerte. En algunos teléfonos no se nota la diferencia.",
                    )
                    Text("Probar avisos", style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        BigBtn(
                            icon = Icons.Filled.Mic,
                            label = "Te escucho",
                            sub = "Un pitido corto y una vibración",
                            variant = BigBtnVariant.Accent,
                            half = true,
                            size = BigBtnSize.Compact,
                            onClick = { viewModel.onTestCue(FeedbackCues.Cue.LISTENING, "Te escucho.") },
                            modifier = Modifier.weight(1f),
                        )
                        BigBtn(
                            icon = Icons.Filled.Check,
                            label = "Éxito",
                            sub = "Dos pulsos, el segundo más largo",
                            variant = BigBtnVariant.Success,
                            half = true,
                            size = BigBtnSize.Compact,
                            onClick = { viewModel.onTestCue(FeedbackCues.Cue.SUCCESS, "Éxito.") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = "Error",
                            sub = "Tres vibraciones seguidas",
                            variant = BigBtnVariant.Danger,
                            half = true,
                            size = BigBtnSize.Compact,
                            onClick = { viewModel.onTestCue(FeedbackCues.Cue.ERROR, "Error.") },
                            modifier = Modifier.weight(1f),
                        )
                        BigBtn(
                            icon = Icons.Filled.Warning,
                            label = "Atención",
                            sub = "Dos vibraciones largas",
                            section = ModuleSection(CecapiWarning, CecapiWarning.copy(alpha = 0.15f)),
                            half = true,
                            size = BigBtnSize.Compact,
                            onClick = { viewModel.onTestCue(FeedbackCues.Cue.WARNING, "Atención.") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    BigBtn(
                        icon = Icons.Filled.Notifications,
                        label = "Notificación",
                        sub = "Tres toques rápidos",
                        variant = BigBtnVariant.Accent,
                        size = BigBtnSize.Compact,
                        onClick = { viewModel.onTestCue(FeedbackCues.Cue.INCOMING, "Llegó una notificación.") },
                    )
                }
            }
        }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di usted o tú, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** A selectable option: highlighted with a thick accent border when chosen, and it explains itself when held. */
@Composable
private fun ChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CecapiSurfaceElevated)
            .border(if (selected) BorderStroke(3.dp, CecapiAccent) else BorderStroke(1.dp, CecapiTextMuted.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (selected) "$label, seleccionada. $detail" else "$label. $detail" }
            .voiceHint(help)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) CecapiAccent else MaterialTheme.colorScheme.onBackground,
        )
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
    }
}

package com.cecapi.app.feature.modulo1_aplicacionprincipal

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The "Cámara" card. Reading text and describing what is in front of the phone both use the camera,
 * so the person picks one here, by touch or by voice, instead of hunting for two separate modules.
 * The two modes themselves are still the text reader and the environment assistant.
 */
@HiltViewModel
class CameraHubViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val deviceSettings: DeviceSettings,
) : ViewModel() {

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    // Stays alive under the text reader or the environment screen; ignore speech meant for them.
    private var screenActive = false

    // True once the person has left for a camera mode, so coming back is announced.
    private var cameFromMode = false

    init {
        viewModelScope.launch {
            val style = deviceSettings.addressStyle.first()
            voiceEngine.speak(
                style.pick(
                    "Cámara. ¿Quieres que lea un texto o que describa lo que hay enfrente? ",
                    "Cámara. ¿Desea que lea un texto o que describa lo que hay enfrente? ",
                ) + CommandCatalog.hint("cámara"),
                listenAfter = true,
            )
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.CAMERA_HUB).collect { speech -> if (screenActive) onSpeech(speech.text) }
        }
    }

    fun setScreenActive(active: Boolean) {
        val wasActive = screenActive
        screenActive = active
        if (active && !wasActive && cameFromMode) {
            cameFromMode = false
            voiceEngine.speak("Cámara. Di leer texto, o qué hay enfrente. " + CommandCatalog.hint("cámara"), listenAfter = true)
        }
    }

    fun openTextReader() = open(CecapiDestinations.DOCUMENT_READER, "Abriendo la cámara para leer texto.")

    fun openEnvironment() = open(CecapiDestinations.ENVIRONMENT, "Abriendo el asistente del entorno.")

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.CAMERA, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    private fun open(route: String, speech: String) {
        cameFromMode = true
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _routes.tryEmit(route)
    }

    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        when {
            CommandCatalog.isRequest(spoken) -> onCommandsRequested()
            listOf("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior").let { phrases -> VoiceText.hasAny(text, phrases) } -> _back.tryEmit(Unit)
            listOf("enfrente", "entorno", "descri*", "alrededor", "que hay").let { phrases -> VoiceText.hasAny(text, phrases) } -> openEnvironment()
            listOf("texto", "leer", "lee", "documento", "letra", "papel").let { phrases -> VoiceText.hasAny(text, phrases) } -> openTextReader()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak(
                    "No entendí. Di leer texto, o describir lo que hay enfrente. " + CommandCatalog.hint("cámara"),
                    listenAfter = true,
                )
            }
        }
    }
}

@Composable
fun CameraHubScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: CameraHubViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTopBar(
            eyebrow = "MENÚ",
            title = "Cámara",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )
        Text(
            "¿Qué quieres hacer con la cámara?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        ModeCard(
            icon = Icons.Filled.TextFields,
            title = "Leer texto",
            subtitle = "Lee en voz alta documentos, carteles y etiquetas",
            example = "Di: leer texto",
            accent = Sections.Camera.color,
            help = "Leer texto. Apunta la cámara a un papel, un cartel o una etiqueta y te lo leo en voz alta. " +
                "Toca dos veces para abrir.",
            onClick = viewModel::openTextReader,
        )
        ModeCard(
            icon = Icons.Filled.Visibility,
            title = "Qué hay enfrente",
            subtitle = "Te cuenta qué objetos y cosas ve la cámara",
            example = "Di: qué hay enfrente",
            accent = Sections.Camera.color,
            help = "Qué hay enfrente. Apunta la cámara y te digo qué objetos veo. Toca dos veces para abrir.",
            onClick = viewModel::openEnvironment,
        )
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di leer texto o qué hay enfrente",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    example: String,
    accent: Color,
    help: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .clip(shape)
            .background(CecapiSurface)
            .border(2.dp, accent.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val iconShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(iconShape)
                .background(accent.copy(alpha = 0.18f))
                .border(2.dp, accent, iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(40.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted, modifier = Modifier.padding(top = 4.dp))
            Text(example, style = MaterialTheme.typography.bodyMedium, color = accent, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

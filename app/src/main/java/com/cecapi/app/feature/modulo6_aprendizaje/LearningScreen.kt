package com.cecapi.app.feature.modulo6_aprendizaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

/**
 * Primero un menú para elegir qué actividad hacer — nada de caer directo en sonidos y tener que
 * adivinar que se puede deslizar, que ni el comando de voz distinguía bien. Elegido un modo (toque
 * o diciendo su nombre), se abre su pantalla completa; "Atrás" ahí regresa a este menú, no sale de
 * Actividades de un salto.
 */
@Composable
fun LearningScreen(
    onBack: () -> Unit,
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    // Leaving the app (home button, screen lock) must stop the sound and the vibration.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.onAppStopped() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    // null = en el menú. Si dicen "vibración" mientras están en el menú (o en otra tarjeta), la
    // pantalla sigue a la voz igual que antes — solo que ahora parte de un menú, no de un swipe.
    var seleccion by remember { mutableStateOf<ActivityMode?>(null) }
    LaunchedEffect(state.mode) {
        if (seleccion != null && seleccion != state.mode) seleccion = state.mode
    }
    var recreativas by remember { mutableStateOf(false) }
    val listening = voiceState is VoiceState.Listening

    // El micrófono grande va fijo en las tres pantallas de Actividades (menú, un modo, o
    // recreativas) — el mismo control, en el mismo lugar, sea cual sea la pestaña.
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            recreativas -> {
                ScreenTopBar(
                    eyebrow = "ACTIVIDADES",
                    title = "Juegos",
                    onBack = { recreativas = false },
                    onCommands = viewModel::onCommandsRequested,
                )
                RecreationalPage()
            }
            seleccion != null -> {
                ScreenTopBar(
                    eyebrow = "ACTIVIDADES",
                    title = if (seleccion == ActivityMode.AUDIO) "Sonidos" else "Vibración",
                    onBack = { seleccion = null },
                    onCommands = viewModel::onCommandsRequested,
                )
                ActivityPage(
                    pageMode = seleccion!!,
                    state = state,
                    onRepeat = viewModel::repeat,
                    onNext = viewModel::next,
                    onSetLevel = viewModel::setLevel,
                    onLeave = viewModel::leave,
                )
            }
            else -> {
                ScreenTopBar(
                    eyebrow = "MENÚ",
                    title = "Actividades",
                    onBack = onBack,
                    onCommands = viewModel::onCommandsRequested,
                )
                BigBtn(
                    icon = Icons.Filled.Hearing,
                    label = "Sonidos",
                    section = Sections.Activities,
                    onClick = { viewModel.setMode(ActivityMode.AUDIO); seleccion = ActivityMode.AUDIO },
                )
                BigBtn(
                    icon = Icons.Filled.Vibration,
                    label = "Vibración",
                    section = Sections.Activities,
                    onClick = { viewModel.setMode(ActivityMode.VIBRATION); seleccion = ActivityMode.VIBRATION },
                )
                BigBtn(
                    icon = Icons.Filled.SportsEsports,
                    label = "Juegos",
                    section = Sections.Activities,
                    onClick = { recreativas = true },
                )
                BigBtn(
                    icon = Icons.Filled.SportsEsports,
                    label = "Retos IA · Próximamente",
                    section = Sections.Activities,
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di sonidos o vibración, o toca aquí",
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

/**
 * One full card: the exercise (title, instructions, feedback) and its controls. [pageMode] is which card
 * this is; while it is not the active mode in [state] (mid-swipe, or state has not caught up yet) it shows
 * a light placeholder instead of the other card's stale exercise, since the ViewModel only ever loads one
 * mode's exercises at a time.
 */
@Composable
private fun ActivityPage(
    pageMode: ActivityMode,
    state: ActivitiesUiState,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onSetLevel: (Int) -> Unit,
    onLeave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.mode != pageMode) {
            Text(
                "Preparando…",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(8.dp),
            )
            return@Column
        }

        if (pageMode == ActivityMode.AUDIO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                (1..3).forEach { level ->
                    BigBtn(
                        icon = Icons.Filled.Hearing,
                        label = "Nivel $level",
                        section = Sections.Activities,
                        variant = if (state.level == level) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                        size = BigBtnSize.Secondary,
                        half = true,
                        onClick = { onSetLevel(level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (state.finished) {
            val resultado = state.finalMessage ?: "Acertaste ${state.correctCount} de ${state.items.size}"
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CecapiSurface)
                    .border(2.dp, CecapiAccent.copy(alpha = 0.5f), shape)
                    .padding(20.dp)
                    .semantics { contentDescription = "Resultado. $resultado" },
            ) {
                Text("Resultado", style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
                Text(resultado, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(
                    icon = Icons.Filled.Replay,
                    label = "Repetir",
                    help = "Repetir. Vuelve a empezar la actividad desde el primer ejercicio. También puedes decir: repetir.",
                    onClick = onRepeat,
                )
                TopAction(
                    icon = Icons.Filled.Close,
                    label = "Salir",
                    help = "Salir. Vuelve al menú. También puedes decir: salir.",
                    onClick = onLeave,
                )
            }
            return@Column
        }

        val item = state.current
        if (item == null) {
            Text(
                "Preparando los ejercicios…",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(8.dp),
            )
            return@Column
        }

        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CecapiSurface)
                .border(2.dp, CecapiAccent.copy(alpha = 0.5f), shape)
                .padding(20.dp),
        ) {
            Text(
                "Ejercicio ${state.index + 1} de ${state.items.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
            Text(item.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        }

        state.correct?.let { correct ->
            Text(
                if (correct) "Correcto" else "Incorrecto",
                style = MaterialTheme.typography.titleLarge,
                color = if (correct) CecapiSuccess else CecapiError,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigBtn(
                section = Sections.Activities,
                size = BigBtnSize.Secondary,
                half = true,
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Replay,
                label = "Repetir",
                onClick = onRepeat,
            )
            BigBtn(
                section = Sections.Activities,
                size = BigBtnSize.Secondary,
                half = true,
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.SkipNext,
                label = "Siguiente",
                onClick = onNext,
            )
        }
    }
}

/**
 * Tercera tarjeta: ideas para divertirse y estimularse, no ejercicios calificados — primer borrador de
 * contenido para ir trabajando con el Equipo 4, no algo terminado todavía.
 */
@Composable
private fun RecreationalPage() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Ideas para divertirte y estimular tus sentidos, solo o en grupo. Esto es un primer borrador: " +
                "iremos agregando y afinando más con el tiempo.",
            style = MaterialTheme.typography.bodyMedium,
            color = CecapiTextMuted,
        )
        CategoriaRecreativa.entries.forEach { categoria ->
            val actividades = RecreationalActivities.todas.filter { it.categoria == categoria }
            if (actividades.isEmpty()) return@forEach
            Text(
                categoria.etiqueta.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = CecapiAccent,
                modifier = Modifier.padding(top = 4.dp),
            )
            actividades.forEach { actividad -> ActividadRecreativaCard(actividad) }
        }
    }
}

/**
 * Solo el título se ve a simple vista — el párrafo completo nadie lo lee en una tarjeta. La
 * descripción sigue completa por voz: mantén presionado para escucharla.
 */
@Composable
private fun ActividadRecreativaCard(actividad: ActividadRecreativa) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CecapiSurface)
            .voiceHint("${actividad.titulo}. ${actividad.descripcion}")
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            actividad.titulo,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Mantén presionado para escuchar",
            style = MaterialTheme.typography.labelSmall,
            color = CecapiTextMuted,
        )
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .border(
                if (selected) BorderStroke(3.dp, CecapiAccent) else BorderStroke(1.dp, CecapiTextMuted.copy(alpha = 0.4f)),
                shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (selected) "$label, seleccionado. $help" else "$label. $help" }
            .voiceHint(help)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) CecapiAccent else MaterialTheme.colorScheme.onBackground,
        )
    }
}

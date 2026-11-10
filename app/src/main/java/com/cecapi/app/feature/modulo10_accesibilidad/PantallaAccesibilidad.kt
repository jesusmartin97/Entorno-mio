package com.cecapi.app.feature.modulo10_accesibilidad

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cecapi.app.R
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiTextPrimary
import com.cecapi.app.core.theme.CecapiWarning
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.voice.VoiceState

/**
 * Pantalla de accesibilidad: lleva a los ajustes del sistema para encender el servicio y muestra si ya
 * está encendido, además del perfil de gestos activo.
 *
 * Todavía no tiene ruta en `core/navigation` (esa parte es de Equipo 1). Cuando se agregue, la llamada es:
 * `PantallaAccesibilidad(onBack = { navController.popBackStack() })`.
 */
@Composable
fun PantallaAccesibilidad(
    onBack: () -> Unit,
    viewModel: AccesibilidadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val talkBackActivo by viewModel.talkBackActivo.collectAsState()
    val listening = voiceState is VoiceState.Listening
    var activo by remember { mutableStateOf(false) }
    var errorAjustesTalkBack by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        activo = AccesibilidadActiva.estaActivo(context)
        viewModel.actualizarTalkBack()
    }

    // El usuario puede activar o desactivar TalkBack y volver; hay que revisarlo cada vez.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.actualizarTalkBack()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                eyebrow = "ACCESIBILIDAD",
                title = "Accesibilidad",
                onBack = onBack,
                onCommands = {},
            )

            Text(
                if (activo) "El servicio está encendido." else "El servicio está apagado.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (activo) CecapiSuccess else CecapiTextMuted,
            )
            Text(
                "Con el servicio encendido, presiona volumen abajo dos veces y CECAPI leerá en voz alta " +
                    "el texto que se ve en la pantalla. La captura es temporal: no se guarda ni se envía.",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )

            if (talkBackActivo) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CecapiSurfaceElevated)
                        .border(2.dp, CecapiWarning, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.accesibilidad_talkback_aviso),
                        style = MaterialTheme.typography.headlineMedium,
                        color = CecapiWarning,
                    )
                    Text(
                        text = stringResource(R.string.accesibilidad_talkback_explicacion),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CecapiTextPrimary,
                    )
                    BigBtn(
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.accesibilidad_talkback_abrir_ajustes),
                        section = Sections.Settings,
                        size = BigBtnSize.Secondary,
                        onClick = {
                            errorAjustesTalkBack = !abrirAjustesDeAccesibilidad(context)
                        },
                    )
                    Text(
                        text = stringResource(R.string.accesibilidad_talkback_no_puede_apagar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                    )
                    if (errorAjustesTalkBack) {
                        Text(
                            text = stringResource(R.string.accesibilidad_talkback_sin_ajustes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = CecapiError,
                        )
                    }
                }
            }

            BigBtn(
                icon = Icons.Filled.Settings,
                label = "Abrir ajustes de accesibilidad",
                section = Sections.Settings,
                onClick = { AccesibilidadActiva.abrirAjustes(context) },
            )
            BigBtn(
                icon = Icons.Filled.Refresh,
                label = "Actualizar estado",
                size = BigBtnSize.Secondary,
                onClick = {
                    activo = AccesibilidadActiva.estaActivo(context)
                    viewModel.anunciarEstado(activo)
                },
            )

            Text("GESTOS DE UN DEDO", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            Text(
                "Ahora son los de Android: desliza con un dedo a la izquierda, derecha, arriba o abajo. " +
                    "CECAPI no intercepta esos movimientos.",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
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

/** Abre los ajustes de accesibilidad del sistema. Devuelve false si el teléfono no tiene esa pantalla. */
private fun abrirAjustesDeAccesibilidad(context: Context): Boolean =
    try {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

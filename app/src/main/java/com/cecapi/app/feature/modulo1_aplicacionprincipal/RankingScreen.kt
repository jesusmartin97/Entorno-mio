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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

private enum class RankingSub { MI_RANKING, TOP5 }

/**
 * Paleta sobria, a juego con el resto de la app (nada tan saturado como el dorado/morado/naranja
 * neón de antes): oro ya usado en Clasificación para el 1°, lavanda y cobre apagados para 2°-3°,
 * azul y verde ya usados en la app para 4°-5°. Un lugar se nota antes de leer el número.
 */
private data class Podio(val color: Color, val fondo: Color, val icono: ImageVector?)
private val PODIO = mapOf(
    1 to Podio(Sections.Ranking.color, Sections.Ranking.bg, Icons.Filled.EmojiEvents),
    2 to Podio(Color(0xFFB9A3E3), Color(0xFF2C2438), Icons.Filled.Star),
    3 to Podio(Color(0xFFD9A066), Color(0xFF332518), Icons.Filled.MilitaryTech),
    4 to Podio(CecapiAccent, Color(0xFF102733), null),
    5 to Podio(CecapiSuccess, Color(0xFF0E2A1B), null),
)

@Composable
fun RankingScreen(
    onBack: () -> Unit,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    var sub by remember { mutableStateOf<RankingSub?>(null) }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val miPosicion = state.individual.indexOfFirst { it.usuarioId == state.miId }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (sub) {
            null -> {
                ScreenTopBar(eyebrow = "ACTIVIDADES", title = "Clasificación", onBack = onBack, onCommands = viewModel::onCommandsRequested)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigBtn(
                        icon = Icons.Filled.BarChart,
                        label = "Ver mi ranking",
                        section = Sections.Ranking,
                        variant = BigBtnVariant.Accent,
                        half = true,
                        enabled = miPosicion >= 0,
                        onClick = { sub = RankingSub.MI_RANKING },
                        modifier = Modifier.weight(1f),
                    )
                    BigBtn(
                        icon = Icons.Filled.EmojiEvents,
                        label = "Top 5",
                        section = Sections.Ranking,
                        half = true,
                        enabled = state.individual.isNotEmpty(),
                        onClick = { sub = RankingSub.TOP5 },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("TABLA INDIVIDUAL", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)

                if (state.tieneInstitucion) {
                    // "Competir con alumnos de su institución y de otras": las dos vistas, una u otra, no mezcladas.
                    // Las instituciones en sí no compiten entre ellas — esto solo filtra a qué personas se ve.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigBtn(
                            icon = Icons.Filled.School,
                            label = "Mi institución",
                            variant = if (state.alcance == AlcanceRanking.MI_INSTITUCION) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                            half = true,
                            onClick = { viewModel.cambiarAlcance(AlcanceRanking.MI_INSTITUCION) },
                            modifier = Modifier.weight(1f),
                        )
                        BigBtn(
                            icon = Icons.Filled.Public,
                            label = "Todas",
                            variant = if (state.alcance == AlcanceRanking.TODAS) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                            half = true,
                            onClick = { viewModel.cambiarAlcance(AlcanceRanking.TODAS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Espacio reservado para competencias por tiempo (eventos/dinámicas de un educador o
                // globales de la institución, con puntos por 1°, 2° o 3° lugar) — todavía sin datos
                // reales que mostrar, así que quedan bloqueadas en vez de simular algo que no existe.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigBtn(
                        icon = Icons.Filled.CalendarViewWeek,
                        label = "Semana",
                        variant = BigBtnVariant.Locked,
                        half = true,
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    BigBtn(
                        icon = Icons.Filled.CalendarMonth,
                        label = "Mes",
                        variant = BigBtnVariant.Locked,
                        half = true,
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }

                if (state.individual.isEmpty()) {
                    val mensaje = if (state.alcance == AlcanceRanking.MI_INSTITUCION) {
                        "Todavía nadie de tu institución tiene puntos aquí. Practica en Actividades, en los sonidos, para aparecer."
                    } else {
                        "Todavía nadie tiene puntos aquí. Practica en Actividades, en los sonidos, para aparecer."
                    }
                    Text(mensaje, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
                } else {
                    state.individual.forEachIndexed { index, fila ->
                        RankingFilaRow(
                            posicion = index + 1,
                            nombre = fila.nombreMostrado,
                            detalle = "Nivel ${fila.nivelActual}",
                            puntos = fila.puntosTotales,
                            destacado = fila.usuarioId == state.miId,
                        )
                    }
                }
            }

            RankingSub.MI_RANKING -> if (miPosicion >= 0) {
                ScreenTopBar(eyebrow = "CLASIFICACIÓN", title = "Mi posición", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                val fila = state.individual[miPosicion]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Sections.Ranking.bg)
                        .border(2.dp, Sections.Ranking.color, RoundedCornerShape(20.dp))
                        .padding(vertical = 20.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("TU POSICIÓN", style = MaterialTheme.typography.labelLarge, color = Sections.Ranking.color)
                    Text("#${miPosicion + 1}", style = MaterialTheme.typography.displayLarge, color = Sections.Ranking.color)
                    Text("${fila.puntosTotales} puntos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Nivel ${fila.nivelActual}" + if (fila.origen.isNotBlank()) " · ${fila.origen}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                    )
                }
                Text("POSICIONES CERCANAS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted, modifier = Modifier.padding(top = 8.dp))
                val desde = (miPosicion - 2).coerceAtLeast(0)
                val hasta = (miPosicion + 2).coerceAtMost(state.individual.lastIndex)
                for (i in desde..hasta) {
                    val cercana = state.individual[i]
                    RankingFilaRow(
                        posicion = i + 1,
                        nombre = cercana.nombreMostrado,
                        detalle = "Nivel ${cercana.nivelActual}",
                        puntos = cercana.puntosTotales,
                        destacado = cercana.usuarioId == state.miId,
                    )
                }
            }

            RankingSub.TOP5 -> {
                ScreenTopBar(eyebrow = "LOS MEJORES", title = "Top 5", onBack = { sub = null }, onCommands = viewModel::onCommandsRequested)
                state.individual.take(5).forEachIndexed { index, fila ->
                    RankingFilaRow(
                        posicion = index + 1,
                        nombre = fila.nombreMostrado,
                        detalle = "Nivel ${fila.nivelActual}",
                        puntos = fila.puntosTotales,
                        destacado = fila.usuarioId == state.miId,
                    )
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di mi lugar, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Una fila de la tabla: 1°-3° como podio grande con medalla, 4°-5° destacadas más chico, el resto normal. */
@Composable
private fun RankingFilaRow(posicion: Int, nombre: String, detalle: String, puntos: Int, destacado: Boolean) {
    val podio = PODIO[posicion]
    if (podio == null) {
        RankingRow(posicion, nombre, detalle, puntos, destacado)
        return
    }
    val esTop3 = posicion <= 3
    val shape = RoundedCornerShape(20.dp)
    if (esTop3) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(podio.fondo)
                    .border(if (posicion == 1) 3.dp else 2.dp, podio.color, shape)
                    .let { if (destacado) it.border(2.dp, CecapiAccent, shape) else it }
                    .voiceHint("Lugar $posicion. $nombre, $detalle, $puntos puntos." + if (destacado) " Eres tú." else "")
                    .padding(if (posicion == 1) 22.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (posicion == 1) 64.dp else 52.dp)
                        .clip(CircleShape)
                        .background(CecapiBackground)
                        .border(if (posicion == 1) 3.dp else 2.dp, podio.color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(podio.icono!!, contentDescription = null, tint = podio.color, modifier = Modifier.size(if (posicion == 1) 34.dp else 28.dp))
                }
                Text(nombre, style = if (posicion == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge, color = podio.color)
                Text(detalle, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
                Text("$puntos pts", style = if (posicion == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge, color = podio.color)
            }
            if (destacado) TuBadge(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(podio.fondo)
                .border(1.dp, podio.color, RoundedCornerShape(16.dp))
                .let { if (destacado) it.border(2.dp, CecapiAccent, RoundedCornerShape(16.dp)) else it }
                .voiceHint("Lugar $posicion. $nombre, $detalle, $puntos puntos." + if (destacado) " Eres tú." else "")
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(podio.color.copy(alpha = 0.18f)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$posicion", style = MaterialTheme.typography.labelLarge, color = podio.color)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = CecapiTextMuted)
            }
            Text("$puntos pts", style = MaterialTheme.typography.titleMedium, color = podio.color)
        }
            if (destacado) TuBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

@Composable
private fun RankingRow(posicion: Int, nombre: String, detalle: String, puntos: Int, destacado: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(CecapiSurfaceElevated)
                .let { if (destacado) it.border(2.dp, CecapiAccent, shape) else it }
                .voiceHint("Lugar $posicion. $nombre, $detalle, $puntos puntos." + if (destacado) " Eres tú." else "")
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(CecapiAccent.copy(alpha = 0.18f)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$posicion", style = MaterialTheme.typography.labelLarge, color = CecapiAccent)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = CecapiTextMuted)
            }
            Text("$puntos pts", style = MaterialTheme.typography.titleMedium, color = CecapiAccent)
        }
        if (destacado) TuBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

/** Para que "tú" se note sin importar el color del lugar o si quedaste fuera del podio. */
@Composable
private fun TuBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CecapiAccent)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text("TÚ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = CecapiBackground)
    }
}

package com.cecapi.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted

/** One card of the main menu: a module, or Configuración. [help] is what is read out when it is held down. */
data class MenuItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val help: String = "$title. $subtitle. Toca dos veces para abrir.",
) {
    companion object {
        const val SETTINGS_KEY = "SETTINGS"
        const val PERSONALIZATION_KEY = "PERSONALIZATION"
        const val CAMERA_KEY = "CAMERA"
        const val CHATS_KEY = "CHATS"

        val Camera = MenuItem(
            key = CAMERA_KEY,
            title = "Cámara",
            subtitle = "Leer texto y describir lo que hay enfrente",
            accent = Color(0xFF4ADE80),
            help = "Cámara. Con ella puedo leerte un texto o describirte lo que hay enfrente. " +
                "Toca dos veces para abrir.",
        )

        val Chats = MenuItem(
            key = CHATS_KEY,
            title = "Chats",
            subtitle = "Tus conversaciones con el asistente",
            accent = Color(0xFF60A5FA),
            help = "Chats. Aquí escuchas, repasas y borras tus conversaciones con el asistente. " +
                "Toca dos veces para abrir.",
        )

        val Personalization = MenuItem(
            key = PERSONALIZATION_KEY,
            title = "Personalización",
            subtitle = "Mi voz, cómo te llamo y cómo me llamas",
            accent = Color(0xFFB794F6),
            help = "Personalización. Aquí eliges la voz del asistente, cómo te llamo, cómo me llamas y los avisos. " +
                "Toca dos veces para abrir.",
        )

        val Settings = MenuItem(
            key = SETTINGS_KEY,
            title = "Configuración",
            subtitle = "Voz, avisos y cuenta",
            accent = Color(0xFF9AA0AE),
            help = "Configuración. Aquí cambias qué tan rápido hablo, el volumen, los sonidos y las vibraciones, " +
                "y mi nombre. Toca dos veces para abrir.",
        )
    }
}

fun ModuloCecapi.toMenuItem() = MenuItem(storageCode, title, subtitle, accent)

/**
 * The main menu: cards side by side in a swipeable row. Each has room for its own icon, name and
 * description, unlike the two-column grid where the text was squeezed on narrow phones.
 */
@Composable
fun ModuleCarousel(
    items: List<MenuItem>,
    onItemClick: (MenuItem) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.key }) { item ->
            Column(
                modifier = Modifier
                    .width(190.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CecapiSurface)
                    .clickable { onItemClick(item) }
                    .semantics { contentDescription = item.help }
                    .voiceHint(item.help)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(item.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(item.accent))
                }
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

package com.cecapi.app.core.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.ModuleSection
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiTextPrimary
import com.cecapi.app.core.theme.CecapiWarning

/**
 * The microphone is the main control of the whole app: a circular button inside its own
 * docked panel, matching the Figma prototype's MicBar exactly (132dp circle, pulse ring
 * while listening, "Di lista de comandos" caption underneath). Used on the home screen
 * and on the signed-in dashboard so both feel the same.
 */
@Composable
fun MicPad(
    listening: Boolean,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
) {
    val ringTransition = rememberInfiniteTransition(label = "mic-ring")
    val ringScale by ringTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "mic-ring-scale",
    )
    val ringAlpha by ringTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "mic-ring-alpha",
    )
    val helper = LocalVoiceHelp.current
    val holdHelp = "Este es el micrófono. Tócalo una vez para hablarme y dime lo que necesitas, " +
        "por ejemplo: módulos disponibles, o estado del teléfono. Tócalo dos veces seguidas para que se calle."

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = CecapiBorder, thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CecapiSurface)
                .padding(top = 18.dp, start = 24.dp, end = 24.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .pointerInput(onDoubleTap, holdHelp) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onDoubleTap = { onDoubleTap?.invoke() },
                            onLongPress = { helper?.speak(holdHelp) },
                        )
                    }
                    .semantics {
                        contentDescription = "Micrófono. Toca dos veces para hablar con el asistente."
                        onClick(label = "Hablar") { onClick(); true }
                        onDoubleTap?.let { silence ->
                            customActions = listOf(
                                CustomAccessibilityAction("Silenciar al asistente") { silence(); true },
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (listening) {
                    Box(
                        modifier = Modifier
                            .size(158.dp)
                            .graphicsLayer {
                                scaleX = ringScale
                                scaleY = ringScale
                                alpha = ringAlpha
                            }
                            .clip(CircleShape)
                            .border(3.dp, CecapiAccent, CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(CecapiBackground)
                        .border(if (listening) 3.dp else 2.dp, if (listening) CecapiAccent else CecapiBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (listening) CecapiAccent else CecapiTextPrimary,
                        modifier = Modifier.size(58.dp),
                    )
                }
            }
            Text(
                text = hint,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (listening) CecapiAccent else CecapiTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .height(64.dp)
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
            )
            Text(
                text = "Di lista de comandos",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).height(24.dp),
            )
        }
    }
}

/** Which look a [BigBtn] takes when it has no [section]. */
enum class BigBtnVariant { Neutral, Accent, Danger, Success, Locked }

/**
 * Los 6 tamaños que puede tener una tarjeta grande en toda la app ("Volver" y el micrófono no
 * pasan por BigBtn, son sus propios estándares aparte). [Standard] es el que ya usaba casi toda
 * la pantalla y se queda como el default.
 * [Hero] = tamaño 1, la más grande (referencia: el micrófono y el 1er lugar del podio).
 * [Standard] = tamaño 3, el más común: el de casi todos los botones de la app.
 * [Secondary] = tamaño 2, más chica: opciones bloqueadas/secundarias, o rejillas densas de 2.
 * [Compact] = tamaño 6: como un [Standard] pero en rejilla de 2, para cuando el contenido no
 * necesita tanto espacio (ej. "Probar avisos") — más grande que [Secondary], más chica que
 * [Standard] a ancho completo.
 */
enum class BigBtnSize { Hero, Standard, Secondary, Compact }

/**
 * The recurring full-width button from the Figma prototype: a big icon badge on top,
 * a bold label below, an optional muted subtitle under that — everything centered. This
 * is the main building block of almost every screen (Home, Login, Register, the signed-in
 * menu...), so screens should prefer this over ad-hoc buttons to stay visually consistent.
 */
@Composable
fun BigBtn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    section: ModuleSection? = null,
    variant: BigBtnVariant = BigBtnVariant.Neutral,
    enabled: Boolean = true,
    // Two of these share one row's width when the action is not the screen's priority
    // (e.g. Iniciar sesión / Crear cuenta on Home) — matches the Figma prototype's HalfBtn.
    half: Boolean = false,
    size: BigBtnSize = BigBtnSize.Standard,
) {
    val tint = section?.color ?: when (variant) {
        BigBtnVariant.Danger -> CecapiError
        BigBtnVariant.Success -> CecapiSuccess
        BigBtnVariant.Accent -> CecapiAccent
        BigBtnVariant.Locked -> CecapiTextMuted
        BigBtnVariant.Neutral -> CecapiTextPrimary
    }
    val borderColor = section?.color ?: when (variant) {
        BigBtnVariant.Danger -> CecapiError
        BigBtnVariant.Success -> CecapiSuccess
        BigBtnVariant.Accent -> CecapiAccent
        else -> CecapiBorder
    }
    val borderWidth = if (variant == BigBtnVariant.Neutral && section == null) 1.dp else 2.dp
    val iconBg = section?.bg ?: CecapiSurfaceElevated
    val shape = RoundedCornerShape(20.dp)
    val help = if (sub != null) "$label. $sub" else label
    val locked = variant == BigBtnVariant.Locked

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CecapiSurface)
            .border(borderWidth, borderColor, shape)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled && !locked, onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(
                top = if (half) 18.dp else 20.dp,
                start = if (half) 10.dp else 16.dp,
                end = if (half) 10.dp else 16.dp,
                bottom = if (half) 16.dp else 18.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (half) 10.dp else 12.dp),
    ) {
        val iconSize = when (size) {
            BigBtnSize.Hero -> if (half) 64.dp else 76.dp
            BigBtnSize.Standard -> if (half) 56.dp else 64.dp
            BigBtnSize.Compact -> if (half) 50.dp else 58.dp
            BigBtnSize.Secondary -> if (half) 44.dp else 50.dp
        }
        val iconShape = RoundedCornerShape(if (half) 16.dp else 18.dp)
        Box(
            modifier = Modifier.size(iconSize).clip(iconShape).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (locked) Icons.Filled.Lock else icon,
                contentDescription = null,
                tint = if (locked) CecapiTextMuted else tint,
                modifier = Modifier.size(
                    when (size) {
                        BigBtnSize.Hero -> if (half) 44.dp else 48.dp
                        BigBtnSize.Standard -> if (half) 36.dp else 44.dp
                        BigBtnSize.Compact -> if (half) 30.dp else 36.dp
                        BigBtnSize.Secondary -> if (half) 26.dp else 30.dp
                    },
                ),
            )
        }
        Text(
            text = label,
            fontSize = when (size) {
                BigBtnSize.Hero -> if (half) 20.sp else 24.sp
                BigBtnSize.Standard -> if (half) 18.sp else 22.sp
                BigBtnSize.Compact -> if (half) 17.sp else 20.sp
                BigBtnSize.Secondary -> if (half) 15.sp else 17.sp
            },
            fontWeight = FontWeight.Black,
            lineHeight = if (half) 23.sp else 29.sp,
            color = tint,
            textAlign = TextAlign.Center,
        )
        if (sub != null && !half) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A large labeled icon button for a screen's header (sign in, sign out...). Pass a different [tint]
 * for actions that must stand out, like the red "Cerrar sesión".
 */
@Composable
fun TopAction(
    icon: ImageVector,
    label: String,
    help: String,
    onClick: () -> Unit,
    tint: Color = CecapiAccent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(iconShape)
                .background(tint.copy(alpha = 0.14f))
                .border(2.dp, tint, iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (tint == CecapiAccent) CecapiTextMuted else tint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A phrase the user can say, also tappable. Shown under "PUEDES DECIR". Tall enough to hit (48 dp). */
@Composable
fun SuggestionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(CecapiSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "\"$label\"", color = CecapiAccent, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Something that needs the user's attention and is not repeated by voice on screen.
 * An optional [actionLabel]/[onAction] adds a button below the text, for when there is
 * somewhere concrete to send the person instead of only naming the problem.
 */
@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CecapiWarning.copy(alpha = 0.15f))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text = text, color = CecapiWarning, style = MaterialTheme.typography.bodyLarge)
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp).voiceHint("$actionLabel. $text"),
            ) {
                Text(actionLabel, color = CecapiAccent)
            }
        }
    }
}

/** Shown only while there is no internet: answers will be less precise. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    NoticeBanner("Sin conexión a internet. Mis respuestas pueden ser menos precisas.", modifier)
}

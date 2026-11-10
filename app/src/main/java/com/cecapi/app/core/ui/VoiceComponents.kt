package com.cecapi.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted

/**
 * Shared "tap to talk" control used by every module screen (Home, Asistente de
 * Voz, IA, OCR, Solicitudes, Aprendizaje, Entorno). Extracted because it is the
 * one interaction pattern that must look and behave identically everywhere —
 * users learn it once on the home screen and rely on muscle memory afterwards.
 */
@Composable
fun VoiceMicButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
    onDoubleTap: (() -> Unit)? = null,
) {
    val helper = LocalVoiceHelp.current
    val holdHelp = "Micrófono. Tócalo una vez para hablar. Tócalo dos veces seguidas para que se calle."
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(CecapiSurfaceElevated)
            // One gesture detector for the three touch behaviors, so they can never overlap: a single tap
            // interacts, two taps in a row silences the assistant (TalkBack's own double-tap stays the
            // activation gesture, through the semantics onClick below), and holding down only speaks what
            // the control does, without also counting as a tap or a double tap once the finger lifts.
            .pointerInput(onDoubleTap, holdHelp) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleTap?.invoke() },
                    onLongPress = { helper?.speak(holdHelp) },
                )
            }
            .semantics {
                contentDescription = "Micrófono. Toca dos veces para hablar."
                onClick(label = "Hablar") { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = if (isListening) CecapiAccent else CecapiTextMuted,
            modifier = Modifier.size(size / 2.2f),
        )
    }
}

/**
 * The recurring "what the app just said" caption bubble seen throughout the prototype.
 * Pass [plain] = true over a live camera image (e.g. "Qué hay enfrente"): no card, no
 * background, just the text with a shadow for contrast — Pp's rule is that this one
 * screen never gets a text box floating over the picture.
 */
@Composable
fun VoiceCaptionBubble(text: String, modifier: Modifier = Modifier, plain: Boolean = false) {
    val textStyle = if (plain) {
        MaterialTheme.typography.bodyLarge.copy(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = androidx.compose.ui.graphics.Color.Black,
                blurRadius = 12f,
            ),
        )
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val textColor = if (plain) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (plain) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(20.dp)).background(CecapiSurface).padding(16.dp)
                },
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = if (plain) androidx.compose.ui.graphics.Color.White else CecapiAccent,
            )
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

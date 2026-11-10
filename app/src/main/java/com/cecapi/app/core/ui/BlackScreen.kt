package com.cecapi.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics

/**
 * Covers the whole screen in black and takes over every touch, so nothing underneath can be pressed
 * by accident. A tap opens the microphone; holding a finger down switches the black screen off.
 * The way out never depends on the voice working.
 */
@Composable
fun BlackScreen(
    onTap: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onHold() })
            }
            .semantics {
                contentDescription =
                    "Pantalla negra activada. Toca dos veces para hablar. Mantén presionado para volver a la pantalla normal."
                onClick(label = "Hablar") {
                    onTap()
                    true
                }
                onLongClick(label = "Volver a la pantalla normal") {
                    onHold()
                    true
                }
            },
    )
}

package com.cecapi.app.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.cecapi.app.core.voice.VoiceEngine
import kotlinx.coroutines.withTimeoutOrNull

/** Lets any composable make the assistant say something out loud without knowing about the engine. */
class VoiceHelp(private val voiceEngine: VoiceEngine) {
    fun speak(text: String) = voiceEngine.speak(text)
}

val LocalVoiceHelp = staticCompositionLocalOf<VoiceHelp?> { null }

/**
 * Makes a control explain itself out loud, for people who cannot see it:
 * - [focusLabel]: said when the control is selected (a text field gets focus), to say where they are;
 * - [help]: said when the finger stays pressed on it, to say what it is and what it does.
 * The press is only observed, never consumed, so a tap still does exactly what it did before.
 */
@Composable
fun Modifier.voiceHint(help: String, focusLabel: String? = null): Modifier {
    val helper = LocalVoiceHelp.current
    val withHold = pointerInput(help) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var held = true
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + 100) {
                waitForUpOrCancellation(PointerEventPass.Initial)
                held = false
            }
            if (held) helper?.speak(help)
        }
    }
    return if (focusLabel == null) {
        withHold
    } else {
        withHold.onFocusChanged { if (it.isFocused) helper?.speak(focusLabel) }
    }
}

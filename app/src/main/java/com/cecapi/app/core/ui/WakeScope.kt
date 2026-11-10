package com.cecapi.app.core.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.cecapi.app.core.voice.WakeWordController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WakeScopeViewModel @Inject constructor(
    private val wakeWordController: WakeWordController,
) : ViewModel() {
    private var holding = false

    fun set(enabled: Boolean) {
        if (enabled == holding) return
        holding = enabled
        if (enabled) wakeWordController.acquire() else wakeWordController.release()
    }

    override fun onCleared() {
        set(false)
        super.onCleared()
    }
}

/**
 * Wrap any screen in this and "hola" (or the assistant's name) works there while it is showing.
 * The screen itself needs no changes: whatever is said next reaches it through the usual speech flow.
 */
@Composable
fun WakeScope(content: @Composable () -> Unit) {
    val viewModel: WakeScopeViewModel = hiltViewModel()
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    DisposableEffect(granted) {
        viewModel.set(granted)
        onDispose { viewModel.set(false) }
    }
    content()
}

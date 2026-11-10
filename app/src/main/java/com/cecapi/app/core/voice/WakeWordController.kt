package com.cecapi.app.core.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns wake-word listening ("hola" or the user's name for the assistant) on while at least one
 * screen wants it. Screens call [acquire] when they appear and [release] when they go away.
 * Counting holders (instead of a plain on/off) keeps it correct while navigating: the next screen
 * acquires before the previous one releases, and listening never drops in between.
 */
@Singleton
class WakeWordController @Inject constructor(
    private val voiceEngine: VoiceEngine,
    assistantPreferences: AssistantPreferences,
    deviceSettings: DeviceSettings,
) {
    private val holders = MutableStateFlow(0)

    // Holders that need the mic open for anything the user says, not just "hola" (e.g. the login flow).
    private val openMicHolders = MutableStateFlow(0)

    // False while the app is not on screen: the mic must be released so the phone behaves
    // normally (calls, other apps' voice features) until the user opens CECAPI again...
    private val foreground = MutableStateFlow(true)

    // ...unless the person turned on "escuchar fuera de la app": then the background service keeps it going.
    private val backgroundService = MutableStateFlow(false)

    init {
        val words = combine(assistantPreferences.assistantName, deviceSettings.holaEnabled) { name, hola ->
            listOfNotNull(if (hola) DEFAULT_WAKE_WORD else null, name.takeIf { it.isNotBlank() })
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            combine(words, holders, openMicHolders, foreground, backgroundService) {
                wakeWords, count, open, visible, service ->
                Listening(wakeWords, count > 0 && (visible || service), open > 0)
            }.collect { state ->
                if (state.active) {
                    voiceEngine.startWakeWord(state.words, state.openMic)
                } else {
                    voiceEngine.stopWakeWord()
                }
            }
        }
    }

    private data class Listening(val words: List<String>, val active: Boolean, val openMic: Boolean)

    fun setForeground(visible: Boolean) {
        foreground.value = visible
    }

    fun setBackgroundService(active: Boolean) {
        backgroundService.value = active
    }

    /** [openMic]: also react to anything the user says, not only "hola". Pair with the same flag in [release]. */
    fun acquire(openMic: Boolean = false) {
        holders.update { it + 1 }
        if (openMic) openMicHolders.update { it + 1 }
    }

    fun release(openMic: Boolean = false) {
        holders.update { maxOf(0, it - 1) }
        if (openMic) openMicHolders.update { maxOf(0, it - 1) }
    }

    private companion object {
        const val DEFAULT_WAKE_WORD = "hola"
    }
}

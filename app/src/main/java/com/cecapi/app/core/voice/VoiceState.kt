package com.cecapi.app.core.voice

/**
 * Global state of the shared voice engine. Every screen observes this instead of
 * managing its own SpeechRecognizer/TextToSpeech instance, because Android only
 * allows meaningful use of one recognizer/TTS engine per process at a time.
 */
sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data class Speaking(val text: String) : VoiceState
    /**
     * [openTtsSettings]: true only when the phone has no text-to-speech engine ready — some phones
     * ship without one picked by default even after installing one, so the screen offers a button
     * straight to the system's "text-to-speech output" settings instead of just naming the problem.
     */
    data class Error(val message: String, val openTtsSettings: Boolean = false) : VoiceState
}

/** A finished, high-confidence speech-to-text result ready to be interpreted as a command. */
data class RecognizedSpeech(val text: String, val timestampMillis: Long)

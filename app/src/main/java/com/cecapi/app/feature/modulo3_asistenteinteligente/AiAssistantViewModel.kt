package com.cecapi.app.feature.modulo3_asistenteinteligente

import com.cecapi.app.core.navigation.CecapiDestinations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: AiAssistantRepository,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    val history: StateFlow<List<ConsultaIaEntity>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> repository.observeRecent(usuario.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        voiceEngine.speak("Este es el asistente inteligente. Toca el micrófono y hazme una pregunta.")
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.AI_ASSISTANT).collect { speech -> onQuestionAsked(speech.text) }
        }
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onQuestionAsked(pregunta: String) {
        val usuario = sessionRepository.currentUser.value ?: run {
            // Nobody signed in: say so instead of ignoring the user in silence.
            voiceEngine.speak(com.cecapi.app.core.voice.VoiceMessages.NEEDS_LOGIN)
            return
        }
        if (!isOnline.value) {
            voiceEngine.speak("No hay conexión a internet. El asistente inteligente necesita internet para responder.")
            return
        }
        viewModelScope.launch {
            val resultado = repository.ask(usuario.id, pregunta)
            resultado.onSuccess { respuesta ->
                voiceEngine.speak(respuesta)
            }.onFailure {
                voiceEngine.speak("No pude conectar con el asistente inteligente. Intenta de nuevo en un momento.")
            }
        }
    }
}

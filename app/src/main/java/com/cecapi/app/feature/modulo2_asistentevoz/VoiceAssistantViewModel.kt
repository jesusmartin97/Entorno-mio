package com.cecapi.app.feature.modulo2_asistentevoz

import com.cecapi.app.core.navigation.CecapiDestinations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceAssistantViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: VoiceAssistantRepository,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val recentCommands: StateFlow<List<ComandoVozEntity>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> repository.observeRecent(usuario.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _navEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<String> = _navEvents

    init {
        voiceEngine.speak(
            "Este es el asistente de voz. Di el nombre de cualquier módulo para abrirlo, " +
                "por ejemplo cámara inteligente o centro de aprendizaje.",
        )
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.VOICE_ASSISTANT).collect { speech ->
                val usuario = sessionRepository.currentUser.value ?: return@collect
                val text = speech.text.lowercase()
                val match = ModuloCecapi.entries.firstOrNull { modulo ->
                    text.contains(modulo.title.lowercase()) || text.contains(modulo.subtitle.lowercase())
                }
                val respuesta = if (match != null) {
                    match.voicePrompt
                } else {
                    "No reconocí ese módulo. Intenta decir el nombre completo."
                }
                voiceEngine.speak(respuesta)
                repository.logInteraction(usuario.id, speech.text, match, respuesta)
                if (match != null) _navEvents.tryEmit(match.route)
            }
        }
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()
}

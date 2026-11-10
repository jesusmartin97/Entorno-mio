package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Emergencias: los contactos son de cada persona. Nadie más los ve, ni la institución ni la app. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmergenciasViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val contactoDao: ContactoEmergenciaDao,
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val contactos: StateFlow<List<ContactoEmergenciaEntity>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { contactoDao.observeDeUsuario(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        voiceEngine.speak(
            "Emergencias. Puedes llamar a tus contactos, enviar tu ubicación o llamar al 911. Agrega a un adulto y a una persona de tu confianza.",
        )
    }

    fun onAgregarContacto(nombre: String, telefono: String, relacion: String) {
        val user = sessionRepository.currentUser.value ?: return
        val numero = telefono.filter { it.isDigit() || it == '+' }
        if (nombre.isBlank() || numero.length < 7) {
            cues.play(FeedbackCues.Cue.ERROR)
            voiceEngine.speak("Falta el nombre o un teléfono válido.")
            return
        }
        viewModelScope.launch {
            contactoDao.insert(
                ContactoEmergenciaEntity(
                    usuarioId = user.id,
                    nombre = nombre.trim(),
                    telefono = numero,
                    relacion = relacion.trim().ifBlank { null },
                ),
            )
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo, guardé a ${nombre.trim()} como contacto de emergencia.")
        }
    }

    fun onBorrarContacto(contacto: ContactoEmergenciaEntity) {
        val user = sessionRepository.currentUser.value ?: return
        viewModelScope.launch {
            contactoDao.borrar(contacto.id, user.id)
            voiceEngine.speak("Quité a ${contacto.nombre} de tus contactos.")
        }
    }

    fun onBack() {
        _back.tryEmit(Unit)
    }

    fun onMicTapped() = voiceEngine.startListening()

    fun onMicDoubleTap() = voiceEngine.mute()
}

package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reportar un incidente: la persona escribe, elige si va con su nombre o anónimo, y a quién lo envía. */
@HiltViewModel
class ReportarIncidenteViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val incidenciaDao: IncidenciaDao,
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
) : ViewModel() {

    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val _enviado = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val enviado: SharedFlow<Unit> = _enviado

    init {
        voiceEngine.speak(
            "Reportar un incidente. Escribe o dicta qué pasó, elige si va con tu nombre o anónimo, y a quién lo envías.",
        )
    }

    /** A quién puede mandar esta persona, según su rol. */
    fun destinatarios(): List<RolUsuario> {
        val rol = currentUser.value?.rol ?: return emptyList()
        return RolePermissions.destinatariosDeReporte(RolUsuario.fromCodigo(rol))
    }

    fun onEnviar(mensaje: String, destino: RolUsuario, anonimo: Boolean) {
        val user = currentUser.value ?: return
        if (mensaje.isBlank() || destino !in destinatarios()) return
        viewModelScope.launch {
            incidenciaDao.insert(
                IncidenciaEntity(
                    reportanteId = user.id,
                    destinoOrigen = user.origen.ifBlank { null },
                    mensaje = mensaje.trim(),
                    destinatarioRol = destino.codigo,
                    anonimo = anonimo,
                ),
            )
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo, envié tu reporte.")
            _enviado.tryEmit(Unit)
        }
    }

    fun onBack() {
        _back.tryEmit(Unit)
    }

    fun onMicTapped() = voiceEngine.startListening()

    fun onMicDoubleTap() = voiceEngine.mute()
}

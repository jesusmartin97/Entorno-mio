package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SoporteUiState(
    val rol: RolUsuario = RolUsuario.USUARIO,
    /** Solo para alumno: null significa que todavía no tiene educador asignado. */
    val nombreEducador: String? = null,
    val cargandoEducador: Boolean = true,
    val esperandoMensaje: Boolean = false,
    val misIncidencias: List<IncidenciaEntity> = emptyList(),
)

/**
 * Pantalla simple para alumno y usuario independiente: muy poco que administrar, a diferencia de
 * [GestionViewModel] (administrador/directivo/educador). Un alumno ve quién es su educador; ambos pueden
 * reportar un problema con la app o con alguien de su institución (o, sin institución, directo a los
 * administradores — ver [RolePermissions.destinoDeIncidencia]).
 */
@HiltViewModel
class SoporteViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val usuarioDao: UsuarioDao,
    private val incidenciaDao: IncidenciaDao,
    private val cues: FeedbackCues,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoporteUiState())
    val uiState: StateFlow<SoporteUiState> = _uiState.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        val usuario = sessionRepository.currentUser.value
        _uiState.value = _uiState.value.copy(rol = usuario?.let { RolUsuario.fromCodigo(it.rol) } ?: RolUsuario.USUARIO)

        if (usuario != null) {
            viewModelScope.launch {
                incidenciaDao.observeDeReportante(usuario.id).collect { lista ->
                    _uiState.value = _uiState.value.copy(misIncidencias = lista)
                }
            }
        }

        if (usuario != null && RolUsuario.fromCodigo(usuario.rol) == RolUsuario.ALUMNO) {
            viewModelScope.launch {
                val educadorId = usuario.educadorId
                val nombre = educadorId?.let { usuarioDao.findById(it)?.nombreCompleto }
                _uiState.value = _uiState.value.copy(nombreEducador = nombre, cargandoEducador = false)
            }
        } else {
            _uiState.value = _uiState.value.copy(cargandoEducador = false)
        }

        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.SOPORTE).collect { speech -> onSpeech(speech.text) }
        }

        val saludo = if (_uiState.value.rol == RolUsuario.ALUMNO) {
            "Aquí ves quién es tu educador y puedes reportar un problema. Di mi educador, o reportar un problema."
        } else {
            "Aquí puedes reportar un problema con la aplicación. Di reportar un problema."
        }
        voiceEngine.speak(saludo, listenAfter = true)
    }

    private fun onSpeech(spoken: String) {
        if (_uiState.value.esperandoMensaje) {
            capturarMensaje(spoken)
            return
        }
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            has("mi educador", "mi maestro", "quien es mi educador", "quien es mi maestro") -> speakEducador()
            has("reportar", "reporta", "tengo un problema", "problema", "incidencia", "queja") -> pedirMensaje()
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. Di mi educador, reportar un problema, o atrás.", listenAfter = true)
            }
        }
    }

    private fun speakEducador() {
        if (_uiState.value.rol != RolUsuario.ALUMNO) {
            voiceEngine.speak("Esto es solo para alumnos. Di reportar un problema.", listenAfter = true)
            return
        }
        val nombre = _uiState.value.nombreEducador
        voiceEngine.speak(
            if (nombre != null) "Tu educador es $nombre." else "Todavía no tienes un educador asignado.",
            listenAfter = true,
        )
    }

    fun hayCuenta(): Boolean = sessionRepository.currentUser.value != null

    /** Mensajes de Ayuda y soporte de todas las personas; solo los ve el administrador. */
    val mensajesDeSoporte: StateFlow<List<IncidenciaEntity>> = incidenciaDao.observeSoporte()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reporte técnico escrito: mismo destino que el dictado. */
    fun enviarEscrito(mensaje: String) {
        if (mensaje.isBlank()) return
        capturarMensaje(mensaje)
    }

    fun responder(incidencia: IncidenciaEntity, texto: String) {
        if (texto.isBlank()) return
        viewModelScope.launch {
            incidenciaDao.responder(incidencia.id, texto.trim())
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Respondí el mensaje.")
        }
    }

    fun eliminarMensaje(incidencia: IncidenciaEntity) {
        viewModelScope.launch {
            incidenciaDao.borrar(incidencia.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Eliminé el mensaje.")
        }
    }

    fun marcarResuelto(incidencia: IncidenciaEntity) {
        viewModelScope.launch {
            incidenciaDao.marcarResuelta(incidencia.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Marqué el mensaje como resuelto.")
        }
    }

    fun pedirMensaje() {
        _uiState.value = _uiState.value.copy(esperandoMensaje = true)
        voiceEngine.rawInput = true // lo que diga a continuación es el mensaje completo, no un comando
        voiceEngine.speak("Dime qué pasó. Puedes decir todo lo que quieras.", listenAfter = true)
    }

    private fun capturarMensaje(mensaje: String) {
        voiceEngine.rawInput = false
        _uiState.value = _uiState.value.copy(esperandoMensaje = false)
        if (mensaje.isBlank()) {
            voiceEngine.speak("No escuché nada. Di reportar un problema para intentarlo de nuevo.", listenAfter = true)
            return
        }
        val usuario = sessionRepository.currentUser.value
        if (usuario == null) {
            voiceEngine.speak("Para escribirnos primero inicia sesión. Así podemos responderte.", listenAfter = true)
            return
        }
        val destino = when (val d = RolePermissions.destinoDeIncidencia(usuario)) {
            is DestinoIncidencia.InstitucionDe -> d.origen
            DestinoIncidencia.Administradores -> null
        }
        viewModelScope.launch {
            incidenciaDao.insert(
                IncidenciaEntity(
                    reportanteId = usuario.id,
                    destinoOrigen = destino,
                    mensaje = mensaje.trim(),
                    destinatarioRol = DESTINO_SOPORTE,
                ),
            )
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo, lo reporté. Gracias por avisar.", listenAfter = true)
        }
    }

    fun onCommandsRequested() {
        val texto = if (_uiState.value.rol == RolUsuario.ALUMNO) {
            "Di mi educador para saber quién es, o reportar un problema para avisar de algo. Atrás, para volver al menú."
        } else {
            "Di reportar un problema para avisar de algo. Atrás, para volver al menú."
        }
        voiceEngine.speak(texto, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }
}

private const val DESTINO_SOPORTE = "soporte"

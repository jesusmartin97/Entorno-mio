package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.PasswordHasher
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

/**
 * Editar mi cuenta: su propia pantalla (antes vivía plegada dentro de Configuración). Cada quien
 * edita solo la suya — Gestión nunca cambia esto, solo el rol — y pide la contraseña actual para
 * confirmar, igual que al borrar la cuenta.
 */
@HiltViewModel
class EditAccountViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
) : ViewModel() {

    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val _guardado = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val guardado: SharedFlow<Unit> = _guardado

    private val _errorMensaje = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMensaje: SharedFlow<String> = _errorMensaje

    init {
        voiceEngine.speak(
            "Editar mi cuenta. Aquí estás tú. Escribe o dicta lo que quieras cambiar y confirma con tu " +
                "contraseña actual.",
        )
    }

    fun onBack() {
        _back.tryEmit(Unit)
    }

    fun onCommandsRequested() {
        voiceEngine.speak(
            "Editar mi cuenta. Cambia tu nombre, tu apodo, si en Clasificación te muestro tu nombre o tu apodo, " +
                "y tu contraseña. Pide siempre tu contraseña actual para confirmar.",
            listenAfter = true,
        )
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onPedirInstitucion(institucion: String) {
        viewModelScope.launch {
            sessionRepository.pedirInstitucion(institucion)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo. Pedí unirme a $institucion. Mientras tanto sigo siendo usuario.")
        }
    }

    /** Un menor no se da de baja solo: queda pedida para que la autorice un adulto responsable. */
    fun onPedirBajaDeMenor() {
        viewModelScope.launch {
            sessionRepository.pedirBajaDeMenor()
            voiceEngine.speak("Listo. Pedí tu baja. Un adulto responsable la tiene que autorizar.")
        }
    }

    /** Un adulto se da de baja solo, confirmando con su contraseña. */
    fun onPedirCambioEducador() {
        viewModelScope.launch {
            sessionRepository.pedirCambioEducador()
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo. Pedí cambiar de educador. Alguien de tu institución lo revisará.")
        }
    }

    fun onDarseDeBaja(contrasenaActual: String) {
        val user = currentUser.value ?: return
        if (user.rol == RolUsuario.ADMINISTRADOR.codigo) {
            voiceEngine.speak("Un administrador no puede darse de baja.")
            return
        }
        if (PasswordHasher.hash(contrasenaActual.trim()) != user.contrasenaHash) {
            cues.play(FeedbackCues.Cue.ERROR)
            voiceEngine.speak("Esa no es tu contraseña. No di de baja tu cuenta.")
            _errorMensaje.tryEmit("Esa no es tu contraseña actual.")
            return
        }
        viewModelScope.launch {
            sessionRepository.deleteCurrentAccount()
            _back.tryEmit(Unit)
        }
    }

    /** [nuevaContrasena] vacía significa "no la cambies". */
    fun onGuardarPerfil(
        nombreCompleto: String,
        apodo: String,
        usarApodoRanking: Boolean,
        contrasenaActual: String,
        nuevaContrasena: String,
        correo: String,
        fechaNacimiento: Long?,
    ) {
        viewModelScope.launch {
            when (
                sessionRepository.actualizarPerfil(
                    nombreCompleto = nombreCompleto,
                    contrasenaActual = contrasenaActual,
                    nuevaContrasena = nuevaContrasena.ifBlank { null },
                    apodo = apodo,
                    usarApodoRanking = usarApodoRanking,
                    email = correo,
                    fechaNacimiento = fechaNacimiento,
                )
            ) {
                SessionRepository.EditarPerfilResult.Exito -> {
                    cues.play(FeedbackCues.Cue.SUCCESS)
                    voiceEngine.speak("Listo, guardé los cambios de tu cuenta.")
                    _guardado.tryEmit(Unit)
                }
                SessionRepository.EditarPerfilResult.ContrasenaActualIncorrecta -> {
                    cues.play(FeedbackCues.Cue.ERROR)
                    voiceEngine.speak("Esa no es tu contraseña actual. No guardé nada.")
                    _errorMensaje.tryEmit("Esa no es tu contraseña actual.")
                }
                SessionRepository.EditarPerfilResult.SinSesion -> {
                    voiceEngine.speak("No hay una sesión iniciada.")
                    _errorMensaje.tryEmit("No hay una sesión iniciada.")
                }
            }
        }
    }
}

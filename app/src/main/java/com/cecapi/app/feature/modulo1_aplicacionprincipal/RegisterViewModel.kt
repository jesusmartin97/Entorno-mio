package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class RegisterVoiceTarget { NOMBRE_COMPLETO, USUARIO, CONTRASENA, NONE }

/** Por ahora solo hay una institución real; el resto entra como independiente. */
enum class InstitucionRegistro(val origen: String, val etiqueta: String) {
    NINGUNA("", "No estoy afiliado con ninguna"),
    CECAPI("CECAPI", "CECAPI"),
}

data class RegisterUiState(
    val nombreCompleto: String = "",
    val nombreUsuario: String = "",
    val contrasena: String = "",
    val confirmarContrasena: String = "",
    val institucion: InstitucionRegistro = InstitucionRegistro.NINGUNA,
    /** Epoch millis; null hasta que la persona elige una fecha. */
    val fechaNacimiento: Long? = null,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _registerSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val registerSucceeded: SharedFlow<Unit> = _registerSucceeded

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private var voiceTarget = RegisterVoiceTarget.NOMBRE_COMPLETO

    init {
        // What is said here is a name or a password, so the global commands must not take it. Whole-phrase
        // controls (cancelar, repite, lista de comandos) are handled below.
        voiceEngine.rawInput = true
        voiceEngine.speak(
            "Vamos a crear tu cuenta. Di tu nombre completo, o escríbelo abajo. Di cancelar para volver. " +
                CommandCatalog.hint("crear cuenta"),
        )
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.REGISTER).collect { speech -> onVoiceInput(speech.text.trim()) }
        }
    }

    private fun onVoiceInput(texto: String) {
        val phrase = VoiceText.normalize(texto)
        if (phrase in CANCEL_PHRASES) {
            _back.tryEmit(Unit)
            return
        }
        if (CommandCatalog.isRequest(phrase)) {
            voiceEngine.speak(CommandCatalog.REGISTER, listenAfter = true)
            return
        }
        if (phrase in REPEAT_PHRASES) {
            repeatPrompt()
            return
        }
        when (voiceTarget) {
            RegisterVoiceTarget.NOMBRE_COMPLETO -> {
                onNombreCompletoChange(texto)
                voiceTarget = RegisterVoiceTarget.USUARIO
                voiceEngine.speak("Ahora di el nombre de usuario que quieres usar.")
            }
            RegisterVoiceTarget.USUARIO -> {
                onNombreUsuarioChange(texto.replace(" ", ""))
                voiceTarget = RegisterVoiceTarget.CONTRASENA
                voiceEngine.speak("Ahora di tu contraseña.")
            }
            RegisterVoiceTarget.CONTRASENA -> {
                onContrasenaChange(texto.replace(" ", ""))
                voiceTarget = RegisterVoiceTarget.NONE
                submit()
            }
            RegisterVoiceTarget.NONE -> voiceEngine.speak(
                "Ya tengo tus datos. Si algo está mal, corrígelo escribiendo en la pantalla.",
            )
        }
    }

    /** Two quick taps on the mic silence the assistant, for someone using touch with their hands instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    fun onMicPermissionDenied() {
        voiceEngine.speak(VoiceMessages.MIC_DENIED)
    }

    private fun repeatPrompt() {
        voiceEngine.speak(
            when (voiceTarget) {
                RegisterVoiceTarget.NOMBRE_COMPLETO -> "Di tu nombre completo."
                RegisterVoiceTarget.USUARIO -> "Di el nombre de usuario que quieres usar."
                RegisterVoiceTarget.CONTRASENA -> "Di tu contraseña."
                RegisterVoiceTarget.NONE -> "Ya tengo tus datos. Si algo está mal, corrígelo escribiendo en la pantalla."
            },
            listenAfter = voiceTarget != RegisterVoiceTarget.NONE,
        )
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }

    private companion object {
        // Whole phrases only: a person's name or password could contain these words.
        val CANCEL_PHRASES = setOf("cancelar", "cancela", "volver", "vuelve", "atras", "regresar", "regresa", "salir")
        val REPEAT_PHRASES = setOf("repite", "repetir", "otra vez", "de nuevo", "repite la pregunta", "ayuda")
    }

    fun onNombreCompletoChange(value: String) {
        _uiState.value = _uiState.value.copy(nombreCompleto = value, errorMessage = null)
    }

    fun onNombreUsuarioChange(value: String) {
        _uiState.value = _uiState.value.copy(nombreUsuario = value.uppercase(), errorMessage = null)
    }

    fun onContrasenaChange(value: String) {
        _uiState.value = _uiState.value.copy(contrasena = value, errorMessage = null)
    }

    fun onConfirmarContrasenaChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmarContrasena = value, errorMessage = null)
    }

    fun onInstitucionChange(value: InstitucionRegistro) {
        _uiState.value = _uiState.value.copy(institucion = value)
    }

    fun onFechaNacimientoChange(epochMillis: Long) {
        _uiState.value = _uiState.value.copy(fechaNacimiento = epochMillis, errorMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.nombreCompleto.isBlank() || state.nombreUsuario.isBlank() || state.contrasena.isBlank()) {
            val message = "Falta llenar tu nombre, usuario o contraseña."
            _uiState.value = state.copy(errorMessage = message)
            voiceEngine.speak(message)
            return
        }
        if (state.contrasena.length < 4) {
            val message = "La contraseña debe tener al menos cuatro caracteres. Dime otra contraseña."
            _uiState.value = state.copy(errorMessage = message)
            voiceTarget = RegisterVoiceTarget.CONTRASENA
            voiceEngine.speak(message, listenAfter = true)
            return
        }
        if (state.confirmarContrasena != state.contrasena) {
            val message = "Las contraseñas no coinciden. Escribe la confirmación otra vez."
            _uiState.value = state.copy(errorMessage = message, confirmarContrasena = "")
            voiceEngine.speak(message)
            return
        }
        if (state.fechaNacimiento == null) {
            val message = "Falta tu fecha de nacimiento."
            _uiState.value = state.copy(errorMessage = message)
            voiceEngine.speak(message)
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val outcome = try {
                sessionRepository.register(
                    nombreUsuario = state.nombreUsuario,
                    contrasena = state.contrasena,
                    nombreCompleto = state.nombreCompleto,
                    origen = state.institucion.origen,
                    fechaNacimiento = state.fechaNacimiento,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CecapiRegister", "register crashed", e)
                val message = "No se pudo crear la cuenta por un problema del teléfono. Intenta de nuevo."
                _uiState.value = state.copy(isSubmitting = false, errorMessage = message)
                voiceEngine.speak(message)
                return@launch
            }
            when (val result = outcome) {
                is RegisterResult.Success -> {
                    _uiState.value = state.copy(isSubmitting = false)
                    cues.play(FeedbackCues.Cue.SUCCESS)
                    // No speech here: the Dashboard opens next and gives the personalized welcome.
                    _registerSucceeded.tryEmit(Unit)
                }
                RegisterResult.UsernameTaken -> {
                    cues.play(FeedbackCues.Cue.ERROR)
                    val message = "Ese nombre de usuario ya existe. Di o escribe otro."
                    _uiState.value = state.copy(isSubmitting = false, errorMessage = message, nombreUsuario = "")
                    voiceEngine.speak(message)
                    voiceTarget = RegisterVoiceTarget.USUARIO
                }
            }
        }
    }
}

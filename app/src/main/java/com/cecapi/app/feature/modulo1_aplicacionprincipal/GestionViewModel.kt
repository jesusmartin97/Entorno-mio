package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.util.PasswordHasher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GestionUiState(
    val rolObservador: RolUsuario = RolUsuario.USUARIO,
    val personas: List<UsuarioEntity> = emptyList(),
    /** La fila que está desplegada mostrando sus acciones; null = ninguna. */
    val expandidoId: Long? = null,
    /** Solo administrador (todas) y directivo (las de su institución); vacío para los demás roles. */
    val incidencias: List<IncidenciaEntity> = emptyList(),
)

/**
 * Gestión: lo que ve y puede hacer un administrador, directivo o educador con las cuentas que les
 * corresponden, según [RolePermissions]. Un alumno o un usuario nunca llegan a esta pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GestionViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val usuarioDao: UsuarioDao,
    private val incidenciaDao: IncidenciaDao,
    private val cambioCuentaDao: CambioCuentaDao,
    private val cues: FeedbackCues,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _expandidoId = MutableStateFlow<Long?>(null)

    private val personasFlow = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario ->
            when (RolUsuario.fromCodigo(usuario.rol)) {
                RolUsuario.ADMINISTRADOR -> usuarioDao.observeTodos()
                RolUsuario.DIRECTIVO -> usuarioDao.observeUsuariosDeInstitucion(usuario.origen)
                RolUsuario.EDUCADOR -> combine(
                    usuarioDao.observeAlumnosDeEducador(usuario.id),
                    usuarioDao.observeSolicitudesDe(usuario.origen),
                ) { alumnos, solicitudes -> (alumnos + solicitudes).distinctBy { it.id } }
                // Nunca debería llegar aquí (la pantalla está oculta para estos roles), pero por si acaso.
                RolUsuario.ALUMNO, RolUsuario.USUARIO -> flowOf(emptyList())
            }
        }

    private val incidenciasFlow = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario ->
            when (RolUsuario.fromCodigo(usuario.rol)) {
                RolUsuario.ADMINISTRADOR -> incidenciaDao.observeTodas().map { lista -> lista.filter { it.destinatarioRol != "soporte" } }
                RolUsuario.DIRECTIVO -> incidenciaDao.observeParaRol(usuario.origen, RolUsuario.DIRECTIVO.codigo)
                RolUsuario.EDUCADOR -> incidenciaDao.observeParaRol(usuario.origen, RolUsuario.EDUCADOR.codigo)
                else -> flowOf(emptyList())
            }
        }

    val uiState: StateFlow<GestionUiState> = combine(
        sessionRepository.currentUser.filterNotNull(),
        personasFlow,
        _expandidoId,
        incidenciasFlow,
    ) { usuario, personas, expandidoId, incidencias ->
        GestionUiState(
            rolObservador = RolUsuario.fromCodigo(usuario.rol),
            personas = personas,
            expandidoId = expandidoId,
            incidencias = incidencias,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GestionUiState())

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.GESTION).collect { speech -> onSpeech(speech.text) }
        }
        // El detalle de cuántas personas se dice a petición ("cuántas personas"), no de entrada.
        voiceEngine.speak("Gestión. " + CommandCatalog.hint("gestión"), listenAfter = true)
    }

    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.GESTION, listenAfter = true)
            has("cuantas personas", "cuantos hay", "cuantos alumnos") -> speakCount()
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. " + CommandCatalog.hint("gestión"), listenAfter = true)
            }
        }
    }

    private fun speakCount() {
        val state = uiState.value
        val palabra = if (state.rolObservador == RolUsuario.EDUCADOR) "alumnos" else "personas"
        voiceEngine.speak("Tienes ${state.personas.size} $palabra.", listenAfter = true)
    }

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.GESTION, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    /** True solo para administrador y directivo: un educador ve su lista pero no cambia roles. */
    fun puedeEditar(): Boolean =
        uiState.value.rolObservador == RolUsuario.ADMINISTRADOR || uiState.value.rolObservador == RolUsuario.DIRECTIVO

    fun onPersonaTocada(persona: UsuarioEntity) {
        val propio = sessionRepository.currentUser.value?.id == persona.id
        // Un educador no cambia roles, pero igual necesita abrir la tarjeta para validar a su alumno.
        if (propio || (!puedeEditar() && !puedeValidar(persona))) return
        _expandidoId.value = if (_expandidoId.value == persona.id) null else persona.id
    }

    /** Roles que [observador] puede asignar: un directivo nunca otorga administrador. */
    fun rolesAsignables(): List<RolUsuario> = when (uiState.value.rolObservador) {
        RolUsuario.ADMINISTRADOR -> listOf(RolUsuario.ADMINISTRADOR, RolUsuario.DIRECTIVO, RolUsuario.EDUCADOR, RolUsuario.ALUMNO)
        RolUsuario.DIRECTIVO -> listOf(RolUsuario.DIRECTIVO, RolUsuario.EDUCADOR, RolUsuario.ALUMNO)
        else -> emptyList()
    }

    /** Educadores que se le pueden asignar a un alumno: de su misma institución. */
    fun educadoresPara(alumno: UsuarioEntity): List<UsuarioEntity> =
        uiState.value.personas.filter { it.rol == RolUsuario.EDUCADOR.codigo && it.origen.equals(alumno.origen, ignoreCase = true) }

    fun onRolElegido(persona: UsuarioEntity, nuevoRol: RolUsuario) {
        if (!puedeEditar()) return
        viewModelScope.launch {
            val quitaAdmin = persona.rol == RolUsuario.ADMINISTRADOR.codigo && nuevoRol != RolUsuario.ADMINISTRADOR
            if (quitaAdmin && usuarioDao.contarAdministradores() <= 1) {
                cues.play(FeedbackCues.Cue.ERROR)
                voiceEngine.speak("No puedo quitarle el rol de administrador: tiene que quedar al menos uno.")
                return@launch
            }
            usuarioDao.cambiarRol(persona.id, nuevoRol.codigo)
            // Un rol que ya no es alumno no debería seguir con un educador asignado.
            if (nuevoRol != RolUsuario.ALUMNO) usuarioDao.asignarEducador(persona.id, null)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("${persona.nombreCompleto} ahora es ${nuevoRol.codigo}.")
        }
        _expandidoId.value = null
    }

    /** Solo un administrador cambia la contraseña de otra persona. Queda registrado y la persona lo ve al abrir su cuenta. */
    fun puedeCambiarContrasenaDe(persona: UsuarioEntity): Boolean {
        val autor = sessionRepository.currentUser.value ?: return false
        return autor.rol == RolUsuario.ADMINISTRADOR.codigo && autor.id != persona.id
    }

    fun onCambiarContrasena(persona: UsuarioEntity, nueva: String) {
        val autor = sessionRepository.currentUser.value ?: return
        if (!puedeCambiarContrasenaDe(persona) || nueva.isBlank()) return
        viewModelScope.launch {
            usuarioDao.actualizarContrasena(persona.id, PasswordHasher.hash(nueva.trim()))
            cambioCuentaDao.insert(
                CambioCuentaEntity(objetivoId = persona.id, autorId = autor.id, tipo = "contrasena", detalle = "Cambiada por un administrador"),
            )
            usuarioDao.actualizarAviso(
                persona.id,
                "Un administrador cambió tu contraseña. Si no lo pediste, avisa a tu institución.",
            )
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo. Cambié la contraseña de ${persona.nombreCompleto}. Se lo avisaré al abrir su cuenta.")
        }
        _expandidoId.value = null
    }

    /** Una baja pedida por una cuenta de institución la confirma un administrador, un directivo de su institución o su educador. */
    fun puedeAutorizarBajaDe(persona: UsuarioEntity): Boolean {
        val autor = sessionRepository.currentUser.value ?: return false
        if (!persona.solicitaBaja || persona.rol == RolUsuario.ADMINISTRADOR.codigo) return false
        return when (RolUsuario.fromCodigo(autor.rol)) {
            RolUsuario.ADMINISTRADOR -> true
            RolUsuario.DIRECTIVO -> autor.origen.equals(persona.origen, ignoreCase = true)
            RolUsuario.EDUCADOR -> persona.educadorId == autor.id
            else -> false
        }
    }

    fun onAutorizarBajaDe(persona: UsuarioEntity) {
        val autor = sessionRepository.currentUser.value ?: return
        if (!puedeAutorizarBajaDe(persona)) return
        viewModelScope.launch {
            cambioCuentaDao.insert(
                CambioCuentaEntity(objetivoId = persona.id, autorId = autor.id, tipo = "baja", detalle = "Autorizada por ${autor.nombreCompleto}"),
            )
            sessionRepository.borrarCuentaDe(persona.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo. Di de baja la cuenta de ${persona.nombreCompleto}.")
        }
        _expandidoId.value = null
    }

    /** Quién puede tocar los datos de una cuenta: el administrador cualquiera; directivo su institución; educador sus alumnos. */
    private fun puedeTocarCuenta(persona: UsuarioEntity): Boolean {
        val autor = sessionRepository.currentUser.value ?: return false
        if (persona.id == autor.id || persona.rol == RolUsuario.ADMINISTRADOR.codigo && autor.rol != RolUsuario.ADMINISTRADOR.codigo) {
            return false
        }
        return when (RolUsuario.fromCodigo(autor.rol)) {
            RolUsuario.ADMINISTRADOR -> true
            RolUsuario.DIRECTIVO -> autor.origen.equals(persona.origen, ignoreCase = true) && autor.origen.isNotBlank()
            RolUsuario.EDUCADOR -> persona.educadorId == autor.id
            else -> false
        }
    }

    fun puedeEditarCuenta(persona: UsuarioEntity): Boolean = puedeTocarCuenta(persona)

    fun puedeDarDeBaja(persona: UsuarioEntity): Boolean =
        puedeTocarCuenta(persona) && (persona.origen.isNotBlank() || persona.institucionSolicitada != null)

    fun puedeEliminarCuenta(persona: UsuarioEntity): Boolean {
        val autor = sessionRepository.currentUser.value ?: return false
        return autor.rol == RolUsuario.ADMINISTRADOR.codigo && persona.id != autor.id &&
            persona.rol != RolUsuario.ADMINISTRADOR.codigo
    }

    fun onEditarCuenta(persona: UsuarioEntity, nombre: String, apodo: String, correo: String) {
        if (!puedeEditarCuenta(persona)) return
        val autor = sessionRepository.currentUser.value ?: return
        viewModelScope.launch {
            usuarioDao.actualizarNombre(persona.id, nombre.trim().ifBlank { persona.nombreCompleto })
            usuarioDao.actualizarApodo(persona.id, apodo.trim().ifBlank { null })
            usuarioDao.actualizarEmail(persona.id, correo.trim().ifBlank { null })
            cambioCuentaDao.insert(CambioCuentaEntity(objetivoId = persona.id, autorId = autor.id, tipo = "edicion", detalle = "Datos editados"))
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo, guardé los datos de ${persona.nombreCompleto}.")
        }
        _expandidoId.value = null
    }

    fun onDarDeBaja(persona: UsuarioEntity) {
        if (!puedeDarDeBaja(persona)) return
        val autor = sessionRepository.currentUser.value ?: return
        viewModelScope.launch {
            usuarioDao.quitarDeInstitucion(persona.id)
            cambioCuentaDao.insert(CambioCuentaEntity(objetivoId = persona.id, autorId = autor.id, tipo = "baja_institucion", detalle = "Dada de baja de la institución"))
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo. ${persona.nombreCompleto} ya no está en la institución.")
        }
        _expandidoId.value = null
    }

    fun onEliminarCuenta(persona: UsuarioEntity) {
        if (!puedeEliminarCuenta(persona)) return
        val autor = sessionRepository.currentUser.value ?: return
        viewModelScope.launch {
            cambioCuentaDao.insert(CambioCuentaEntity(objetivoId = persona.id, autorId = autor.id, tipo = "eliminada", detalle = "Cuenta eliminada por un administrador"))
            sessionRepository.borrarCuentaDe(persona.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo. Eliminé la cuenta de ${persona.nombreCompleto}.")
        }
        _expandidoId.value = null
    }

    fun onEducadorElegido(alumno: UsuarioEntity, educador: UsuarioEntity?) {
        if (!puedeEditar()) return
        viewModelScope.launch {
            usuarioDao.asignarEducador(alumno.id, educador?.id)
            usuarioDao.actualizarSolicitaCambioEducador(alumno.id, false)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak(
                if (educador != null) "${alumno.nombreCompleto} ahora está con ${educador.nombreCompleto}."
                else "Se quitó el educador de ${alumno.nombreCompleto}.",
            )
        }
        _expandidoId.value = null
    }

    /** Administrador valida directivo (o a cualquiera); directivo valida educador; educador valida alumno. */
    fun puedeValidar(persona: UsuarioEntity): Boolean {
        val observador = sessionRepository.currentUser.value ?: return false
        return RolePermissions.puedeValidar(observador, persona)
    }

    /**
     * Un educador que valida a un alumno queda como su educador. Un directivo valida, y después elige a qué educador
     * se asigna desde "Asignar educador".
     */
    fun onValidar(persona: UsuarioEntity) {
        if (!puedeValidar(persona)) return
        val autor = sessionRepository.currentUser.value ?: return
        viewModelScope.launch {
            usuarioDao.confirmarInstitucion(persona.id)
            if (autor.rol == RolUsuario.EDUCADOR.codigo && persona.rol == RolUsuario.ALUMNO.codigo) {
                usuarioDao.asignarEducador(persona.id, autor.id)
            }
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Validé a ${persona.nombreCompleto}.")
        }
    }

    /** La institución (o un administrador) no valida la solicitud: la persona sigue como usuario y queda registrado. */
    fun onRechazarSolicitud(persona: UsuarioEntity) {
        val autor = sessionRepository.currentUser.value ?: return
        if (!puedeValidar(persona)) return
        viewModelScope.launch {
            usuarioDao.rechazarSolicitud(persona.id)
            cambioCuentaDao.insert(
                CambioCuentaEntity(objetivoId = persona.id, autorId = autor.id, tipo = "solicitud_rechazada", detalle = "Solicitud rechazada"),
            )
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Rechacé la solicitud de ${persona.nombreCompleto}. Sigue como usuario.")
        }
        _expandidoId.value = null
    }

    /** Cuentas nuevas que el administrador todavía no ha revisado. */
    val cuentasPorRevisar: StateFlow<List<UsuarioEntity>> = usuarioDao.observeNuevas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onConfirmarCuenta(persona: UsuarioEntity) {
        viewModelScope.launch {
            usuarioDao.actualizarRevision(persona.id, "confirmada")
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Confirmé la cuenta de ${persona.nombreCompleto}.")
        }
    }

    /** "No la conozco": la cuenta se da de baja y queda registrado. */
    fun onNoConozcoCuenta(persona: UsuarioEntity) {
        val autor = sessionRepository.currentUser.value ?: return
        if (autor.rol != RolUsuario.ADMINISTRADOR.codigo) return
        viewModelScope.launch {
            cambioCuentaDao.insert(
                CambioCuentaEntity(objetivoId = persona.id, autorId = autor.id, tipo = "baja", detalle = "No reconocida por un administrador"),
            )
            sessionRepository.borrarCuentaDe(persona.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Di de baja la cuenta de ${persona.nombreCompleto}.")
        }
    }

    /** Alumnos ya validados que todavía no tienen educador: los asigna un directivo o un administrador. */
    fun alumnosSinEducador(): List<UsuarioEntity> =
        uiState.value.personas.filter { it.validado && it.rol == RolUsuario.ALUMNO.codigo && it.educadorId == null }

    fun onResolverIncidencia(incidencia: IncidenciaEntity) {
        viewModelScope.launch {
            incidenciaDao.marcarResuelta(incidencia.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
        }
    }
}

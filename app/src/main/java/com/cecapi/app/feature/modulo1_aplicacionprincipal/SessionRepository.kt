package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cecapi.app.core.data.AccountEraser
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.util.PasswordHasher
import com.cecapi.app.core.voice.VoiceEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginResult {
    data class Success(val usuario: UsuarioEntity) : LoginResult
    data object InvalidCredentials : LoginResult
}

sealed interface RegisterResult {
    data class Success(val usuario: UsuarioEntity) : RegisterResult
    data object UsernameTaken : RegisterResult
}

private val Context.sessionStore by preferencesDataStore(name = "remembered_session")

@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usuarioDao: UsuarioDao,
    private val permisosModuloDao: PermisosModuloDao,
    private val configuracionUsuarioDao: ConfiguracionUsuarioDao,
    private val accountEraser: AccountEraser,
    private val voiceEngine: VoiceEngine,
) {
    private val _currentUser = MutableStateFlow<UsuarioEntity?>(null)
    val currentUser: StateFlow<UsuarioEntity?> = _currentUser.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Opening the app from the same device it was used on before signs the person straight in,
        // without asking for the password again — the phone's own lock screen is the security boundary,
        // the same trade-off every app that "stays signed in" makes. Cleared the moment they log out.
        scope.launch { restoreRememberedSession() }
    }

    private suspend fun restoreRememberedSession() {
        val rememberedId = context.sessionStore.data.first()[KEY_REMEMBERED_USER_ID] ?: return
        val usuario = usuarioDao.findById(rememberedId) ?: return
        _currentUser.value = usuario
    }

    suspend fun login(nombreUsuario: String, contrasena: String): LoginResult {
        val usuario = usuarioDao.findByUsername(nombreUsuario.trim())
            ?: return LoginResult.InvalidCredentials
        val hashed = PasswordHasher.hash(contrasena.trim())
        if (usuario.contrasenaHash != hashed) {
            return LoginResult.InvalidCredentials
        }
        completeLogin(usuario)
        return LoginResult.Success(usuario)
    }

    /**
     * Creates a new CECAPI account. Anyone can self-register and sign in right away — "por default se
     * les dejará ingresar" — but [UsuarioEntity.validado] defaults to false, so it shows up as pending in
     * whoever is above them in Gestión (administrador > directivo > educador > alumno, or
     * administrador > usuario) to confirm later. Nothing here blocks on that.
     */
    suspend fun register(
        nombreUsuario: String,
        contrasena: String,
        nombreCompleto: String,
        origen: String = "",
        fechaNacimiento: Long? = null,
    ): RegisterResult {
        val nombreNormalizado = nombreUsuario.trim().uppercase()
        // The table has no unique index on the username, so the duplicate check has to be explicit.
        if (usuarioDao.findByUsername(nombreNormalizado) != null) return RegisterResult.UsernameTaken
        val institucion = origen.trim()
        // Todos empiezan como usuarios. Si eligieron una institución, queda la solicitud pendiente; la institución
        // la valida después, y solo entonces pasa a ser su institución.
        val usuario = UsuarioEntity(
            nombreUsuario = nombreNormalizado,
            contrasenaHash = PasswordHasher.hash(contrasena.trim()),
            nombreCompleto = nombreCompleto.trim(),
            fechaNacimiento = fechaNacimiento,
            institucionSolicitada = institucion.ifBlank { null },
            solicitaValidacion = institucion.isNotBlank(),
            revisionCuenta = "nueva",
        )
        val nuevoId = usuarioDao.insert(usuario)
        if (nuevoId <= 0) return RegisterResult.UsernameTaken

        val usuarioCreado = usuario.copy(id = nuevoId)
        completeLogin(usuarioCreado)
        return RegisterResult.Success(usuarioCreado)
    }

    private var logoutNoticePending = false

    fun logout() {
        _currentUser.value = null
        logoutNoticePending = true
        scope.launch { context.sessionStore.edit { it.remove(KEY_REMEMBERED_USER_ID) } }
    }

    /** Deletes the signed-in person's account and all their data, then signs out. False when nobody is signed in. */
    suspend fun deleteCurrentAccount(): Boolean {
        val user = _currentUser.value ?: return false
        accountEraser.erase(user.id)
        logout()
        return true
    }

    sealed interface EditarPerfilResult {
        data object Exito : EditarPerfilResult
        data object ContrasenaActualIncorrecta : EditarPerfilResult
        data object SinSesion : EditarPerfilResult
    }

    /**
     * Each person edits their own name and, optionally, their password — never someone else's (Gestión
     * only ever changes a role, not these). Confirms with the CURRENT password first, same as deleting
     * the account, so a stray "cambia mi nombre" from someone else in the room cannot silently do it.
     */
    suspend fun actualizarPerfil(
        nombreCompleto: String,
        contrasenaActual: String,
        nuevaContrasena: String?,
        apodo: String? = null,
        usarApodoRanking: Boolean = false,
        email: String? = null,
        fechaNacimiento: Long? = null,
    ): EditarPerfilResult {
        val user = _currentUser.value ?: return EditarPerfilResult.SinSesion
        if (PasswordHasher.hash(contrasenaActual.trim()) != user.contrasenaHash) {
            return EditarPerfilResult.ContrasenaActualIncorrecta
        }
        val nombreLimpio = nombreCompleto.trim().ifBlank { user.nombreCompleto }
        usuarioDao.actualizarNombre(user.id, nombreLimpio)
        var nuevoHash = user.contrasenaHash
        if (!nuevaContrasena.isNullOrBlank()) {
            nuevoHash = PasswordHasher.hash(nuevaContrasena.trim())
            usuarioDao.actualizarContrasena(user.id, nuevoHash)
        }
        val apodoLimpio = apodo?.trim()?.ifBlank { null }
        usuarioDao.actualizarApodo(user.id, apodoLimpio)
        usuarioDao.actualizarUsarApodoRanking(user.id, usarApodoRanking)
        val correoLimpio = email?.trim()?.ifBlank { null }
        usuarioDao.actualizarEmail(user.id, correoLimpio)
        usuarioDao.actualizarFechaNacimiento(user.id, fechaNacimiento)
        _currentUser.value = user.copy(
            nombreCompleto = nombreLimpio,
            contrasenaHash = nuevoHash,
            apodo = apodoLimpio,
            usarApodoRanking = usarApodoRanking,
            email = correoLimpio,
            fechaNacimiento = fechaNacimiento,
        )
        return EditarPerfilResult.Exito
    }

    /** Pide unirse a una institución; mientras no la valide, la persona sigue como usuario. */
    suspend fun pedirInstitucion(institucion: String) {
        val user = _currentUser.value ?: return
        usuarioDao.solicitarInstitucion(user.id, institucion)
        _currentUser.value = user.copy(institucionSolicitada = institucion, solicitaValidacion = true)
    }

    /** Un menor no se da de baja solo: queda pedida para que la autorice un adulto responsable. */
    suspend fun pedirBajaDeMenor() {
        val user = _currentUser.value ?: return
        usuarioDao.actualizarSolicitaBaja(user.id, true)
        _currentUser.value = user.copy(solicitaBaja = true)
    }

    /** Borra la cuenta de otra persona con la autorización de quien puede hacerlo (Gestión). */
    suspend fun borrarCuentaDe(usuarioId: Long) {
        accountEraser.erase(usuarioId)
    }

    /** Un alumno pide cambiar de educador; lo atiende un directivo, un educador o un administrador. */
    suspend fun pedirCambioEducador() {
        val user = _currentUser.value ?: return
        usuarioDao.actualizarSolicitaCambioEducador(user.id, true)
        _currentUser.value = user.copy(solicitaCambioEducador = true)
    }

    /** Lee y borra el aviso pendiente de la cuenta, para decirlo una sola vez al abrir la app. */
    suspend fun consumirAvisoPendiente(): String? {
        val user = _currentUser.value ?: return null
        val aviso = user.avisoPendiente ?: return null
        usuarioDao.actualizarAviso(user.id, null)
        _currentUser.value = user.copy(avisoPendiente = null)
        return aviso
    }

    /** True once after a logout, so the home screen can say "Sesión cerrada" as it opens. */
    fun consumeLogoutNotice(): Boolean = logoutNoticePending.also { logoutNoticePending = false }

    /** Shared by login and register: sets up defaults for a brand-new account and is a
     * harmless no-op for a returning one, then applies saved voice settings and opens the session. */
    private suspend fun completeLogin(usuario: UsuarioEntity) {
        ensureDefaultPermisos(usuario.id)
        // Voice speed and cues are per device now (DeviceSettings); the per-user row is kept only so
        // older accounts and the database schema stay intact.
        ensureDefaultConfiguracion(usuario.id)
        _currentUser.value = usuario
        context.sessionStore.edit { it[KEY_REMEMBERED_USER_ID] = usuario.id }
    }

    /** First login for a user: grant access to all six functional modules by default. */
    private suspend fun ensureDefaultPermisos(usuarioId: Long) {
        if (permisosModuloDao.countByUser(usuarioId) > 0) return
        val defaults = ModuloCecapi.entries.map { modulo ->
            PermisosModuloEntity(usuarioId = usuarioId, moduloCodigo = modulo.storageCode, habilitado = true)
        }
        permisosModuloDao.insertAll(defaults)
    }

    private suspend fun ensureDefaultConfiguracion(usuarioId: Long): ConfiguracionUsuarioEntity {
        val existente = configuracionUsuarioDao.findByUser(usuarioId)
        if (existente != null) return existente
        val nueva = ConfiguracionUsuarioEntity(usuarioId = usuarioId)
        configuracionUsuarioDao.upsert(nueva)
        return nueva
    }

    private companion object {
        val KEY_REMEMBERED_USER_ID = longPreferencesKey("remembered_user_id")
    }
}

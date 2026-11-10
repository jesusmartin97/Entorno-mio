package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.PasswordHasher
import com.cecapi.app.core.util.StorageReport
import com.cecapi.app.core.util.StorageUsage
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.notifications.NotificationAccess
import com.cecapi.app.service.BackgroundListening
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

/**
 * Configuración: the technical side (volume, notification access, listening outside the app, account).
 * How the assistant sounds and what it calls the person lives in Personalización.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val deviceSettings: DeviceSettings,
    private val volumeControl: VolumeControl,
    private val cues: FeedbackCues,
    private val notificationAccess: NotificationAccess,
    private val backgroundListening: BackgroundListening,
    private val sessionRepository: SessionRepository,
    private val storageReport: StorageReport,
) : ViewModel() {

    val simpleMode: StateFlow<Boolean> = deviceSettings.simpleMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val announceNotifications: StateFlow<Boolean> = deviceSettings.announceNotifications.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val listenOutsideApp: StateFlow<Boolean> = deviceSettings.backgroundListening.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val aiEnabled: StateFlow<Boolean> = deviceSettings.aiEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val holaEnabled: StateFlow<Boolean> = deviceSettings.holaEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The signed-in user, or null when Configuración was opened from the home screen. */
    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    // Turning listening outside the app on needs a permission the screen asks for, so the screen does the switching.
    private val _listenOutsideRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val listenOutsideRequests: SharedFlow<Boolean> = _listenOutsideRequests

    private val _storage = MutableStateFlow<StorageUsage?>(null)
    val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    /** True while the person has been asked whether to delete the old photos and has not answered. */
    private val _pendingClean = MutableStateFlow(false)

    /** True while the person has been asked to say their password to confirm deleting their account. */
    private val _awaitingDeletePassword = MutableStateFlow(false)
    val awaitingDeletePassword: StateFlow<Boolean> = _awaitingDeletePassword.asStateFlow()

    /** True while, password already confirmed, the person has been asked the final "¿borro...?", for a short time only. */
    private val _pendingDeleteAccount = MutableStateFlow(false)
    val pendingDeleteAccount: StateFlow<Boolean> = _pendingDeleteAccount.asStateFlow()
    private var deleteAskedAt = 0L
    val pendingClean: StateFlow<Boolean> = _pendingClean.asStateFlow()


    init {
        refreshStorage()
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.SETTINGS).collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Configuración. Aquí están el volumen, las notificaciones, escuchar fuera de la aplicación y tu cuenta. " +
                "Mantén presionado cualquier control para que te explique para qué sirve. " +
                CommandCatalog.hint("configuración"),
            listenAfter = true,
        )
    }

    /**
     * The commands this screen answers. Volume and anything that names notifications are global commands
     * (they never reach here), so the notification switch is worded as "avisos".
     */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val enable = when {
            listOf("desactiv*", "apaga", "quita", "sin", "no quiero").let { phrases -> VoiceText.hasAny(text, phrases) } -> false
            listOf("activ*", "enciende", "prende", "pon", "quiero").let { phrases -> VoiceText.hasAny(text, phrases) } -> true
            else -> null
        }
        // "si" and "no" must be whole words: "siguiente" contains "si".
        val spokenWords = text.split(" ")
        fun hasWord(vararg words: String) = words.any { it in spokenWords }
        val cleaning = _pendingClean.value
        val expired = System.currentTimeMillis() - deleteAskedAt >= CONFIRM_WINDOW_MS
        val awaitingPassword = _awaitingDeletePassword.value && !expired
        val deletingAccount = _pendingDeleteAccount.value && !expired
        // The password step opens rawInput so a spoken password is never swallowed by another command; if the
        // person went quiet instead of answering, put the screen back to normal instead of leaving it stuck.
        if (_awaitingDeletePassword.value && expired) {
            _awaitingDeletePassword.value = false
            voiceEngine.rawInput = false
        }
        when {
            // Antes de tratar lo dicho como el intento de contraseña: si la persona quiere salir de ahí
            // ("regresar", "salir"...), eso debe cancelar el borrado, no fallar como si fuera una contraseña mala.
            awaitingPassword && has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "cancelar", "cancela", "menu", "inicio") ->
                cancelDeleteAccount()
            // Borrar la cuenta pide, además del "sí", la contraseña: mientras se espera, cualquier otra frase es el
            // intento, no un comando (igual que el usuario y la contraseña al iniciar sesión).
            awaitingPassword -> checkDeletePassword(spoken)
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.SETTINGS, listenAfter = true)
            deletingAccount && !VoiceText.isNo(spoken) && spokenWords.any { it in DELETE_CONFIRM_WORDS } -> confirmDeleteAccount()
            deletingAccount && VoiceText.isNo(spoken) -> cancelDeleteAccount()
            has("borra mi cuenta", "borrar mi cuenta", "elimina mi cuenta", "eliminar mi cuenta", "borra mis datos", "borrar mis datos", "elimina mis datos", "eliminar mis datos", "darme de baja") -> askDeleteAccount()
            has("inteligencia artificial", "la ia") && enable != null -> onAiEnabledChanged(enable)
            cleaning && hasWord("si", "claro", "dale", "ok", "okey", "supuesto", "afirmativo", "correcto", "seguro", "hazlo", "confirmo", "borralas", "adelante") -> confirmClean()
            cleaning && hasWord("no", "nunca", "negativo", "olvidalo", "dejalo", "cancela", "cancelar") -> cancelClean()
            has("cache", "memoria temporal", "archivos temporales", "temporales") &&
                has("limpia", "limpiar", "borra", "borrar", "vacia", "vaciar", "libera", "liberar", "elimina") -> clearCache()
            has("cuanto espacio", "espacio libre", "almacenamiento", "cuanto ocupa") -> speakStorage()
            has("libera espacio", "liberar espacio", "libera memoria", "limpia el espacio", "limpiar espacio", "borra las fotos", "borrar las fotos") ->
                askClean()
            has("cerrar sesion", "cierra sesion", "cierra mi sesion", "salir de la cuenta") ->
                if (currentUser.value != null) onLogout() else voiceEngine.speak("No hay una sesión iniciada.", listenAfter = true)
            has("fuera de la app", "fuera de la aplicacion", "segundo plano") && enable != null ->
                _listenOutsideRequests.tryEmit(enable)
            has("modo simple") && enable != null -> onSimpleModeChanged(enable)
            has("aviso") && enable != null -> onAnnounceNotificationsChanged(enable)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. " + CommandCatalog.hint("configuración"), listenAfter = true)
            }
        }
    }

    /** Call from the header button: reads out this screen's commands. */
    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.SETTINGS, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun refreshStorage() {
        viewModelScope.launch { _storage.value = storageReport.read() }
    }

    fun speakStorage() {
        viewModelScope.launch {
            val usage = storageReport.read()
            _storage.value = usage
            voiceEngine.speak(storageReport.describe(usage), listenAfter = true)
        }
    }

    /** Offers to delete the photos older than a month. Nothing is deleted until the person says yes. */
    fun askClean() {
        viewModelScope.launch {
            val usage = storageReport.read()
            _storage.value = usage
            if (usage.oldPhotoCount == 0) {
                voiceEngine.speak(
                    "No hay fotos de más de ${StorageReport.OLD_PHOTO_DAYS} días que borrar.",
                    listenAfter = true,
                )
            } else {
                _pendingClean.value = true
                voiceEngine.speak(
                    "Puedo borrar ${usage.oldPhotoCount} fotos de más de ${StorageReport.OLD_PHOTO_DAYS} días y liberar " +
                        "${storageReport.format(usage.oldPhotoBytes)}. Solo se borran las fotos; el texto que leí sigue guardado. " +
                        "¿Las borro? Di sí o no.",
                    listenAfter = true,
                )
            }
        }
    }

    fun confirmClean() {
        _pendingClean.value = false
        viewModelScope.launch {
            val (count, bytes) = storageReport.deleteOldPhotos()
            _storage.value = storageReport.read()
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo, borré $count fotos y liberé ${storageReport.format(bytes)}.", listenAfter = true)
        }
    }

    /** The temporary files hold nothing the person made, so no question is asked; the answer says how much came back. */
    fun clearCache() {
        viewModelScope.launch {
            val freed = storageReport.clearCache()
            _storage.value = storageReport.read()
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak(
                if (freed > 0) "Listo, vacié la memoria temporal y liberé ${storageReport.format(freed)}."
                else "La memoria temporal ya estaba vacía.",
                listenAfter = true,
            )
        }
    }

    fun cancelClean() {
        _pendingClean.value = false
        voiceEngine.speak("De acuerdo, no borré nada.", listenAfter = true)
    }

    fun describeStorage(usage: StorageUsage): String = storageReport.describe(usage)

    // ---- Privacy: delete my account, and the AI switch ------------------------------------------------------

    /** Asks first, in plain words about what is lost: this cannot be undone. Needs a signed-in person. */
    /**
     * Deleting an account cannot be undone, so it asks for one more thing than a plain "sí": the person's own
     * password. This also stops a stray "sí, borrar" from someone else in the room from deleting an account.
     */
    fun askDeleteAccount() {
        if (currentUser.value == null) {
            voiceEngine.speak("No hay una sesión iniciada, así que no hay una cuenta que borrar. Ve a iniciar sesión primero.", listenAfter = true)
            return
        }
        _awaitingDeletePassword.value = true
        deleteAskedAt = System.currentTimeMillis()
        voiceEngine.rawInput = true // a spoken password must reach here untouched, like at login
        voiceEngine.speak("Para borrar tu cuenta, primero dime tu contraseña.", listenAfter = true)
    }

    /** For the on-screen password field, when the person types instead of speaking it. */
    fun onDeletePasswordEntered(password: String) {
        if (password.isBlank()) return
        checkDeletePassword(password)
    }

    private fun checkDeletePassword(spoken: String) {
        val user = currentUser.value
        _awaitingDeletePassword.value = false
        if (user == null || PasswordHasher.hash(spoken.trim()) != user.contrasenaHash) {
            voiceEngine.rawInput = false
            cues.play(FeedbackCues.Cue.ERROR)
            voiceEngine.speak("Esa no es tu contraseña. No borré nada.", listenAfter = true)
            return
        }
        _pendingDeleteAccount.value = true
        deleteAskedAt = System.currentTimeMillis()
        voiceEngine.speak(
            "Contraseña correcta. ¿Borro tu cuenta y todo lo que guardaste: chats, resultados, documentos y fotos? " +
                "No se puede deshacer. Di sí, borrar, para confirmar, o no.",
            listenAfter = true,
        )
    }

    fun confirmDeleteAccount() {
        _pendingDeleteAccount.value = false
        voiceEngine.rawInput = false
        viewModelScope.launch {
            if (sessionRepository.deleteCurrentAccount()) {
                cues.play(FeedbackCues.Cue.SUCCESS)
                voiceEngine.speak(
                    "Listo. Borré los datos de tu cuenta: tus chats, resultados, documentos y fotos. " +
                        "Algunas preferencias de este teléfono, como el nombre con el que te saludo y cómo hablo, se quedan, " +
                        "porque son de este teléfono y no de tu cuenta.",
                )
                _loggedOut.tryEmit(Unit)
            } else {
                voiceEngine.speak("No pude borrar la cuenta porque no hay una sesión iniciada.")
            }
        }
    }

    fun cancelDeleteAccount() {
        _pendingDeleteAccount.value = false
        _awaitingDeletePassword.value = false
        voiceEngine.rawInput = false
        voiceEngine.speak("De acuerdo, no borré nada.", listenAfter = true)
    }

    /** The AI is optional and off by default: what the assistant does not understand is never sent anywhere unless this is on. */
    fun onHolaChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setHolaEnabled(enabled) }
        voiceEngine.speak(
            if (enabled) {
                "Listo. Ahora también respondo cuando digas hola."
            } else {
                "Listo. Ya no respondo a hola. Di mi nombre para hablar conmigo."
            },
        )
    }

    fun onAiEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setAiEnabled(enabled) }
        voiceEngine.speak(
            if (enabled) {
                "Inteligencia artificial activada. Cuando no entienda una frase y haya internet, podré enviarla a un servidor para interpretarla. " +
                    "Por ahora todavía no está conectada. No la actives si quien usa la aplicación es menor de edad."
            } else {
                "Inteligencia artificial desactivada. Ninguna de tus preguntas se envía a un servidor para interpretarla. " +
                    "El reconocimiento de voz del teléfono sigue funcionando como siempre."
            },
        )
    }

    private companion object {
        /** A confirmation to delete an account is only good for a short while. */
        const val CONFIRM_WINDOW_MS = 30_000L

        /** Whole words that confirm the deletion; a bare "ok" or "dale" is not enough for something this final. */
        val DELETE_CONFIRM_WORDS = setOf("si", "confirmo", "borrar", "borrala", "borralo", "elimina", "eliminala")
    }

    fun currentVolumePercent(): Int = volumeControl.percent()

    fun onVolumeChanged(percent: Int) {
        volumeControl.setPercent(percent)
        voiceEngine.speak(volumeControl.levelText())
    }

    fun isNotificationAccessEnabled(): Boolean = notificationAccess.isEnabled()

    fun onOpenNotificationSettings() {
        voiceEngine.speak("Voy a abrir los ajustes. Busca CECAPI en la lista y actívalo. Después regresa a la aplicación.")
        notificationAccess.openSettings()
    }

    fun onAnnounceNotificationsChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setAnnounceNotifications(enabled) }
        voiceEngine.speak(
            if (enabled) "Te avisaré cuando llegue una notificación." else "Ya no te avisaré cuando llegue una notificación.",
        )
    }

    /** Starts or stops the service that keeps listening for "hola" after the app is closed. */
    fun onListenOutsideAppChanged(enabled: Boolean) {
        viewModelScope.launch {
            deviceSettings.setBackgroundListening(enabled)
            if (enabled) backgroundListening.start() else backgroundListening.stop()
        }
        voiceEngine.speak(
            if (enabled) {
                "Listo. Seguiré escuchando aunque cierres la aplicación. Verás un aviso fijo. Di para, para detenerme."
            } else {
                "Listo. Ya no escucharé cuando la aplicación esté cerrada."
            },
        )
    }

    fun onSimpleModeChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setSimpleMode(enabled) }
        voiceEngine.speak(if (enabled) "Modo simple activado." else "Modo simple desactivado.")
    }

    fun onLogout() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        sessionRepository.logout()
        _loggedOut.tryEmit(Unit)
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }
}

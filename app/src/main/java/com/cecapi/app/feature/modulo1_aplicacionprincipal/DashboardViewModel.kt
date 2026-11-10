package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.ui.MenuItem
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.util.PhoneStatusReader
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.util.WikidataFact
import com.cecapi.app.core.util.WikidataLookup
import com.cecapi.app.core.util.WikipediaLookup
import com.cecapi.app.core.voice.AssistantPreferences
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.IntentFallback
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.notifications.NotificationReader
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMemory
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.core.voice.WakeWordController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val permisosModuloDao: PermisosModuloDao,
    private val phoneStatusReader: PhoneStatusReader,
    private val wakeWordController: WakeWordController,
    private val voiceMemory: VoiceMemory,
    private val volumeControl: VolumeControl,
    private val cues: FeedbackCues,
    private val notificationReader: NotificationReader,
    private val intentFallback: IntentFallback,
    private val deviceSettings: DeviceSettings,
    private val connectivityObserver: ConnectivityObserver,
    private val wikipediaLookup: WikipediaLookup,
    private val wikidataLookup: WikidataLookup,
    assistantPreferences: AssistantPreferences,
) : ViewModel() {

    private var holdingWakeWord = false

    init {
        viewModelScope.launch {
            sessionRepository.consumirAvisoPendiente()?.let { voiceEngine.speak(it) }
        }
    }

    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val assistantName: StateFlow<String> = assistantPreferences.assistantName.stateIn(
        viewModelScope, SharingStarted.Eagerly, "",
    )

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    private val _exitEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitEvents: SharedFlow<Unit> = _exitEvents

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val enabledModules: StateFlow<List<ModuloCecapi>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> permisosModuloDao.observeByUser(usuario.id) }
        .map { permisos ->
            permisos.filter { it.habilitado }
                .mapNotNull { ModuloCecapi.fromStorageCode(it.moduloCodigo) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuloCecapi.entries.toList())

    /** The main menu for this account: Cámara, Documentos, Personalización and Configuración. */
    val menu: StateFlow<List<MenuItem>> = enabledModules
        .map { ModuleVoice.menu(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuleVoice.menu(ModuloCecapi.entries))

    private val _navEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<String> = _navEvents

    // This ViewModel outlives its screen while a module is open on top of it, so it must
    // ignore speech that belongs to that module.
    private var screenActive = false

    init {
        viewModelScope.launch {
            // Connection status is only mentioned when there is a problem.
            val online = connectivityObserver.observe().first()
            voiceEngine.speak(welcomeMessage() + if (online) "" else " $OFFLINE_NOTICE")
        }
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            connectivityObserver.observe().collect { online ->
                if (wasOnline == true && !online && screenActive) { cues.play(FeedbackCues.Cue.WARNING); voiceEngine.speak(OFFLINE_NOTICE) }
                wasOnline = online
            }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.DASHBOARD).collect { speech ->
                if (screenActive) interpretCommand(speech.text.lowercase())
            }
        }
    }

    private enum class PendingConfirm { EXIT, LOGOUT }

    private var pendingConfirm: PendingConfirm? = null
    private var pendingConfirmAskedAt = 0L

    /**
     * Answers the "¿Cierro...? Di sí o no." question. Only for a short while, and only with a deliberate word:
     * this can happen to anyone, including a child, so a word that also means something else in passing
     * conversation ("va", "sale") must not count.
     */
    private fun handlePendingConfirm(text: String): Boolean {
        val action = pendingConfirm ?: return false
        pendingConfirm = null
        if (System.currentTimeMillis() - pendingConfirmAskedAt > EXIT_CONFIRM_WINDOW_MS) return false
        return when {
            VoiceText.isNo(text) -> {
                voiceEngine.speak("De acuerdo, no cierro nada.")
                true
            }
            VoiceText.normalize(text).split(" ").any { it in EXIT_CONFIRM_WORDS } -> {
                if (action == PendingConfirm.EXIT) exitApp() else onLogout()
                true
            }
            else -> false
        }
    }

    private fun interpretCommand(text: String) {
        if (handlePendingConfirm(text)) return
        when (voiceMemory.repeatKind(text)) {
            VoiceMemory.Repeat.RESPONSE ->
                voiceEngine.speak(voiceEngine.lastSpoken ?: "Todavía no he dicho nada.")
            VoiceMemory.Repeat.REQUEST -> {
                val previous = voiceMemory.lastRequest
                if (previous == null) voiceEngine.speak("Todavía no me has pedido nada.") else runCommand(previous)
            }
            null -> {
                voiceMemory.remember(text)
                runCommand(text)
            }
        }
    }

    private fun runCommand(text: String, allowFallback: Boolean = true) {
        // "dime más": go deeper on the last thing the AI answered, or read the rest of a Wikipedia summary.
        intentFallback.deepQuestionFor(text)?.let { question ->
            askAi(question, deep = true)
            return
        }
        pendingWikiRest?.let { rest ->
            if (intentFallback.wantsMore(text)) {
                pendingWikiRest = null
                voiceEngine.speak(rest, listenAfter = true)
                return
            }
        }
        phoneStatusReader.answer(text)?.let { status ->
            voiceEngine.speak(status)
            return
        }
        volumeControl.handle(text)?.let { message ->
            voiceEngine.speak(message)
            return
        }
        if (CommandCatalog.isRequest(text)) {
            voiceEngine.speak(CommandCatalog.DASHBOARD)
            return
        }
        notificationReader.handle(text)?.let { message ->
            voiceEngine.speak(message)
            return
        }
        // Leaving is hard to undo for someone who cannot see the screen, and a misheard word must not do it.
        if (SessionCommands.isExitApp(text)) {
            pendingConfirm = PendingConfirm.EXIT
            pendingConfirmAskedAt = System.currentTimeMillis()
            voiceEngine.speak("¿Cierro la aplicación? Di sí, cerrar, o no.", listenAfter = true)
            return
        }
        if (SessionCommands.isLogout(text)) {
            pendingConfirm = PendingConfirm.LOGOUT
            pendingConfirmAskedAt = System.currentTimeMillis()
            voiceEngine.speak("¿Cierro tu sesión? Di sí, cerrar, o no.", listenAfter = true)
            return
        }
        if (puedeGestionar() && VoiceText.hasAny(VoiceText.normalize(text), "gestion", "administracion", "panel")) {
            onGestionSelected()
            return
        }
        if (esAlumnoOUsuario() &&
            VoiceText.hasAny(VoiceText.normalize(text), "ayuda y soporte", "soporte", "mi educador", "reportar un problema")
        ) {
            onSoporteSelected()
            return
        }
        if (VoiceText.hasAny(VoiceText.normalize(text), "clasificacion", "tabla de clasificacion", "ranking")) {
            onRankingSelected()
            return
        }
        if (ModuleVoice.isListRequest(text)) {
            voiceEngine.speak(ModuleVoice.spokenList(menu.value))
            return
        }
        ModuleVoice.directCameraRoute(text)?.let { route ->
            openRoute(
                route,
                if (route == CecapiDestinations.ENVIRONMENT) "Abriendo el asistente del entorno." else "Abriendo la cámara para leer texto.",
            )
            return
        }
        val key = ModuleVoice.matchKey(text, menu.value)
        if (key != null) {
            onMenuKey(key)
        } else {
            notUnderstood(text, allowFallback)
        }
    }

    /**
     * Not one of our commands. If a resolver is registered (the AI team's "traductor de frases") and there is
     * internet, it gets one chance to turn the phrase into a command we know; otherwise we say so.
     */
    private fun notUnderstood(text: String, allowFallback: Boolean) {
        val fact = wikidataLookup.matchFact(text)
        val wikiQuery = wikiQuery(text)
        when {
            // A specific, structured fact ("cuándo nació", "capital de", "población de"...) goes first:
            // Wikidata answers it more precisely than hunting for the number in Wikipedia's prose.
            allowFallback && fact != null && isOnline.value -> askWikidataFact(fact)
            allowFallback && intentFallback.resolver != null && isOnline.value -> askAi(text, deep = false)
            // The AI is off (its providers do not allow minors) or not connected yet: Wikipedia still helps
            // with a plain "qué es / quién fue / busca..." when there is internet, without needing an account.
            allowFallback && wikiQuery != null && isOnline.value -> askWikipedia(wikiQuery)
            // Last resort before giving up: the phrase did not say "qué es" or "busca", but might still be a
            // topic on its own ("Miguel Hidalgo", "pregúntale por el día de la independencia"...). Wikipedia
            // is safe to try blindly — it is curated reference content, not an open answer from anywhere.
            allowFallback && isOnline.value && text.trim().length >= 3 -> askWikipedia(text.trim())
            else -> sayNotUnderstood()
        }
    }

    /** A structured fact from Wikidata; falls back to the AI or a plain Wikipedia search if it has none. */
    private fun askWikidataFact(fact: WikidataFact) {
        voiceEngine.speak("Buscando el dato.")
        viewModelScope.launch {
            val answer = runCatching { wikidataLookup.answer(fact) }.getOrNull()
            when {
                answer != null -> voiceEngine.speak(answer, listenAfter = true)
                intentFallback.resolver != null && isOnline.value -> askAi(fact.subject, deep = false)
                else -> askWikipedia(fact.subject)
            }
        }
    }

    /** The part after "qué es", "quién fue", "busca"... in the words the person actually said (accents kept). */
    private fun wikiQuery(text: String): String? {
        val match = WIKI_TRIGGER.find(VoiceText.fold(text)) ?: return null
        return text.substring(match.groups[1]!!.range.first).trim().takeIf { it.length >= 2 }
    }

    /** What to say after the current Wikipedia summary if the person says "dime más". */
    private var pendingWikiRest: String? = null

    private fun askWikipedia(query: String) {
        voiceEngine.speak("Buscando en Wikipedia.")
        viewModelScope.launch {
            val result = runCatching { wikipediaLookup.search(query) }.getOrNull()
            if (result == null) {
                voiceEngine.speak("No encontré nada en Wikipedia sobre eso. Di lista de comandos para escuchar lo que puedo hacer.")
            } else {
                pendingWikiRest = result.rest
                voiceEngine.speak(
                    result.short + if (result.rest != null) " Si quieres saber más, di dime más." else "",
                    listenAfter = true,
                )
            }
        }
    }

    /** Asks the AI backend. It answers with a command to run, or a short answer to read aloud. */
    private fun askAi(question: String, deep: Boolean) {
        val resolver = intentFallback.resolver ?: return sayNotUnderstood()
        viewModelScope.launch {
            val reply = runCatching { voiceEngine.speakWhile { resolver(question, deep) } }.getOrNull()
            val command = reply?.command
            val answer = reply?.answer
            when {
                command != null -> runCommand(command, allowFallback = false)
                answer != null -> {
                    intentFallback.lastQuestion = question
                    voiceEngine.speak(answer + if (reply.hasMore) " Si quieres saber más, di dime más." else "")
                }
                // The AI could not answer (server unreachable, no reply...): a "qué es / quién fue / busca"
                // question might still work through Wikipedia instead of giving up right there.
                else -> askWikipedia(wikiQuery(question) ?: question.trim())
            }
        }
    }

    // Never stay silent: the user cannot see whether the phrase was understood.
    private fun sayNotUnderstood() {
        cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
        voiceEngine.speak("No entendí ese comando. Di lista de comandos para escuchar lo que puedo hacer.")
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }

    /** "Hola" (or the assistant's name) opens the mic; needs the mic permission, which the screen checks. */
    fun setWakeWordEnabled(enabled: Boolean) {
        if (enabled == holdingWakeWord) return
        holdingWakeWord = enabled
        if (enabled) wakeWordController.acquire() else wakeWordController.release()
    }

    override fun onCleared() {
        setWakeWordEnabled(false)
        super.onCleared()
    }

    /**
     * Neutral welcome for everyone: name, role (and where they come from, if known), and what is
     * different inside. Wording per role can be customized later without changing this structure.
     */
    private suspend fun welcomeMessage(): String {
        val style = deviceSettings.addressStyle.first()
        val usuario = sessionRepository.currentUser.value
        // The name the person chose to be called wins over the one on the account.
        val nombre = deviceSettings.preferredName.first().ifBlank { usuario?.nombreCompleto.orEmpty() }
        val rol = usuario?.rol ?: RolUsuario.USUARIO.codigo
        val origen = usuario?.origen.orEmpty()
        val identidad = buildString {
            append(style.pick("Tu rol es $rol", "Su rol es $rol"))
            if (origen.isNotBlank() && rol != RolUsuario.ADMINISTRADOR.codigo) append(", de $origen")
            append(".")
        }
        return "Bienvenido $nombre. $identidad " +
            style.pick("Aquí tienes tu menú. Di menú para escucharlo.", "Aquí tiene su menú. Diga menú para escucharlo.")
    }

    /** Two quick taps on the mic silence the assistant, for someone using touch with their hands instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    /** The user refused the mic permission: say why nothing will happen instead of staying silent. */
    fun onMicPermissionDenied() {
        voiceEngine.speak(VoiceMessages.MIC_DENIED)
    }

    /** Same as saying [command] out loud; used by the on-screen suggestion chips. */
    fun onSuggestionTapped(command: String) {
        interpretCommand(command.lowercase())
    }

    fun onModuleSelected(modulo: ModuloCecapi) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(modulo.voicePrompt)
        _navEvents.tryEmit(modulo.route)
    }

    fun onSettingsSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo la configuración.")
        _navEvents.tryEmit(CecapiDestinations.SETTINGS)
    }

    /** Only administrador, directivo and educador ever see the button that calls this. */
    fun onGestionSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo gestión.")
        _navEvents.tryEmit(CecapiDestinations.GESTION)
    }

    private fun puedeGestionar(): Boolean {
        val rol = currentUser.value?.rol?.let(RolUsuario::fromCodigo) ?: return false
        return rol == RolUsuario.ADMINISTRADOR || rol == RolUsuario.DIRECTIVO || rol == RolUsuario.EDUCADOR
    }

    /** Only alumno and usuario ever see the button that calls this — the opposite of Gestión. */
    fun onEmergenciasSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo emergencias.")
        _navEvents.tryEmit(CecapiDestinations.EMERGENCIAS)
    }

    fun onReportarIncidenteSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo reportes.")
        _navEvents.tryEmit(CecapiDestinations.REPORTAR_INCIDENTE)
    }

    fun onSoporteSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo ayuda.")
        _navEvents.tryEmit(CecapiDestinations.SOPORTE)
    }

    private fun esAlumnoOUsuario(): Boolean {
        val rol = currentUser.value?.rol?.let(RolUsuario::fromCodigo) ?: return false
        return rol == RolUsuario.ALUMNO || rol == RolUsuario.USUARIO
    }

    /** Open to anyone signed in, every role — unlike Gestión y Soporte, que dependen del rol. */
    fun onRankingSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo clasificación.")
        _navEvents.tryEmit(CecapiDestinations.RANKING)
    }

    /** Open to anyone signed in, every role. */
    fun onEditarCuentaSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo editar mi cuenta.")
        _navEvents.tryEmit(CecapiDestinations.EDIT_ACCOUNT)
    }

    /** A card of the main menu was tapped (or named out loud). */
    fun onMenuItemSelected(item: MenuItem) = onMenuKey(item.key)

    private fun onMenuKey(key: String) {
        when (key) {
            MenuItem.SETTINGS_KEY -> onSettingsSelected()
            MenuItem.CAMERA_KEY -> openRoute(CecapiDestinations.CAMERA_HUB, "Abriendo la cámara.")
            MenuItem.PERSONALIZATION_KEY -> openRoute(CecapiDestinations.PERSONALIZATION, "Abriendo la personalización.")
            MenuItem.CHATS_KEY -> openRoute(CecapiDestinations.CHATS, "Abriendo los chats.")
            else -> ModuloCecapi.fromStorageCode(key)?.let(::onModuleSelected)
        }
    }

    private fun openRoute(route: String, speech: String) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _navEvents.tryEmit(route)
    }

    /** No speech here: the home screen opens next and says "Sesión cerrada" itself. */
    fun onLogout() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        sessionRepository.logout()
    }

    /** Says goodbye, waits for it to finish, then closes the app so the phone is free again. */
    private fun exitApp() {
        voiceEngine.speak("Cerrando la aplicación. Hasta luego.")
        viewModelScope.launch {
            delay(300)
            withTimeoutOrNull(6_000) { voiceEngine.state.first { it !is VoiceState.Speaking } }
            _exitEvents.emit(Unit)
        }
    }

    private companion object {
        const val OFFLINE_NOTICE =
            "Ahora no tienes conexión a internet, así que mis respuestas pueden ser menos precisas."

        const val EXIT_CONFIRM_WINDOW_MS = 30_000L
        val EXIT_CONFIRM_WORDS = setOf("si", "cerrar", "cierra", "confirmo", "hazlo", "correcto")

        // Matched against VoiceText.fold(text) — accents stripped, same length as the original — so the
        // captured group's range lines up with the words the person actually said, accents and all.
        // Las preguntas clave en español: qué, quién, cómo, cuándo, dónde, por qué, cuál, cuánto.
        val WIKI_TRIGGER = Regex(
            "(?:busca(?:r)? en wikipedia|busca(?:r)?|informacion sobre|dime sobre|wikipedia|" +
                "que es|que son|que fue|que fueron|que significa|" +
                "quien es|quien fue|quien son|quien era|" +
                "como es|como funciona|como se hace|como surgio|" +
                "cuando fue|cuando es|cuando ocurrio|cuando paso|cuando nacio|cuando murio|" +
                "donde esta|donde queda|donde se encuentra|donde nacio|" +
                "por que|porque|" +
                "cual es|cuales son|" +
                "cuanto es|cuanto vale|cuanto mide|cuanto pesa|cuanto cuesta)\\s+(.+)",
        )
    }
}

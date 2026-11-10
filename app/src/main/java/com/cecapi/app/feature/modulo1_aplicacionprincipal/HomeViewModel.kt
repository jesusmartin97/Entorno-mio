package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.MenuItem
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.util.PhoneStatusReader
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.util.WikidataFact
import com.cecapi.app.core.util.WikidataLookup
import com.cecapi.app.core.util.WikipediaLookup
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.core.voice.AssistantPreferences
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.IntentFallback
import com.cecapi.app.core.voice.LaunchRequests
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.notifications.NotificationReader
import com.cecapi.app.core.voice.VoiceMemory
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.WakeWordController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface HomeNavEvent {
    data object GoToLogin : HomeNavEvent
    data object GoToRegister : HomeNavEvent
    data object GoToSettings : HomeNavEvent
    data object ExitApp : HomeNavEvent
    data class GoToModule(val route: String) : HomeNavEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val assistantPreferences: AssistantPreferences,
    private val wakeWordController: WakeWordController,
    private val phoneStatusReader: PhoneStatusReader,
    private val voiceMemory: VoiceMemory,
    private val volumeControl: VolumeControl,
    private val cues: FeedbackCues,
    private val launchRequests: LaunchRequests,
    private val notificationReader: NotificationReader,
    private val intentFallback: IntentFallback,
    private val deviceSettings: DeviceSettings,
    private val sessionRepository: SessionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val wikipediaLookup: WikipediaLookup,
    private val wikidataLookup: WikidataLookup,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val assistantName: StateFlow<String> = assistantPreferences.assistantName.stateIn(
        viewModelScope, SharingStarted.Eagerly, "",
    )

    private var holdingWakeWord = false

    /** The main menu. Same for everyone here; the dashboard filters it by what the account may use. */
    val menu: List<MenuItem> = ModuleVoice.menu(ModuloCecapi.entries)

    // This ViewModel outlives its screen while Login/Register are open on top of it, so it must
    // ignore speech that belongs to those screens.
    private var screenActive = false

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    private val _navEvents = MutableSharedFlow<HomeNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<HomeNavEvent> = _navEvents

    init {
        viewModelScope.launch {
            // Opened from the widget's "Hablar" button: answer and listen instead of the long greeting.
            if (launchRequests.consumeListen()) {
                voiceEngine.speak(voiceEngine.wakePrompt(), listenAfter = true)
                return@launch
            }
            val name = assistantPreferences.assistantName.first()
            val online = connectivityObserver.observe().first()
            val loggedOut = if (sessionRepository.consumeLogoutNotice()) "Sesión cerrada. " else ""
            val style = deviceSettings.addressStyle.first()
            val intro = loggedOut + if (name.isBlank()) {
                style.pick(
                    "Hola, soy tu asistente. Di hola o toca el micrófono para hablar conmigo. " +
                        "Si quieres, ponme un nombre: di por ejemplo, llámate Alice. ",
                    "Hola, soy su asistente. Diga hola o toque el micrófono para hablar conmigo. " +
                        "Si desea, póngame un nombre: diga por ejemplo, llámate Alice. ",
                )
            } else {
                style.pick(
                    "Hola, soy $name. Di hola o $name para hablar conmigo. ",
                    "Hola, soy $name. Diga hola o $name para hablar conmigo. ",
                )
            }
            // Connection status is only mentioned when there is a problem.
            val offlineNotice = if (online) "" else "$OFFLINE_NOTICE "
            voiceEngine.speak(
                intro + offlineNotice + style.pick("Di menú para saber qué puedo hacer.", "Diga menú para saber qué puedo hacer."),
            )
        }
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            connectivityObserver.observe().collect { online ->
                if (wasOnline == true && !online && screenActive) { cues.play(FeedbackCues.Cue.WARNING); voiceEngine.speak(OFFLINE_NOTICE) }
                wasOnline = online
            }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.HOME).collect { speech ->
                if (screenActive) interpretCommand(speech.text.lowercase())
            }
        }
    }

    fun onModuleSelected(modulo: ModuloCecapi) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(modulo.voicePrompt)
        _navEvents.tryEmit(HomeNavEvent.GoToModule(modulo.route))
    }

    fun onAyudaSelected() {
        _navEvents.tryEmit(HomeNavEvent.GoToModule(CecapiDestinations.SOPORTE))
    }

    fun onSettingsSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo la configuración.")
        _navEvents.tryEmit(HomeNavEvent.GoToSettings)
    }

    fun onCrearCuentaSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo crear cuenta.")
        _navEvents.tryEmit(HomeNavEvent.GoToRegister)
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
        _navEvents.tryEmit(HomeNavEvent.GoToModule(route))
    }

    private fun openCameraMode(route: String) = openRoute(
        route,
        if (route == CecapiDestinations.ENVIRONMENT) "Abriendo el asistente del entorno." else "Abriendo la cámara para leer texto.",
    )

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }

    /** Wake-word listening needs the mic permission, so the screen turns it on only once it has it. */
    fun setWakeWordEnabled(enabled: Boolean) {
        if (enabled == holdingWakeWord) return
        holdingWakeWord = enabled
        if (enabled) wakeWordController.acquire() else wakeWordController.release()
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

    fun onSuggestionTapped(command: String) {
        interpretCommand(command.lowercase())
    }

    private var pendingExit = false
    private var pendingExitAskedAt = 0L

    /**
     * Answers "¿Cierro la aplicación? Di sí o no." Only for a short while, and only with a deliberate word:
     * closing the app can happen to anyone, including a child, so a word that also means something else in
     * passing conversation ("va", "sale") must not count.
     */
    private fun handlePendingConfirm(text: String): Boolean {
        if (!pendingExit || System.currentTimeMillis() - pendingExitAskedAt > EXIT_CONFIRM_WINDOW_MS) {
            pendingExit = false
            return false
        }
        pendingExit = false
        return when {
            VoiceText.isNo(text) -> {
                voiceEngine.speak("De acuerdo, no cierro nada.")
                true
            }
            VoiceText.normalize(text).split(" ").any { it in EXIT_CONFIRM_WORDS } -> {
                exitApp()
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
        val newName = NAME_COMMAND.find(text)?.groupValues?.get(1)?.trim { !it.isLetterOrDigit() }
        val phoneStatus = phoneStatusReader.answer(text)
        // Changing the volume is an action, so only try it when nothing above already claimed the phrase.
        val volume = if (newName == null && phoneStatus == null) volumeControl.handle(text) else null
        val notification = notificationReader.handle(text)
        val cameraRoute = ModuleVoice.directCameraRoute(text)
        val menuKey = ModuleVoice.matchKey(text, menu)
        when {
            newName != null -> saveAssistantName(newName)
            phoneStatus != null -> voiceEngine.speak(phoneStatus)
            volume != null -> voiceEngine.speak(volume)
            notification != null -> voiceEngine.speak(notification)
            SessionCommands.isExitApp(text) -> {
                pendingExit = true
                pendingExitAskedAt = System.currentTimeMillis()
                voiceEngine.speak("¿Cierro la aplicación? Di sí, cerrar, o no.", listenAfter = true)
            }
            VoiceText.normalize(text).let { "configuracion" in it || "ajustes" in it } -> onSettingsSelected()
            SessionCommands.isLogout(text) -> voiceEngine.speak("No tienes una sesión abierta.")
            CommandCatalog.isRequest(text) -> voiceEngine.speak(CommandCatalog.HOME)
            ModuleVoice.isListRequest(text) -> voiceEngine.speak(ModuleVoice.spokenList(menu))
            cameraRoute != null -> openCameraMode(cameraRoute)
            menuKey != null -> onMenuKey(menuKey)
            "iniciar sesión" in text || "iniciar sesion" in text -> {
                voiceEngine.speak("Abriendo el inicio de sesión.")
                _navEvents.tryEmit(HomeNavEvent.GoToLogin)
            }
            "ayuda" in text -> voiceEngine.speak(
                "Soy un asistente para personas invidentes. Puedo abrir la cámara para leer texto o describir lo que hay enfrente, " +
                    "y decirte cómo está tu teléfono. Di menú, estado del teléfono, " +
                    "batería, wifi, hora, fecha, o iniciar sesión. Si quieres que repita algo, " +
                    "di repite la solicitud anterior. Di lista de comandos para escucharlos todos.",
            )
            else -> notUnderstood(text, allowFallback)
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

    private fun sayNotUnderstood() {
        cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
        voiceEngine.speak("No entendí ese comando. Di lista de comandos para escuchar lo que puedo hacer.")
    }

    /** Says goodbye, waits for it to finish, then closes the app so the phone is free again. */
    private fun exitApp() {
        voiceEngine.speak("Cerrando la aplicación. Hasta luego.")
        viewModelScope.launch {
            delay(300)
            withTimeoutOrNull(6_000) { voiceEngine.state.first { it !is VoiceState.Speaking } }
            _navEvents.emit(HomeNavEvent.ExitApp)
        }
    }

    private fun saveAssistantName(name: String) {
        if (name.isBlank() || name.length > MAX_NAME_LENGTH || name.equals(DEFAULT_WAKE_WORD, ignoreCase = true)) {
            voiceEngine.speak("No pude usar ese nombre. Di por ejemplo: llámate Alice.")
            return
        }
        val display = name.replaceFirstChar { it.uppercase() }
        viewModelScope.launch {
            assistantPreferences.setAssistantName(display)
            voiceEngine.speak("Listo, ahora me llamo $display. Di $display para hablar conmigo.")
        }
    }

    override fun onCleared() {
        setWakeWordEnabled(false)
        voiceEngine.stopListening()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_WAKE_WORD = "hola"
        const val MAX_NAME_LENGTH = 30
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

        // "llámate Luna", "quisiera llamarte Luna", "quiero que te llames Luna", "te llamas Luna",
        // "te voy a llamar Luna", "tu nombre será Luna", "te pongo Luna", "ponte de nombre Luna"
        val NAME_COMMAND = Regex(
            "(?:l+[aá]ma(?:rte|te)|l+[aá]mar|te llam(?:as|es)|te llamar[eé]|" +
                "tu nombre (?:es|ser[aá]|va a ser|sea)|ponerte(?: de)? nombre|ponte(?: de)? nombre|" +
                "te pongo|te pondr[eé]|te voy a poner)" +
                "\\s+(?:como\\s+|de\\s+|el nombre\\s+(?:de\\s+)?)?(.+)",
        )
    }
}

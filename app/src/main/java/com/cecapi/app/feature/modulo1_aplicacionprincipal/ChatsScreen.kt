package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.theme.CecapiBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.BigBtn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo3_asistenteinteligente.ConsultaIaDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.RespuestaIaDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** One saved conversation, shown newest first. */
data class ChatItem(val id: Long, val pregunta: String, val respuesta: String?, val fecha: Long)

/** What the person asked to delete and has not confirmed yet. */
enum class PendingDelete { NONE, ONE, ALL }

data class ChatsUiState(
    val current: Int = 0,
    val pending: PendingDelete = PendingDelete.NONE,
)

/**
 * Chats: the conversations the person had with the assistant, listed newest first, read aloud one at a time
 * and deleted by voice or touch. It reads the tables that exist today (a question and its answer);
 * when the chat structure in the database is redefined, only [chats] and the delete calls change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val sessionRepository: SessionRepository,
    private val consultaDao: ConsultaIaDao,
    private val respuestaDao: RespuestaIaDao,
) : ViewModel() {

    private val chatsFlow: Flow<List<ChatItem>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { user ->
            consultaDao.observeRecent(user.id).map { consultas ->
                consultas.map { consulta ->
                    ChatItem(
                        id = consulta.id,
                        pregunta = consulta.textoPregunta,
                        respuesta = respuestaDao.findByConsulta(consulta.id)?.textoRespuesta,
                        fecha = consulta.fechaHora,
                    )
                }
            }
        }

    val chats: StateFlow<List<ChatItem>> = chatsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    /** When the last "¿borro...?" was asked; a confirmation only counts within [CONFIRM_WINDOW_MS]. */
    private var pendingAskedAt = 0L

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    init {
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.CHATS).collect { speech -> onSpeech(speech.text) }
        }
        if (sessionRepository.currentUser.value == null) {
            voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
        } else {
            viewModelScope.launch {
                // The list loads a moment after the screen opens; wait for its first real answer before counting.
                val total = chatsFlow.first().size
                voiceEngine.speak(
                    "Chats. " + countText(total) + " " + CommandCatalog.hint("chats"),
                    listenAfter = true,
                )
            }
        }
    }

    private fun countText(total: Int): String = when (total) {
        0 -> "Todavía no hay chats guardados. Cuando hables con la inteligencia artificial, aparecerán aquí."
        1 -> "Tienes un chat guardado."
        else -> "Tienes $total chats guardados. Di lee el último, para escuchar el más reciente."
    }

    /** The commands this screen answers, most specific first. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        // A pending confirmation only counts for a short while and "no" always wins ("no estoy seguro" is a no).
        val pending = _state.value.pending
            .takeIf { it != PendingDelete.NONE && System.currentTimeMillis() - pendingAskedAt < CONFIRM_WINDOW_MS }
            ?: PendingDelete.NONE
        when {
            CommandCatalog.isRequest(spoken) -> onCommandsRequested()
            // Only meaningful without a session: there is nothing to confirm/delete/read otherwise.
            sessionRepository.currentUser.value == null && has("crear cuenta", "registrarme", "registrar", "nueva cuenta") -> {
                voiceEngine.speak("Vamos a crear tu cuenta.")
                _routes.tryEmit(CecapiDestinations.REGISTER)
            }
            sessionRepository.currentUser.value == null && has("iniciar sesion", "inicia sesion", "ingresar") -> {
                voiceEngine.speak("Abriendo el inicio de sesión.")
                _routes.tryEmit(CecapiDestinations.LOGIN)
            }
            pending != PendingDelete.NONE && VoiceText.isNo(spoken) -> cancelDelete()
            pending != PendingDelete.NONE && VoiceText.isYes(spoken) -> confirmDelete()
            has("borra todos", "borrar todos", "elimina todos", "eliminar todos", "vacia") -> askDeleteAll()
            has("borra este", "borrar este", "elimina este", "eliminar este", "borra el chat", "borra el actual") -> askDeleteCurrent()
            has("cuantos") -> speakCount()
            has("el ultimo", "la ultima", "lee el ultimo", "mas reciente", "abre el ultimo") -> readAt(0)
            has("siguiente", "mas antiguo") -> readAt(_state.value.current + 1)
            has("anterior", "mas nuevo") -> readAt(_state.value.current - 1)
            has("repite", "otra vez", "de nuevo", "lee") -> readAt(_state.value.current)
            has("nuevo chat", "nueva conversacion", "empezar un chat") -> {
                voiceEngine.speak("Para un chat nuevo, vuelve al menú y háblame. Cada conversación se guarda aquí sola.")
                _back.tryEmit(Unit)
            }
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. " + CommandCatalog.hint("chats"), listenAfter = true)
            }
        }
    }

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.CHATS, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun speakCount() {
        voiceEngine.speak(countText(chats.value.size), listenAfter = chats.value.isNotEmpty())
    }

    /** Reads chat number [index] (0 is the newest), keeping the position inside the list. */
    fun readAt(index: Int) {
        val list = chats.value
        if (list.isEmpty()) return speakCount()
        val target = index.coerceIn(0, list.lastIndex)
        _state.value = _state.value.copy(current = target, pending = PendingDelete.NONE)
        val chat = list[target]
        val position = "Chat ${target + 1} de ${list.size}, del ${DateFormat.getDateInstance(DateFormat.LONG).format(Date(chat.fecha))}. "
        val edge = when {
            target == index -> ""
            index < 0 -> " Este es el más reciente."
            else -> " Este es el más antiguo."
        }
        voiceEngine.speak(
            position + "Preguntaste: ${chat.pregunta}. " + (chat.respuesta?.let { "Respondí: $it" } ?: "No guardé la respuesta.") + edge,
            listenAfter = true,
        )
    }

    fun askDeleteCurrent() {
        if (chats.value.isEmpty()) return speakCount()
        _state.value = _state.value.copy(pending = PendingDelete.ONE)
        pendingAskedAt = System.currentTimeMillis()
        voiceEngine.speak("¿Borro el chat ${_state.value.current + 1}? Di sí, borrar, o no.", listenAfter = true)
    }

    fun askDeleteAll() {
        if (chats.value.isEmpty()) return speakCount()
        _state.value = _state.value.copy(pending = PendingDelete.ALL)
        pendingAskedAt = System.currentTimeMillis()
        voiceEngine.speak("¿Borro los ${chats.value.size} chats? No se pueden recuperar. Di sí, borrar, o no.", listenAfter = true)
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(pending = PendingDelete.NONE)
        voiceEngine.speak("De acuerdo, no borré nada.", listenAfter = true)
    }

    fun confirmDelete() {
        val pending = _state.value.pending
        val user = sessionRepository.currentUser.value ?: return
        val target = chats.value.getOrNull(_state.value.current)
        _state.value = ChatsUiState()
        viewModelScope.launch {
            if (pending == PendingDelete.ALL) {
                consultaDao.deleteAllByUser(user.id)
                cues.play(FeedbackCues.Cue.SUCCESS)
                voiceEngine.speak("Listo, borré todos los chats.")
            } else if (pending == PendingDelete.ONE && target != null) {
                consultaDao.deleteById(target.id)
                cues.play(FeedbackCues.Cue.SUCCESS)
                voiceEngine.speak("Listo, borré ese chat.", listenAfter = true)
            }
        }
    }

    private companion object {
        /** A "sí" or "no" to "¿borro...?" only counts for this long; after that it is a normal command. */
        const val CONFIRM_WINDOW_MS = 30_000L
    }
}

@Composable
fun ChatsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsState()
    val state by viewModel.state.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTopBar(
            eyebrow = "MENÚ",
            title = "Chats",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        if (state.pending != PendingDelete.NONE) {
            NoticeBanner(
                if (state.pending == PendingDelete.ALL) "¿Borrar los ${chats.size} chats? Di sí o no." else "¿Borrar este chat? Di sí o no.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigBtn(icon = Icons.Filled.Check, label = "Sí", variant = BigBtnVariant.Danger, half = true, onClick = viewModel::confirmDelete, modifier = Modifier.weight(1f))
                BigBtn(icon = Icons.Filled.Close, label = "No", half = true, onClick = viewModel::cancelDelete, modifier = Modifier.weight(1f))
            }
        }

        if (chats.isEmpty()) {
            Text(
                "Todavía no hay chats guardados. Cuando hables con la inteligencia artificial, aparecerán aquí.",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                ChatCard(
                    chat = chat,
                    selected = index == state.current,
                    onClick = { viewModel.readAt(index) },
                )
            }
        }

        if (chats.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TopAction(
                    icon = Icons.Filled.Delete,
                    label = "Borrar este",
                    help = "Borrar este chat. Te pido confirmación antes de borrarlo.",
                    onClick = viewModel::askDeleteCurrent,
                    tint = CecapiError,
                )
                TopAction(
                    icon = Icons.Filled.Delete,
                    label = "Borrar todos",
                    help = "Borrar todos los chats. Te pido confirmación antes de borrarlos.",
                    onClick = viewModel::askDeleteAll,
                    tint = CecapiError,
                )
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di lee el último, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ChatCard(chat: ChatItem, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(chat.fecha))
    val help = "Chat del $date. Preguntaste: ${chat.pregunta}. Toca dos veces para que te lo lea."
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(CecapiSurface)
            .border(if (selected) 3.dp else 1.dp, if (selected) CecapiAccent else CecapiTextMuted.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(16.dp),
    ) {
        Text(
            chat.pregunta,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
        )
        Text(date, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted, modifier = Modifier.padding(top = 4.dp))
        chat.respuesta?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

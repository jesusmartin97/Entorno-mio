package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Con quién se compara el individual: solo su institución, o todo el mundo (otras instituciones incluidas). */
enum class AlcanceRanking { MI_INSTITUCION, TODAS }

data class RankingUiState(
    val individual: List<RankingFila> = emptyList(),
    val alcance: AlcanceRanking = AlcanceRanking.TODAS,
    /** Un usuario independiente no tiene institución: no tiene sentido ofrecerle "mi institución". */
    val tieneInstitucion: Boolean = false,
    val miId: Long? = null,
)

/**
 * Clasificación: la tabla individual (apodo, no el nombre real — hay menores). Las instituciones no
 * compiten entre ellas; un alumno solo puede comparar su lugar contra su institución o contra todas —
 * "competir con alumnos de su institución y de otras", tal cual se pidió. Los puntos vienen de Actividades
 * de sonidos; alguien sin puntos (vibración todavía no da puntos, o nunca ha jugado) simplemente no
 * aparece en la lista.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RankingViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val rankingDao: RankingDao,
    private val cues: FeedbackCues,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val miOrigen = sessionRepository.currentUser.value?.origen.orEmpty()
    private val tieneInstitucion = miOrigen.isNotBlank() && !miOrigen.equals("admin", ignoreCase = true)

    private val _alcance = MutableStateFlow(if (tieneInstitucion) AlcanceRanking.MI_INSTITUCION else AlcanceRanking.TODAS)

    private val individualFlow = _alcance.flatMapLatest { alcance ->
        if (alcance == AlcanceRanking.MI_INSTITUCION && tieneInstitucion) {
            rankingDao.observeIndividualDeInstitucion(miOrigen)
        } else {
            rankingDao.observeIndividual()
        }
    }

    private val _uiState = MutableStateFlow(RankingUiState(alcance = _alcance.value, tieneInstitucion = tieneInstitucion))
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        val usuario = sessionRepository.currentUser.value
        _uiState.value = _uiState.value.copy(miId = usuario?.id)

        viewModelScope.launch {
            individualFlow.collect { lista -> _uiState.value = _uiState.value.copy(individual = lista) }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.RANKING).collect { speech -> onSpeech(speech.text) }
        }

        voiceEngine.speak(
            if (tieneInstitucion) {
                "Clasificación. Empiezas viendo tu institución. Di todas, para compararte con otras " +
                    "instituciones también, o mi institución para volver. Di mi lugar, para saber tu posición."
            } else {
                "Clasificación. Aquí ves tu lugar entre todos. Di mi lugar, para saber tu posición."
            },
            listenAfter = true,
        )
    }

    fun cambiarAlcance(nuevo: AlcanceRanking) {
        if (nuevo == _alcance.value) return
        _alcance.value = nuevo
        _uiState.value = _uiState.value.copy(alcance = nuevo)
    }

    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            has("mi lugar", "mi posicion", "mi puesto", "en que lugar voy", "como voy") -> speakMiLugar()
            has("todas", "todas las instituciones", "otras instituciones", "comparar con todos") -> {
                cambiarAlcance(AlcanceRanking.TODAS)
                voiceEngine.speak("Comparando con todas las instituciones.", listenAfter = true)
            }
            _uiState.value.tieneInstitucion && has("mi institucion", "solo mi institucion", "mis companeros") -> {
                cambiarAlcance(AlcanceRanking.MI_INSTITUCION)
                voiceEngine.speak("Comparando solo con tu institución.", listenAfter = true)
            }
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. Di mi lugar, o atrás.", listenAfter = true)
            }
        }
    }

    private fun speakMiLugar() {
        val id = _uiState.value.miId
        val lugar = _uiState.value.individual.indexOfFirst { it.usuarioId == id }
        if (lugar == -1) {
            voiceEngine.speak("Todavía no tienes puntos en Actividades. Practica con los sonidos para aparecer aquí.", listenAfter = true)
            return
        }
        val fila = _uiState.value.individual[lugar]
        val ambito = if (_uiState.value.alcance == AlcanceRanking.MI_INSTITUCION) "de tu institución" else "entre todas las instituciones"
        voiceEngine.speak("Vas en el lugar ${lugar + 1} $ambito, con ${fila.puntosTotales} puntos.", listenAfter = true)
    }

    fun onCommandsRequested() {
        val textoAlcance = if (_uiState.value.tieneInstitucion) {
            "Di todas, para comparar con otras instituciones, o mi institución, para volver a solo la tuya. "
        } else {
            ""
        }
        voiceEngine.speak(
            "Di mi lugar, para saber tu posición. $textoAlcance" +
                "Para cambiar tu apodo, o si te muestro con tu nombre o tu apodo, ve a Editar mi cuenta. " +
                "Atrás, para volver al menú.",
            listenAfter = true,
        )
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }
}

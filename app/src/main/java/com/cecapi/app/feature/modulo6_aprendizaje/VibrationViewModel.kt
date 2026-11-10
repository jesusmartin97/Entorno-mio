package com.cecapi.app.feature.modulo6_aprendizaje

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import javax.inject.Inject

// =============================================================================
// AVISO PARA EL EQUIPO: este archivo es NUEVO (actividad de vibracion).
//
// Hace lo mismo que LearningViewModel.kt pero con VibrationEngine en lugar de
// AudioSpatialPlayer: dice la instruccion una sola vez al empezar, reproduce el
// patron de vibracion de cada ejercicio, escucha la respuesta ("corto", "largo"
// o "mixto"), la califica y guarda el resultado en Room.
//
// Ademas incluye el PUNTAJE FINAL (tarea 3): al terminar todos los ejercicios
// dice "Obtuviste X de Y" y ofrece repetir o salir, por voz o con los botones.
// =============================================================================

private const val NIVEL_VIBRACION = 1 // todo el banco esta en nivel 1 por ahora

private fun normalizarTexto(texto: String): String =
    Normalizer.normalize(texto.lowercase().trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")

data class VibrationUiState(
    val ejercicios: List<EjercicioVibracionEntity> = emptyList(),
    val indiceActual: Int = 0,
    val feedback: String? = null,
    val feedbackCorrecto: Boolean = false,
    val reproduciendo: Boolean = false, // true mientras habla la instruccion o vibra el patron
    val aciertos: Int = 0,
    val terminado: Boolean = false,
    val mensajeFinal: String? = null,
) {
    val ejercicioActual: EjercicioVibracionEntity? get() = ejercicios.getOrNull(indiceActual)
}

@HiltViewModel
class VibrationViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: VibrationRepository,
    private val vibrationEngine: VibrationEngine,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(VibrationUiState())
    val uiState: StateFlow<VibrationUiState> = _uiState.asStateFlow()

    // Evento de "salir de la pantalla": la pantalla lo escucha y llama a onBack.
    private val _salir = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val salir: SharedFlow<Unit> = _salir.asSharedFlow()

    private var instruccionDicha = false
    private var presentacionJob: Job? = null

    init {
        viewModelScope.launch {
            repository.seedEjerciciosSiVacio()
        }

        viewModelScope.launch {
            // Igual que en audio: la actividad arranca cuando ya hay sesion iniciada.
            sessionRepository.currentUser.filterNotNull()
                .map { it.id }
                .distinctUntilChanged()
                .flatMapLatest { repository.observeEjerciciosDelNivel(NIVEL_VIBRACION) }
                .collect { ejercicios ->
                    _uiState.value = VibrationUiState(ejercicios = ejercicios.shuffled())
                    presentarEjercicioActual()
                }
        }

        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onRespuestaDada(speech.text) }
        }
    }

    fun onMicTapped() {
        if (_uiState.value.reproduciendo) return // no escuchar mientras vibra o habla
        voiceEngine.startListening()
    }

    /** Dice la instruccion solo la primera vez, y siempre reproduce el patron del ejercicio. */
    private fun presentarEjercicioActual() {
        val ejercicio = _uiState.value.ejercicioActual ?: return
        presentacionJob?.cancel()
        vibrationEngine.detener()
        presentacionJob = viewModelScope.launch {
            _uiState.update { it.copy(reproduciendo = true) }

            if (!instruccionDicha) {
                voiceEngine.speak(ejercicio.instruccion)
                esperarInicioYFinDeVoz()
                instruccionDicha = true
            } else {
                esperarAQueTermineDeHablar() // por si todavia suena la retroalimentacion anterior
            }

            vibrationEngine.reproducirPatron(ejercicio.patronMs.aPatronMs(), ejercicio.intensidad)
            _uiState.update { it.copy(reproduciendo = false) }
        }
    }

    /** Vuelve a reproducir el mismo patron, sin repetir la instruccion hablada. */
    fun onRepetirVibracion() {
        val ejercicio = _uiState.value.ejercicioActual ?: return
        if (_uiState.value.reproduciendo || _uiState.value.terminado) return
        presentacionJob?.cancel()
        presentacionJob = viewModelScope.launch {
            _uiState.update { it.copy(reproduciendo = true) }
            vibrationEngine.reproducirPatron(ejercicio.patronMs.aPatronMs(), ejercicio.intensidad)
            _uiState.update { it.copy(reproduciendo = false) }
        }
    }

    fun onRespuestaDada(respuesta: String) {
        val estado = _uiState.value
        if (estado.terminado) {
            procesarComandoFinal(respuesta)
            return
        }
        if (estado.feedback != null) return // este ejercicio ya se respondio (evita contar doble)
        val usuario = sessionRepository.currentUser.value ?: return
        val ejercicio = estado.ejercicioActual ?: return

        val esCorrecto = normalizarTexto(respuesta).contains(normalizarTexto(ejercicio.respuestaCorrecta))
        viewModelScope.launch { repository.registrarResultado(usuario.id, ejercicio.id, esCorrecto) }

        val mensaje = if (esCorrecto) {
            "¡Correcto! Muy bien."
        } else {
            "No es correcto. La respuesta era: ${ejercicio.respuestaCorrecta}."
        }
        voiceEngine.speak(mensaje)
        _uiState.update {
            it.copy(
                feedback = mensaje,
                feedbackCorrecto = esCorrecto,
                aciertos = if (esCorrecto) it.aciertos + 1 else it.aciertos,
            )
        }
    }

    fun onSiguienteEjercicio() {
        val estado = _uiState.value
        if (estado.terminado || estado.feedback == null) return // solo avanza despues de responder
        val siguiente = estado.indiceActual + 1
        if (siguiente >= estado.ejercicios.size) {
            terminar()
            return
        }
        _uiState.update { it.copy(indiceActual = siguiente, feedback = null) }
        presentarEjercicioActual()
    }

    /** Puntaje final hablado (tarea 3). */
    private fun terminar() {
        presentacionJob?.cancel()
        vibrationEngine.detener()
        val estado = _uiState.value
        val mensaje = "Terminaste. Obtuviste ${estado.aciertos} de ${estado.ejercicios.size}. " +
            "Di repetir para intentarlo otra vez, o salir para volver."
        _uiState.update { it.copy(terminado = true, feedback = null, reproduciendo = false, mensajeFinal = mensaje) }
        voiceEngine.speak(mensaje)
    }

    fun onRepetirActividad() {
        _uiState.update {
            it.copy(
                ejercicios = it.ejercicios.shuffled(),
                indiceActual = 0,
                feedback = null,
                aciertos = 0,
                terminado = false,
                mensajeFinal = null,
            )
        }
        presentarEjercicioActual()
    }

    fun onSalir() {
        presentacionJob?.cancel()
        vibrationEngine.detener()
        _salir.tryEmit(Unit)
    }

    /** Al final de la actividad, lo que diga la persona se interpreta como "repetir" o "salir". */
    private fun procesarComandoFinal(texto: String) {
        val t = normalizarTexto(texto)
        when {
            t.contains("repet") || t.contains("otra vez") -> onRepetirActividad()
            t.contains("salir") -> onSalir()
        }
    }

    private suspend fun esperarAQueTermineDeHablar() {
        voiceState.first { it !is VoiceState.Speaking }
    }

    /** Espera a que la voz EMPIECE (hasta 1.5 s) y luego a que TERMINE, para no encimarse con la vibracion. */
    private suspend fun esperarInicioYFinDeVoz() {
        withTimeoutOrNull(1_500) { voiceState.first { it is VoiceState.Speaking } }
        esperarAQueTermineDeHablar()
    }

    override fun onCleared() {
        super.onCleared()
        vibrationEngine.detener() // por si sales de la pantalla mientras algo vibraba
    }
}

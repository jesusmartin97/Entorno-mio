package com.cecapi.app.feature.modulo7_entorno

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.GALLERY_WORDS
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnvironmentUiState(
    val isProcessing: Boolean = false,
    val descripcion: String? = null,
    val errorMessage: String? = null,
    /** La foto vive solo en memoria mientras se muestra y se analiza. Nunca se guarda en disco ni en Room. */
    val foto: Bitmap? = null,
)

@HiltViewModel
class EnvironmentViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val repository: EnvironmentRepository,
    private val cues: FeedbackCues,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(EnvironmentUiState())
    val uiState: StateFlow<EnvironmentUiState> = _uiState.asStateFlow()

    private val _flashSoloParaFotos = MutableStateFlow(false)
    val flashSoloParaFotos: StateFlow<Boolean> = _flashSoloParaFotos.asStateFlow()

    // The screen owns the camera, so voice commands reach it through these.
    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    // Asks the screen to open the system picture picker (it owns the activity result launcher).
    private val _pickRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pickRequests: SharedFlow<Unit> = _pickRequests

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    init {
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.ENVIRONMENT).collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Descripción del entorno. Apunta la cámara al frente y di qué hay enfrente. " +
                CommandCatalog.hint("cámara"),
            listenAfter = true,
        )
    }

    /** The commands this screen answers, most specific first. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.ENVIRONMENT, listenAfter = true)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            has("cuanto llevo", "cuanto dinero llevo", "que llevo", "cuanto suman", "total de billetes", "dime el total") -> decirTotalBilletes()
            has("borra los billetes", "borrar billetes", "limpia los billetes", "empieza de nuevo", "reinicia los billetes", "borrar total") -> reiniciarBilletes()
            has("agrega este billete", "agregar billete", "agrega billete", "suma este billete", "sumar billete", "anota este billete", "anotar billete") -> pedirSumarBillete()
            has("activa flash", "activar flash", "flash on", "enciende flash", "prende flash") -> activarFlashParaFotos()
            has("desactiva flash", "desactivar flash", "flash off", "apaga flash", "quita flash") -> quitaFlash()
            has(
                "agrega esta moneda", "agregar moneda", "agrega moneda", "suma esta moneda", "sumar moneda",
                "suma la moneda", "sumar la moneda", "anota esta moneda", "anotar moneda", "anota la moneda",
                "anotar la moneda", "guarda la moneda", "guardar la moneda", "guarda moneda", "guardar moneda",
                "cuenta la moneda", "contar la moneda",
            ) -> pedirSumarMoneda()
            has("borra las monedas", "borrar monedas", "limpia las monedas", "reinicia las monedas") -> reiniciarMonedas()
            has("moneda", "monedas") -> pedirCapturaMoneda()
            has("billete", "billetes", "dinero", "cuanto vale", "cuanto dinero") -> pedirCapturaBillete()
            has("leer texto", "lee", "leer", "texto", "documento") -> {
                cues.play(FeedbackCues.Cue.NAVIGATE)
                voiceEngine.speak("Cambiando al lector de texto.")
                _routes.tryEmit(CecapiDestinations.DOCUMENT_READER)
            }
            has(*GALLERY_WORDS) -> pedirGaleria()
            has("otra vez", "repite", "repetir", "de nuevo", "dilo") -> repetir()
            has("enfrente", "describe", "descripcion", "foto", "fotografia", "captura", "toma", "analiza",
                "que hay", "que ves", "entorno") -> pedirCaptura()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak(
                    "No entendí. Di qué hay enfrente, repite, o atrás. " + CommandCatalog.hint("cámara"),
                    listenAfter = true,
                )
            }
        }
    }

    // true cuando la próxima foto es para leer un billete (dijo "qué billete es") y no para describir el entorno.
    private var buscandoBilleteActual = false

    // true cuando ese billete se va a sumar a la cuenta de "uno en uno" (dijo "agrega este billete").
    private var sumandoBilleteActual = false

    // Cuenta exacta de los billetes que se han mostrado uno por uno, para sumar el total.
    private val billetesContados = mutableMapOf<DenominacionBillete, Int>()

    // true cuando la próxima foto es para reconocer una moneda (dijo "qué moneda es").
    private var buscandoMonedaActual = false

    // true cuando esa moneda se va a sumar a la cuenta de "una en una" (dijo "agrega esta moneda").
    private var sumandoMonedaActual = false

    // Cuenta de las monedas que se han mostrado una por una, para sumar el total junto con los billetes.
    private val monedasContadas = mutableMapOf<DenominacionMoneda, Int>()

    /**
     * Botón grande "Tomar foto": vibra, dice la cuenta regresiva y captura al terminar. La foto se
     * analiza en memoria; no se guarda en disco ni en la base de datos.
     */
    fun pedirFotoConCuenta() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        buscandoMonedaActual = false
        sumandoMonedaActual = false
        _uiState.value = EnvironmentUiState(isProcessing = true)
        cues.play(FeedbackCues.Cue.TAP, withSound = false)
        voiceEngine.speak("Tomando foto en 3, 2, 1")
        viewModelScope.launch {
            delay(3_200)
            _captureRequests.tryEmit(Unit)
        }
    }

    /** Also what the on-screen button and the "qué hay enfrente" chip call. */
    fun pedirCaptura() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        buscandoMonedaActual = false
        sumandoMonedaActual = false
        voiceEngine.speak("Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** Lo que dice "qué billete es" y el botón del mismo nombre: toma la foto y dice de cuánto es el billete. */
    fun activarFlashParaFotos() {
        _flashSoloParaFotos.value = true
        voiceEngine.speak("Flash activado para las próximas fotos. Di quita flash para desactivarlo.", listenAfter = true)
    }

    fun quitaFlash() {
        _flashSoloParaFotos.value = false
        voiceEngine.speak("Flash quitado. Quedó como si nunca se hubiera activado.", listenAfter = true)
    }

    fun cancelarFlashSilencioso() {
        _flashSoloParaFotos.value = false
    }

    fun pedirCapturaBillete() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = true
        sumandoBilleteActual = false
        buscandoMonedaActual = false
        sumandoMonedaActual = false
        voiceEngine.speak("Pon el billete plano frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** Vuelve a la cámara para tomar otra foto: quita la foto y la descripción anteriores. */
    /** "Agrega este billete": toma la foto de un billete y, si se lee con claridad, lo suma a la cuenta. */
    fun pedirSumarBillete() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = true
        sumandoBilleteActual = true
        buscandoMonedaActual = false
        sumandoMonedaActual = false
        voiceEngine.speak("Muéstrame un solo billete, plano frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** "Qué moneda es": toma la foto de una moneda y dice de cuánto es. */
    fun pedirCapturaMoneda() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        buscandoMonedaActual = true
        sumandoMonedaActual = false
        voiceEngine.speak("Pon una sola moneda sobre una superficie lisa, frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** "Agrega esta moneda": toma la foto de una moneda y, si se reconoce con claridad, la suma a la cuenta. */
    fun pedirSumarMoneda() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        buscandoMonedaActual = true
        sumandoMonedaActual = true
        voiceEngine.speak("Muéstrame una sola moneda, sola y frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** "Borra las monedas": empieza la cuenta de monedas de cero (la de billetes no se toca). */
    fun reiniciarMonedas() {
        monedasContadas.clear()
        voiceEngine.speak("Listo. Empecé la cuenta de monedas desde cero.", listenAfter = true)
    }

    /** "Cuánto llevo": dice lo que se ha sumado (billetes y monedas) y el total. */
    fun decirTotalBilletes() {
        voiceEngine.speak(fraseTotalDinero(), listenAfter = true)
    }

    /** Billetes y monedas sumados. Si no hay monedas, queda igual que la cuenta de solo billetes. */
    private fun fraseTotalDinero(): String {
        if (monedasContadas.isEmpty()) return fraseTotalBilletes()
        val partesBilletes = billetesContados.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
            if (n == 1) "un billete de ${d.nombreHablado}" else "$n billetes de ${d.nombreHablado}"
        }
        val partesMonedas = monedasContadas.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
            if (n == 1) "una moneda de ${d.nombreHablado}" else "$n monedas de ${d.nombreHablado}"
        }
        val partes = partesBilletes + partesMonedas
        val total = billetesContados.entries.sumOf { it.key.valor * it.value } +
            monedasContadas.entries.sumOf { it.key.valor * it.value }
        val unidos = if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
        return "Llevas $unidos. Son $total pesos en total."
    }

    /** Suma la moneda de la foto a la cuenta solo si se reconoció con claridad; si no, dice por qué. */
    private fun sumarMonedaALaCuenta(resultado: ResultadoMoneda?): String {
        val d = resultado?.denominacion
        if (resultado == null || d == null || !resultado.esSegura) {
            return "No sumé nada. Para llevar la cuenta, muéstrame una sola moneda a la vez, sola y con buena luz."
        }
        monedasContadas[d] = (monedasContadas[d] ?: 0) + 1
        return "Anotada, una moneda de ${d.nombreHablado}. " + fraseTotalDinero()
    }

    /** "Borra los billetes": empieza la cuenta de cero. */
    fun reiniciarBilletes() {
        billetesContados.clear()
        voiceEngine.speak("Listo. Empecé la cuenta de billetes desde cero.", listenAfter = true)
    }

    private fun fraseTotalBilletes(): String {
        if (billetesContados.isEmpty()) return "Todavía no has sumado ningún billete. Di agrega este billete."
        val partes = billetesContados.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
            if (n == 1) "un billete de ${d.nombreHablado}" else "$n billetes de ${d.nombreHablado}"
        }
        val total = billetesContados.entries.sumOf { it.key.valor * it.value }
        val cuantos = billetesContados.values.sum()
        val unidos = if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
        return "Llevas $unidos. Son $cuantos ${if (cuantos == 1) "billete" else "billetes"}, $total pesos en total."
    }

    /** Suma el billete de la foto a la cuenta solo si es uno solo y se leyó con claridad; si no, dice por qué. */
    private fun sumarALaCuenta(resultado: ResultadoBillete?): String {
        val d = resultado?.denominacion
        if (resultado == null || d == null || resultado.hayVarios) {
            return "No sumé nada. Para llevar la cuenta, muéstrame un solo billete a la vez, plano y con buena luz."
        }
        billetesContados[d] = (billetesContados[d] ?: 0) + 1
        return "Anotado, un billete de ${d.nombreHablado}. " + fraseTotalDinero()
    }

    fun tomarOtraFoto() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        buscandoMonedaActual = false
        sumandoMonedaActual = false
        _uiState.value = EnvironmentUiState()
        voiceEngine.speak("Listo. Apunta la cámara y di qué hay enfrente.")
    }

    fun repetir() {
        val descripcion = _uiState.value.descripcion
        if (descripcion == null) {
            voiceEngine.speak("Todavía no he descrito nada. Di qué hay enfrente.", listenAfter = true)
        } else {
            voiceEngine.speak(descripcion)
        }
    }

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.ENVIRONMENT, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onCameraPermissionDenied() {
        voiceEngine.speak("Sin el permiso de la cámara no puedo describir lo que hay enfrente. Actívalo en los ajustes de la aplicación.")
    }

    /**
     * "Elige una foto": opens the system picture picker. The person picks one picture and the app sees only
     * that one; there is no permission to the whole gallery. Said out loud first so it is always their decision.
     */
    fun pedirGaleria() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        buscandoMonedaActual = false
        sumandoMonedaActual = false
        voiceEngine.speak("Voy a abrir tus fotos. Elige la imagen que quieres que describa; solo veré esa.")
        _pickRequests.tryEmit(Unit)
    }

    fun onPickCancelled() {
        voiceEngine.speak("No elegiste ninguna foto. Di qué hay enfrente, o elige una foto.", listenAfter = true)
    }

    fun onPickFailed() {
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak("No pude abrir esa imagen. Prueba con otra.", listenAfter = true)
    }

    fun onCaptureFailed() {
        val message = "No pude tomar la foto. Revisa que la cámara esté libre e intenta de nuevo."
        _uiState.value = EnvironmentUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak(message, listenAfter = true)
    }

    /** Foto tomada por la cámara, ya en memoria. */
    fun onFotoCapturada(bitmap: Bitmap) {
        procesarFoto(bitmap, "Foto tomada, analizando.")
    }

    /** Imagen elegida de la galería, decodificada en memoria sin copiarla a disco. */
    fun onImagenElegida(bitmap: Bitmap) {
        procesarFoto(bitmap, "Analizando la foto.")
    }

    private fun procesarFoto(bitmap: Bitmap, mensaje: String) {
        // Si ya hay una foto analizándose, no se manda otra a Gemini: se evita gastar tokens.
        if (_uiState.value.isProcessing) return
        val buscarBillete = buscandoBilleteActual
        val sumarBillete = sumandoBilleteActual
        val buscarMoneda = buscandoMonedaActual
        val sumarMoneda = sumandoMonedaActual
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        buscandoMonedaActual = false
        sumandoMonedaActual = false
        _uiState.value = EnvironmentUiState(isProcessing = true, foto = bitmap)
        voiceEngine.speak(
            when {
                buscarBillete -> "Leyendo el billete."
                buscarMoneda -> "Reconociendo la moneda."
                else -> mensaje
            },
        )
        viewModelScope.launch {
            repository.analizarFotoEnMemoria(bitmap, buscarBillete, buscarMoneda)
                .onSuccess { result ->
                    // Si era para llevar la cuenta, lo que se dice es lo anotado y el total, no la descripción suelta.
                    val dicho = when {
                        sumarBillete -> sumarALaCuenta(result.billetes)
                        sumarMoneda -> sumarMonedaALaCuenta(result.monedas)
                        else -> result.descripcion
                    }
                    _uiState.value = EnvironmentUiState(descripcion = dicho, foto = bitmap)
                    // Vibración corta: el análisis terminó, justo antes de leer el resultado.
                    cues.play(FeedbackCues.Cue.TAP)
                    voiceEngine.speak(dicho)
                }
                .onFailure {
                    val message = "No pude analizar la imagen. Intenta de nuevo."
                    _uiState.value = EnvironmentUiState(errorMessage = message)
                    cues.play(FeedbackCues.Cue.ERROR)
                    voiceEngine.speak(message, listenAfter = true)
                }
        }
    }
}

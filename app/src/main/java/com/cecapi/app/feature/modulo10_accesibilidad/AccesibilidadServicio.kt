package com.cecapi.app.feature.modulo10_accesibilidad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.cecapi.app.MainActivity
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.voice.VoiceEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Servicio de accesibilidad de CECAPI (Equipo 5). Hace dos cosas:
 *
 * 1. Escucha el doble volumen abajo para tomar una captura temporal de lo que se ve en pantalla,
 *    reconocer su texto con ML Kit y leerlo en voz alta.
 * 2. Escucha el doble volumen arriba para describir solo las imágenes de la pantalla: con internet
 *    las describe Gemini (igual que la cámara); sin internet, tapa las zonas con texto y etiqueta
 *    lo que queda con ML Kit.
 * 3. Avisa cuando cambia de ventana y puede leer la pantalla completa desde el árbol de accesibilidad.
 *
 * No pide exploración táctil: los gestos de un dedo se quedan como los de Android, sin que CECAPI
 * los intercepte. Por eso tampoco recibe los gestos del perfil ([onGesture]): el perfil queda para
 * cuando se decida volver a activar la exploración táctil.
 *
 * La captura se queda en memoria: no se escribe en disco y no se muestra al usuario. El OCR corre en
 * el teléfono con el modelo de ML Kit que ya usa el lector de documentos. Para el doble volumen
 * arriba, si hay internet, la captura se envía a Gemini para describir sus imágenes; si no hay
 * conexión o la API falla, se queda en el teléfono con ML Kit. Ver la nota de la decisión 0005 más
 * abajo, junto a la lectura de pantalla.
 */
@AndroidEntryPoint
class AccesibilidadServicio : AccessibilityService() {

    @Inject lateinit var voiceEngine: VoiceEngine

    @Inject lateinit var volumeControl: VolumeControl

    @Inject lateinit var repositorio: AccesibilidadRepository

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Doble pulsación de volumen: solo volumen abajo dos veces hace algo. */
    private val detectorVolumen = DetectorPulsacionesVolumen()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Se crea la primera vez que se usa y se cierra al destruir el servicio. */
    @Volatile private var reconocedorTexto: TextRecognizer? = null

    /** Etiquetador de imágenes para el doble volumen arriba; se crea solo cuando hace falta. */
    @Volatile private var etiquetadorImagenes: ImageLabeler? = null

    /** Describe las imágenes de la captura con Gemini cuando hay internet. */
    private val geminiPantalla = GeminiPantalla()

    /** Evita dos capturas al mismo tiempo si se pulsa el gesto dos veces seguidas. */
    @Volatile private var capturandoPantalla = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        // La configuración base vive en res/xml/accessibility_service_config.xml; aquí solo se asegura
        // lo que hace falta para la exploración táctil, sin depender de que el XML cambie.
        serviceInfo?.let { info ->
            // Sin FLAG_REQUEST_TOUCH_EXPLORATION_MODE: los deslizamientos de un dedo son de Android.
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            info.flags = info.flags and
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
            serviceInfo = info
            val filtraTeclas =
                info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
            val exploraTactil =
                info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0
            Log.i(
                TAG,
                "Servicio conectado; filtraTeclas=$filtraTeclas exploraTactil=$exploraTactil " +
                    "flags=0x${info.flags.toString(16)}",
            )
        } ?: Log.w(TAG, "Servicio conectado sin serviceInfo")
    }

    /** Avisa el título cuando cambia la ventana; la lectura por gesto va por [capturarYLeerPantalla]. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            // Ventana nueva (otra app o una pantalla de CECAPI): se anuncia el título. De la propia app
            // no, porque CECAPI ya narra sus pantallas por su cuenta y se oiría dos veces.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                if (event.packageName?.toString() != packageName) anunciarVentana(event)

            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    /**
     * Botones de volumen: abajo dos veces lee el texto visible; arriba dos veces describe solo las
     * imágenes. Las dos acciones toman una captura temporal en memoria.
     *
     * Siempre se devuelve false para que el sistema reciba la tecla y el volumen siga cambiando
     * normal. Los eventos repetidos (dedo mantenido) no cuentan como una pulsación nueva.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false

        val tecla = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> TeclaVolumen.BAJAR
            KeyEvent.KEYCODE_VOLUME_UP -> TeclaVolumen.SUBIR
            else -> return false
        }

        val esDoble = detectorVolumen.registrar(tecla, SystemClock.uptimeMillis())
        Log.d(TAG, "Tecla de volumen $tecla; doble=$esDoble")
        if (esDoble) {
            // La captura/lectura tarda más que el anuncio del nivel: se cancela desde ahora.
            volumeControl.suprimirAnuncio()
            when (tecla) {
                TeclaVolumen.BAJAR -> capturarYLeerPantalla()
                TeclaVolumen.SUBIR -> capturarYDescribirImagenes()
            }
        }
        return false
    }

    /** Anuncia el título de una ventana nueva, si trae texto. */
    private fun anunciarVentana(event: AccessibilityEvent) {
        val titulo = event.text.joinToString(" ").trim()
        if (titulo.isNotEmpty()) voiceEngine.speak(titulo)
    }

    override fun onGesture(gestureId: Int): Boolean {
        val gesto = gestoDe(gestureId) ?: return false
        alcance.launch {
            val perfil = repositorio.perfilActivo()
            ejecutar(GestorGestos.accionesDe(perfil, gesto))
        }
        return true
    }

    /**
     * Ejecuta las acciones. Si alguna es de control ("silencio" o "para", decisión 0006) se ejecuta
     * SOLO esa y se descartan las demás: callar o detener gana sobre cualquier otra acción del gesto.
     */
    private fun ejecutar(acciones: List<Accion>) {
        if (acciones.isEmpty()) return
        GestorGestos.accionGanadora(acciones)?.let {
            ejecutarUna(it)
            return
        }
        acciones.forEach(::ejecutarUna)
    }

    private fun ejecutarUna(accion: Accion) {
        Log.d(TAG, "Acción de gesto: ${accion.tipo.codigo}${accion.parametro?.let { " ($it)" }.orEmpty()}")
        when (accion.tipo) {
            TipoAccion.HABLAR -> accion.parametro?.let(voiceEngine::speak)
            TipoAccion.SILENCIO -> voiceEngine.mute()
            TipoAccion.DETENER -> voiceEngine.stopByUser()
            TipoAccion.VIBRAR -> vibrar()
            TipoAccion.VOLVER_AL_MENU -> abrirCecapi()
            TipoAccion.LEER_PANTALLA -> leerPantalla()
            TipoAccion.COMANDO_VOZ -> ejecutarComandoDeVoz(accion.parametro)
            TipoAccion.NADA -> Unit
        }
    }

    /** "silencio" y "para" primero; cualquier otro comando todavía no se puede reenviar al motor de voz. */
    private fun ejecutarComandoDeVoz(frase: String?) {
        when {
            GestorGestos.esSilencio(frase) -> voiceEngine.mute()
            GestorGestos.esDetener(frase) -> voiceEngine.stopByUser()
            else -> voiceEngine.speak(PENDIENTE_COMANDO)
        }
    }

    /**
     * Lee la pantalla completa desde el árbol de accesibilidad (acción del perfil de gestos).
     * El doble volumen usa la captura temporal + OCR de [capturarYLeerPantalla].
     */
    private fun leerPantalla() {
        leerEnVozAlta(LectorPantallaAndroid.leer(rootInActiveWindow))
    }

    /**
     * Lee un texto de pantalla con la voz un poco más pausada y con las unidades y abreviaturas
     * convertidas a palabras. Así una etiqueta con "28\"", "Hz" o "$" no se lee como símbolos
     * sueltos ni dice "dólares" cuando en México son pesos.
     */
    private fun leerEnVozAlta(texto: String) {
        val preparado = NormalizadorLectura.normalizar(texto)
        if (preparado.isBlank()) return
        voiceEngine.speak(preparado, speedFactor = VELOCIDAD_LECTURA)
    }

    /**
     * Doble volumen abajo: toma una captura temporal en memoria y lee con OCR solo lo que se ve.
     * En Android 11 o superior usa la captura de accesibilidad; en versiones anteriores cae al
     * árbol de accesibilidad. La captura no se guarda, no se muestra y no sale del teléfono.
     */
    private fun capturarYLeerPantalla() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            leerTextoDePantalla()
            return
        }
        if (capturandoPantalla) return
        capturandoPantalla = true

        capturarPantalla(
            alCapturar = { bitmap ->
                alcance.launch {
                    try {
                        val texto = withContext(Dispatchers.Default) { reconocerTexto(bitmap) }
                        if (texto.isNullOrBlank()) {
                            Log.d(TAG, "Doble volumen abajo: la captura no tiene texto legible")
                        } else {
                            Log.d(TAG, "Doble volumen abajo: leyendo ${texto.length} caracteres de la captura")
                            leerEnVozAlta(texto)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "No se pudo leer la captura", e)
                    } finally {
                        bitmap.recycle()
                        capturandoPantalla = false
                    }
                }
            },
            alFallar = { capturandoPantalla = false },
        )
    }

    /**
     * Doble volumen arriba: describe las imágenes de la pantalla. Con internet lo hace Gemini, con el
     * mismo criterio que la cámara (1 principal + 2 secundarios). Si no hay conexión, la API falla o
     * Gemini no ve imágenes claras, se reutiliza la captura, se tapan las zonas con texto y se
     * etiqueta lo que queda con ML Kit, sin salir del teléfono. En Android 10 o anterior no hay
     * captura de pantalla y no se hace nada.
     */
    private fun capturarYDescribirImagenes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "Doble volumen arriba: este Android no permite captura de pantalla")
            return
        }
        if (capturandoPantalla) return
        capturandoPantalla = true

        capturarPantalla(
            alCapturar = { bitmap ->
                alcance.launch {
                    try {
                        // Con internet manda Gemini, igual que en la cámara normal.
                        val deGemini = if (hayInternet()) {
                            geminiPantalla.reconocer(bitmap)
                        } else {
                            ResultadoPantallaGemini(null, emptyList(), MotivoPantallaGemini.SIN_CONEXION)
                        }
                        if (deGemini.hayObjetos) {
                            val frase = GeminiPantalla.frase(deGemini)
                            Log.d(TAG, "Doble volumen arriba: Gemini -> $frase")
                            if (frase.isNotBlank()) leerEnVozAlta(frase)
                            return@launch
                        }
                        Log.d(
                            TAG,
                            "Doble volumen arriba: Gemini no describió la pantalla (${deGemini.motivo}); uso ML Kit",
                        )

                        val zonas = withContext(Dispatchers.Default) { reconocerZonasTexto(bitmap) }
                        val etiquetas = withContext(Dispatchers.Default) {
                            val paraEtiquetar = taparZonas(bitmap, zonas)
                            try {
                                reconocerEtiquetas(paraEtiquetar)
                            } finally {
                                if (paraEtiquetar !== bitmap) paraEtiquetar.recycle()
                            }
                        }
                        val frase = DescriptorImagenes.descripcion(zonas, etiquetas, bitmap.width, bitmap.height)
                        if (frase.isNullOrBlank()) {
                            Log.d(TAG, "Doble volumen arriba: sin imágenes con confianza suficiente")
                        } else {
                            Log.d(TAG, "Doble volumen arriba: leyendo ${frase.length} caracteres")
                            leerEnVozAlta(frase)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "No se pudieron describir las imágenes de la captura", e)
                    } finally {
                        bitmap.recycle()
                        capturandoPantalla = false
                    }
                }
            },
            alFallar = { capturandoPantalla = false },
        )
    }

    /** Pide la captura y reintenta una vez si el sistema acaba de dar otra o la ventana cambió. */
    private fun capturarPantalla(
        intentos: Int = 0,
        pantallaCompleta: Boolean = false,
        alCapturar: (Bitmap) -> Unit,
        alFallar: () -> Unit,
    ) {
        try {
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val bitmap = bitmapDeCaptura(screenshot)
                    if (bitmap == null) {
                        Log.w(TAG, "La captura no se pudo convertir en imagen")
                        alFallar()
                        return
                    }
                    alCapturar(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT &&
                        intentos == 0
                    ) {
                        mainHandler.postDelayed({
                            capturarPantalla(intentos + 1, pantallaCompleta, alCapturar, alFallar)
                        }, 400)
                        return
                    }
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW &&
                        intentos == 0 && !pantallaCompleta
                    ) {
                        // La ventana activa dejó de ser válida: se intenta con la pantalla completa.
                        capturarPantalla(intentos + 1, pantallaCompleta = true, alCapturar, alFallar)
                        return
                    }
                    Log.w(TAG, "No se pudo tomar la captura de pantalla: $errorCode")
                    alFallar()
                }
            }

            val executor = ContextCompat.getMainExecutor(this)
            // En Android 14+ se captura la ventana activa para que el panel de volumen no tape el texto.
            val ventana = windows.firstOrNull { it.isActive }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && ventana != null && !pantallaCompleta) {
                takeScreenshotOfWindow(ventana.id, executor, callback)
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al pedir la captura de pantalla", e)
            alFallar()
        }
    }

    /** Comprueba si hay una red con internet validada, para no esperar el tiempo de conexión de Gemini. */
    private fun hayInternet(): Boolean = try {
        val administrador = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val red = administrador.activeNetwork ?: return false
        val capacidades = administrador.getNetworkCapabilities(red) ?: return false
        capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        // Si no se puede consultar, se intenta Gemini; su tiempo de espera decide.
        true
    }

    /**
     * Tapa con negro las zonas de texto de una copia para que el etiquetador no describa letras.
     * La captura original no se modifica.
     */
    private fun taparZonas(bitmap: Bitmap, zonas: List<ZonaCaptura>): Bitmap {
        if (zonas.isEmpty()) return bitmap
        val copia = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
        val canvas = Canvas(copia)
        val pintura = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        zonas.forEach { zona ->
            canvas.drawRect(
                zona.izquierda.toFloat(),
                zona.arriba.toFloat(),
                zona.derecha.toFloat(),
                zona.abajo.toFloat(),
                pintura,
            )
        }
        return copia
    }

    /** Solo las cajas de los bloques de texto, para taparlas antes de etiquetar. */
    private suspend fun reconocerZonasTexto(bitmap: Bitmap): List<ZonaCaptura> {
        val reconocedor = reconocedorTexto ?: TextRecognition
            .getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .also { reconocedorTexto = it }

        val escalada = escalarParaOcr(bitmap)
        val factor = if (escalada.width == bitmap.width) 1f else escalada.width.toFloat() / bitmap.width
        val resultado = try {
            reconocedor.process(InputImage.fromBitmap(escalada, 0)).await()
        } finally {
            if (escalada !== bitmap) escalada.recycle()
        }
        val zonas = resultado.textBlocks.mapNotNull { bloque ->
            val caja = bloque.boundingBox ?: return@mapNotNull null
            ZonaCaptura(
                izquierda = (caja.left / factor).toInt(),
                arriba = (caja.top / factor).toInt(),
                derecha = (caja.right / factor).toInt(),
                abajo = (caja.bottom / factor).toInt(),
            )
        }
        Log.d(TAG, "Doble volumen arriba: ${resultado.textBlocks.size} bloques, ${zonas.size} zonas de texto")
        return zonas
    }

    /** Etiquetas de imagen en el teléfono. Devuelve todas las que pasan el umbral bajo del cliente. */
    private suspend fun reconocerEtiquetas(bitmap: Bitmap): List<EtiquetaImagen> {
        val etiquetador = etiquetadorImagenes ?: ImageLabeling
            .getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(UMBRAL_ETIQUETADOR).build())
            .also { etiquetadorImagenes = it }

        val etiquetas: List<ImageLabel> = etiquetador.process(InputImage.fromBitmap(bitmap, 0)).await()
        Log.d(
            TAG,
            "Doble volumen arriba: etiquetas " +
                etiquetas.joinToString { "${it.text} (${"%.2f".format(it.confidence)})" },
        )
        return etiquetas.map { EtiquetaImagen(it.text, it.confidence) }
    }

    /** Convierte la captura en un bitmap de software y libera el búfer de hardware. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun bitmapDeCaptura(screenshot: AccessibilityService.ScreenshotResult): Bitmap? {
        val hardware = screenshot.hardwareBuffer
        return try {
            val bitmapHardware = Bitmap.wrapHardwareBuffer(hardware, screenshot.colorSpace) ?: return null
            val bitmap = bitmapHardware.copy(Bitmap.Config.ARGB_8888, false)
            bitmapHardware.recycle()
            bitmap
        } finally {
            hardware.close()
        }
    }

    /**
     * Las capturas de ventanas suelen venir a 1080 px de ancho. Duplicarlas ayuda al OCR con letras
     * pequeñas de publicaciones o imágenes. Si la pantalla ya es muy grande, se deja como está.
     */
    private fun escalarParaOcr(bitmap: Bitmap): Bitmap {
        if (bitmap.width >= 1200) return bitmap
        return runCatching {
            Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, true)
        }.getOrDefault(bitmap)
    }

    /** OCR en el teléfono. Devuelve null si no hay texto legible. */
    private suspend fun reconocerTexto(bitmap: Bitmap): String? {
        val reconocedor = reconocedorTexto ?: TextRecognition
            .getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .also { reconocedorTexto = it }

        val escalada = escalarParaOcr(bitmap)
        val resultado = try {
            reconocedor.process(InputImage.fromBitmap(escalada, 0)).await()
        } finally {
            if (escalada !== bitmap) escalada.recycle()
        }
        Log.d(TAG, "OCR: ${resultado.textBlocks.size} bloques, ${resultado.text.length} caracteres reconocidos")
        val bloques = resultado.textBlocks.mapNotNull { bloque ->
            val texto = bloque.text.trim()
            if (texto.isEmpty()) return@mapNotNull null
            val caja = bloque.boundingBox
            BloqueCaptura(
                texto = texto,
                arriba = caja?.top ?: Int.MAX_VALUE,
                izquierda = caja?.left ?: Int.MAX_VALUE,
                abajo = caja?.bottom ?: 0,
            )
        }
        val texto = LectorCaptura.textoParaLeer(bloques)
        Log.d(TAG, "OCR: texto para leer ${texto?.length ?: 0} caracteres en ${bloques.size} bloques legibles")
        return texto
    }

    /** Respaldo para Android 10 o anterior: lee el árbol de accesibilidad, sin captura. */
    private fun leerTextoDePantalla() {
        val lectura = LectorPantallaAndroid.leerTexto(rootInActiveWindow)
        if (lectura == null) {
            Log.d(TAG, "Doble volumen abajo: la pantalla no tiene texto")
            return
        }
        Log.d(TAG, "Doble volumen abajo: leyendo ${lectura.length} caracteres del árbol")
        leerEnVozAlta(lectura)
    }

    private fun abrirCecapi() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private fun vibrar() {
        val vibrador = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrador.hasVibrator()) return
        vibrador.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        alcance.cancel()
        runCatching { reconocedorTexto?.close() }
        reconocedorTexto = null
        runCatching { etiquetadorImagenes?.close() }
        etiquetadorImagenes = null
        super.onDestroy()
    }

    /** El entero que manda Android a [onGesture] traducido al gesto que entienden los perfiles. */
    private fun gestoDe(gestureId: Int): Gesto? = when (gestureId) {
        AccessibilityService.GESTURE_SWIPE_UP -> Gesto.DESLIZAR_ARRIBA
        AccessibilityService.GESTURE_SWIPE_DOWN -> Gesto.DESLIZAR_ABAJO
        AccessibilityService.GESTURE_SWIPE_LEFT -> Gesto.DESLIZAR_IZQUIERDA
        AccessibilityService.GESTURE_SWIPE_RIGHT -> Gesto.DESLIZAR_DERECHA
        AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN -> Gesto.DESLIZAR_ARRIBA_ABAJO
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT -> Gesto.DESLIZAR_IZQUIERDA_DERECHA
        AccessibilityService.GESTURE_DOUBLE_TAP -> Gesto.TOQUE_DOBLE
        AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD -> Gesto.TOQUE_DOBLE_MANTENER
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) gestoDeApi30(gestureId) else null
    }

    /** Gestos con dos dedos, que solo existen desde Android 11 (API 30). */
    private fun gestoDeApi30(gestureId: Int): Gesto? = when (gestureId) {
        AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP -> Gesto.DOS_DEDOS_TOQUE
        AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP -> Gesto.DOS_DEDOS_DOBLE_TOQUE
        AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP -> Gesto.DOS_DEDOS_TRIPLE_TOQUE
        AccessibilityService.GESTURE_2_FINGER_SWIPE_UP -> Gesto.DOS_DEDOS_ARRIBA
        AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN -> Gesto.DOS_DEDOS_ABAJO
        AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT -> Gesto.DOS_DEDOS_IZQUIERDA
        AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT -> Gesto.DOS_DEDOS_DERECHA
        else -> null
    }

    private companion object {
        const val TAG = "CecapiAccesibilidad"

        /** La lectura de pantalla va un 10% más lenta que la voz normal de la app. */
        const val VELOCIDAD_LECTURA = 0.9f

        /** Umbral bajo del etiquetador: DescriptorImagenes aplica el umbral definitivo. */
        const val UMBRAL_ETIQUETADOR = 0.5f

        const val PENDIENTE_COMANDO =
            "Ese comando todavía no se puede ejecutar desde un gesto. Ábreme y dímelo con la voz."
    }
}

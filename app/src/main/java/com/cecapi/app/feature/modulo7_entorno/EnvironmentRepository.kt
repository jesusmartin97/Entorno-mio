package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TfliteObjectDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

data class EnvironmentResult(
    val escaneoId: Long?,
    val descripcion: String,
    val etiquetas: List<String>,
    /** Solo cuando la foto fue de billetes: lo que se leyó, para que la pantalla pueda llevar la cuenta. */
    val billetes: ResultadoBillete? = null,
    /** Solo cuando la foto fue de una moneda: lo que se reconoció, para que la pantalla pueda llevar la cuenta. */
    val monedas: ResultadoMoneda? = null,
)

/** Un objeto ya nombrado en español, con su color y dónde está en la foto. */
private data class ObjetoDescrito(
    val nombre: NombreEs,
    val color: String?,
    val posicion: String?,
    val confianza: Float,
    val area: Int,
    /** El modelo no está muy seguro: se dice "posiblemente" para no afirmar de más. */
    val dudoso: Boolean = false,
) {
    /** "silla negro" (sin artículo ni posición), para guardar en la base. */
    val etiqueta: String get() = if (color != null) "${nombre.texto} de color $color" else nombre.texto

    /** "una silla de color negro a la izquierda", para decirlo en voz alta. */
    val frase: String
        get() = buildString {
            if (dudoso) append("posiblemente ")
            append(nombre.conArticulo)
            if (color != null) append(" de color ").append(color)
            if (posicion != null) append(" ").append(posicion)
        }
}

/**
 * Describe lo que hay enfrente sin internet con un detector local de objetos y un etiquetador de respaldo.
 */
@Singleton
class EnvironmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val escaneoDao: EscaneoEntornoDao,
    private val objetoDao: ObjetoDetectadoDao,
    private val descripcionDao: DescripcionEntornoDao,
) {
    // Detector de objetos COCO: devuelve clases concretas y una caja por objeto, no solo
    // categorías amplias como "comida" o "instrumento musical".
    private val detectorDetallado by lazy {
        val opciones = TfliteObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(MAX_OBJETOS)
            .setScoreThreshold(CONFIANZA_MINIMA)
            .build()
        TfliteObjectDetector.createFromFileAndOptions(context, MODELO_DE_OBJETOS, opciones)
    }

    // Etiquetador: dice QUÉ es cada objeto (silla, laptop, botella...). Modelo incluido en la app.
    private val etiquetador = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(CONFIANZA_RESPALDO)
            .build(),
    )

    // Etiquetador con umbral bajo, solo para sospechar si una foto normal trae un billete (papel, dinero).
    private val etiquetadorBilletes = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build(),
    )

    // Lector de texto para los números y palabras del billete.
    private val reconocedorTexto = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Reconocimiento de monedas con Gemini. Solo se usa cuando la persona lo pide por voz.
    private val geminiMoneda = GeminiMoneda()

    // Descripción de objetos de la cámara normal con Gemini. Si no hay internet, se usa el detector local.
    private val geminiEntorno = GeminiEntorno()

    // Intérprete TFLite 2.17 (nuevo, compatible con FULLY_CONNECTED v12) para el modelo de monedas.
    private val interpreteMonedas: Interpreter? by lazy {
        try {
            val archivo = context.assets.openFd(MODELO_DE_MONEDAS)
            val canal = archivo.createInputStream().channel
            val buffer = canal.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, archivo.startOffset, archivo.declaredLength)
            canal.close(); archivo.close()
            val opciones = Interpreter.Options()
            val interpreter = Interpreter(buffer, opciones)
            Log.i(TAG_MONEDA, "Modelo de monedas cargado desde assets sin excepción: $MODELO_DE_MONEDAS")
            interpreter
        } catch (e: Exception) {
            Log.w(TAG_MONEDA, "No se pudo cargar el modelo de monedas ($MODELO_DE_MONEDAS).", e)
            null
        }
    }

    fun observeRecent(usuarioId: Long): Flow<List<EscaneoEntornoEntity>> = escaneoDao.observeRecent(usuarioId)

    /**
     * The camera is free for anyone: with no [usuarioId] (nobody signed in) the description is still said
     * out loud, it just is not saved to a history that would have nowhere to belong.
     */
    suspend fun processCapturedPhoto(
        usuarioId: Long?,
        imageUri: Uri,
        rutaImagen: String,
        buscarBillete: Boolean = false,
        buscarMoneda: Boolean = false,
    ): Result<EnvironmentResult> {
        var etapa = "validar la foto"
        return try {
            val inicio = SystemClock.elapsedRealtime()

            val file = File(rutaImagen)
            if (!file.exists()) {
                return Result.failure(Exception("La imagen no existe en el disco."))
            }

            // Una sola imagen, ya girada y reducida: detección, recortes y color usan las mismas coordenadas.
            val bitmap = cargarBitmapOrientado(rutaImagen)
                ?: return Result.failure(Exception("No pude abrir la imagen."))

            // "Qué billete es": solo se lee el billete. No se corre el detector de objetos, que no conoce billetes
            // y solo metería ruido ("veo un libro") en la respuesta.
            if (buscarBillete) {
                etapa = "leer el billete"
                val resultado = leerBillete(bitmap)
                bitmap.recycle()
                return Result.success(guardarBillete(usuarioId, rutaImagen, resultado, inicio))
            }

            // "Qué moneda es": solo se clasifica la moneda, sin detector de objetos ni lectura de billetes.
            if (buscarMoneda) {
                etapa = "reconocer la moneda"
                val resultado = reconocerMonedaConRespaldo(bitmap)
                bitmap.recycle()
                return Result.success(guardarMoneda(usuarioId, rutaImagen, resultado, inicio))
            }

            // Foto normal ("qué hay enfrente"): si en la foto hay un billete cuya denominación se lee con claridad,
            // se dice de cuánto es sin que la persona tenga que pedirlo aparte.
            etapa = "revisar si hay un billete"
            val billeteAutomatico = detectarBilleteAutomatico(bitmap)
            if (billeteAutomatico != null) {
                bitmap.recycle()
                return Result.success(guardarBillete(usuarioId, rutaImagen, billeteAutomatico, inicio))
            }

            // En una foto normal, si el modelo de monedas reconoce una moneda con seguridad, también se dice.
            // Así no hace falta activar el comando "qué moneda es"; se comporta como un objeto del entorno.
            etapa = "revisar si hay una moneda"
            val monedaAutomatica = try { reconocerMoneda(bitmap, automatico = true) } catch (e: Exception) { null }
            if (monedaAutomatica != null && monedaAutomatica.esSegura) {
                bitmap.recycle()
                return Result.success(guardarMoneda(usuarioId, rutaImagen, monedaAutomatica, inicio))
            }

            etapa = "detectar objetos"
            // Si el modelo detallado falla en algún teléfono, intenta el etiquetador de respaldo
            // en vez de abandonar el análisis completo de la foto.
            val detecciones = try {
                detectorDetallado.detect(TensorImage.fromBitmap(bitmap))
            } catch (e: Exception) {
                Log.e(TAG, "Falló el detector detallado; probaré el etiquetador de respaldo.", e)
                emptyList()
            }
            val descritos = detecciones
                .mapNotNull { describirObjetoDetectado(it, bitmap) }
                .sortedByDescending { it.confianza }
                .take(MAX_OBJETOS)

            // Si el detector no encontró cajas (por ejemplo, un objeto muy de cerca),
            // se etiqueta la foto completa y se dice lo principal sin posición.
            val principal: ObjetoDescrito? =
                if (descritos.isEmpty()) {
                    etapa = "etiquetar la foto completa"
                    describirFotoCompleta(bitmap)
                } else {
                    null
                }

            val finales = when {
                descritos.isNotEmpty() -> descritos
                    .sortedBy { ordenHorizontal(it.posicion) }
                principal != null -> listOf(principal)
                else -> emptyList()
            }

            // Si no se identificó ningún objeto pero toda la foto es casi de un solo color (una hoja, una tela, una
            // pared lisa), se dice el color en lugar de no decir nada.
            val colorLiso = if (finales.isEmpty()) NombreColor.dominante(pixelesDelCentro(bitmap), 0.6f) else null

            val descripcion = when {
                finales.isEmpty() && colorLiso != null ->
                    "No identifico un objeto, pero veo algo de color ${colorLiso.nombre}. " +
                        "Acércate o centra el objeto si quieres que lo describa."
                finales.isEmpty() ->
                    "No logré identificar objetos claros enfrente. Intenta centrar el objeto, acercarte un poco más o mejorar la iluminación."
                descritos.isEmpty() ->
                    "Veo principalmente ${finales.first().frase}."
                else ->
                    "Veo ${unir(finales.map { it.frase })}."
            }

            bitmap.recycle()

            val escaneoId = usuarioId?.let { id ->
                etapa = "guardar el resultado"
                val nuevoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = id, rutaImagen = rutaImagen))
                if (finales.isNotEmpty()) {
                    objetoDao.insertAll(
                        finales.map {
                            ObjetoDetectadoEntity(
                                escaneoId = nuevoId,
                                etiqueta = it.etiqueta,
                                confianza = it.confianza,
                            )
                        },
                    )
                }
                descripcionDao.insert(DescripcionEntornoEntity(escaneoId = nuevoId, textoDescripcion = descripcion))
                nuevoId
            }

            Log.d(TAG, "Descripción lista en ${SystemClock.elapsedRealtime() - inicio} ms: $descripcion")

            Result.success(EnvironmentResult(escaneoId, descripcion, finales.map { it.etiqueta }))
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo analizar la foto durante la etapa: $etapa", e)
            Result.failure(e)
        }
    }

    /**
     * Analiza una foto que ya está en memoria para el Asistente del Entorno. No escribe archivos ni
     * filas en Room: devuelve la frase y se olvida de la imagen al terminar. La calidad se revisa
     * antes de describir; si la foto está oscura o movida, se avisa y no se describe nada.
     *
     * Reutiliza el mismo detector, el mismo etiquetador y el mismo `describirFotoCompleta` que la
     * foto normal. Los billetes y monedas se siguen reconociendo, pero sin guardar historial.
     */
    suspend fun analizarFotoEnMemoria(
        bitmap: Bitmap,
        buscarBillete: Boolean = false,
        buscarMoneda: Boolean = false,
    ): Result<EnvironmentResult> = withContext(Dispatchers.Default) {
        try {
            if (buscarBillete) {
                val resultado = leerBillete(bitmap)
                val etiquetas = etiquetasDeBillete(resultado)
                return@withContext Result.success(
                    EnvironmentResult(null, fraseBillete(resultado), etiquetas, billetes = resultado),
                )
            }
            // "Qué moneda es" y "guarda la moneda": con internet manda Gemini, como antes. Si no hay
            // conexión (o falla la API), se usa el modelo local para que el comando siga funcionando.
            if (buscarMoneda) {
                return@withContext Result.success(fotoDeMoneda(reconocerMonedaConRespaldo(bitmap)))
            }

            val problema = CalidadFoto.evaluar(matrizGrises(bitmap))
            if (problema != null) {
                Log.d(TAG, "Foto descartada por calidad: ${problema.name}")
                return@withContext Result.success(
                    EnvironmentResult(null, CalidadFoto.mensaje(problema), emptyList()),
                )
            }

            val detecciones = try {
                detectorDetallado.detect(TensorImage.fromBitmap(bitmap))
            } catch (e: Exception) {
                Log.e(TAG, "Falló el detector en memoria; probaré el etiquetador de respaldo.", e)
                emptyList()
            }
            registrarDetecciones(detecciones, bitmap)

            val billete = detectarBilleteAutomatico(bitmap)
            if (billete != null) {
                return@withContext Result.success(
                    EnvironmentResult(null, fraseBillete(billete), etiquetasDeBillete(billete), billetes = billete),
                )
            }
            // Las monedas no se reconocen solas en una foto normal: solo con "qué moneda es" o
            // "guarda la moneda". Así Gemini no recibe fotos que nadie pidió.

            // Las personas tienen su propia ruta: no se describen como objetos ni se inventan caras.
            val personas = CamaraPersonas.personas(
                detecciones.mapNotNull { personaDe(it, bitmap) },
            )
            if (personas.isNotEmpty()) {
                val principal = personas.first()
                val encuadre = CamaraPersonas.encuadre(principal.caja)
                val ropa = if (encuadre == null) {
                    ropaDe(bitmap, principal.caja)
                } else {
                    RopaCamara(arriba = null, abajo = null)
                }
                val nombre = if (encuadre == null) leerGafete(bitmap, principal.caja) else null
                val frase = CamaraPersonas.frasePersonas(
                    cantidad = personas.size,
                    posicion = CamaraPersonas.posicion(principal.caja),
                    encuadre = encuadre,
                    ropa = ropa,
                    nombre = nombre,
                ) ?: CamaraPersonas.FRASE_SIN_RECONOCIMIENTO
                Log.d(
                    TAG,
                    "Personas en memoria: ${personas.size}; encuadre=${encuadre?.name ?: "ok"}; " +
                        "gafete=${if (nombre != null) "leido" else "no"}; sin guardar.",
                )
                return@withContext Result.success(
                    EnvironmentResult(null, frase, listOf("persona")),
                )
            }

            // Cámara normal: con internet, Gemini describe hasta 3 objetos (1 principal + 2 secundarios).
            // Si no hay conexión, la API falla o no ve objetos, se sigue con el detector local de abajo.
            val deGemini = if (hayInternet()) {
                geminiEntorno.reconocer(bitmap)
            } else {
                ResultadoEntornoGemini(null, emptyList(), MotivoEntornoGemini.SIN_CONEXION)
            }
            if (deGemini.hayObjetos) {
                val frase = GeminiEntorno.frase(deGemini)
                val etiquetas = GeminiEntorno.etiquetas(deGemini)
                Log.d(TAG, "Descripción con Gemini: $frase")
                return@withContext Result.success(EnvironmentResult(null, frase, etiquetas))
            }
            Log.d(TAG, "Gemini no describió el entorno (${deGemini.motivo}); sigo con el detector local.")

            // Pantallas y periféricos se resuelven en conjunto, con una segunda opinión de ML Kit
            // solo cuando el detector ya vio algo de ese grupo. Así no se duplica la pantalla.
            val senalesPantalla = detecciones.mapNotNull { senalDePantalla(it, bitmap) }
            val senalesMlKit = if (senalesPantalla.isNotEmpty()) {
                senalesMlKitDePantalla(bitmap)
            } else {
                emptyList()
            }
            val pantalla = TraductorPantallas.describir(senalesPantalla + senalesMlKit)
            if (Log.isLoggable(TAG_DETECCIONES, Log.DEBUG)) {
                Log.d(
                    TAG_DETECCIONES,
                    "resumen: senales=${senalesPantalla.size} mlkit=${senalesMlKit.size} " +
                        "con-pantalla=${pantalla != null}",
                )
            }

            val hayPantallaEnFoto = contienePantalla(detecciones)
            val otros = detecciones
                .filterNot { esPersona(it) || esSenalDePantalla(it) }
                .filter { conservarObjeto(it, bitmap, hayPantallaEnFoto) }
                .mapNotNull { describirObjetoDetectadoCamara(it, bitmap) }
                .sortedByDescending { it.confianza }
                .take(CamaraPersonas.MAX_OBJETOS)

            // Si no hay grupo de pantalla ni otros objetos, se usa el etiquetador de respaldo, pero
            // sin dejar que vuelva a nombrar televisores, monitores o computadoras por su cuenta.
            val relleno = if (pantalla == null && otros.isEmpty()) {
                listOfNotNull(
                    describirFotoCompleta(
                        bitmap,
                        excluirPersonas = true,
                        excluirPantallas = true,
                    ),
                )
            } else {
                emptyList()
            }
            val finales = otros + relleno
            val fraseOtros = CamaraPersonas.fraseObjetos(finales.map { it.frase })
            val frase = when {
                pantalla != null && fraseOtros == null -> pantalla.frase
                pantalla != null ->
                    "${pantalla.frase} También ${fraseOtros!!.replaceFirstChar { it.lowercaseChar() }}"
                fraseOtros != null -> fraseOtros
                else -> ReglasEntorno.FRASE_SIN_RECONOCIMIENTO
            }
            val etiquetas = buildList {
                pantalla?.let { add(it.etiqueta) }
                addAll(finales.map { it.etiqueta })
            }
            Log.d(TAG, "Análisis en memoria: ${etiquetas.size} etiquetas, sin guardar la foto.")
            Result.success(EnvironmentResult(null, frase, etiquetas))
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo analizar la foto en memoria", e)
            Result.failure(e)
        }
    }

    /** Etiquetas del billete sin escribir nada en la base. */
    private fun etiquetasDeBillete(resultado: ResultadoBillete): List<String> =
        resultado.todas.map {
            val n = resultado.cantidadDe(it)
            if (n > 1) "$n billetes de ${it.valor} pesos" else "billete de ${it.valor} pesos"
        }.ifEmpty { listOf("billete") }

    /** Resultado de una moneda listo para decir, sin guardar historial. */
    private fun fotoDeMoneda(resultado: ResultadoMoneda): EnvironmentResult {
        val d = resultado.denominacion
        val etiqueta = if (resultado.esSegura && d != null) {
            "moneda de ${d.valor} pesos"
        } else {
            "moneda sin identificar"
        }
        return EnvironmentResult(
            escaneoId = null,
            descripcion = MonedaMx.frase(resultado),
            etiquetas = listOf(etiqueta),
            monedas = resultado,
        )
    }

    /**
     * Diagnóstico interno del Asistente del Entorno: escribe en Logcat la etiqueta, la confianza y el
     * tamaño de cada caja, en porcentajes de la foto. Sirve para decidir reglas con datos reales.
     * Nunca imprime texto leído, nombres de personas ni rutas: solo nombres de clases del modelo.
     */
    private fun registrarDetecciones(detecciones: List<Detection>, bitmap: Bitmap) {
        if (!Log.isLoggable(TAG_DETECCIONES, Log.DEBUG)) return
        val ancho = bitmap.width.toFloat().coerceAtLeast(1f)
        val alto = bitmap.height.toFloat().coerceAtLeast(1f)
        detecciones.forEachIndexed { indice, deteccion ->
            val categoria = deteccion.categories.maxByOrNull { it.score }
            val caja = deteccion.boundingBox
            val medidas = if (caja == null) {
                "caja=sin-caja"
            } else {
                val anchoCaja = caja.width() / ancho
                val altoCaja = caja.height() / alto
                "ancho=${porcentaje(anchoCaja)} alto=${porcentaje(altoCaja)} " +
                    "area=${porcentaje(anchoCaja * altoCaja)}"
            }
            Log.d(
                TAG_DETECCIONES,
                "d=$indice etiqueta=${categoria?.label ?: "?"} " +
                    "conf=${"%.4f".format(categoria?.score ?: 0f)} $medidas",
            )
        }
    }

    private fun porcentaje(fraccion: Float): String = "%.1f%%".format(fraccion * 100f)

    /** Usa la caja del detector y la posición de la cámara ("a tu izquierda", "al centro", "a tu derecha"). */
    private fun describirObjetoDetectadoCamara(deteccion: Detection, bitmap: Bitmap): ObjetoDescrito? {
        val base = describirObjetoDetectado(deteccion, bitmap) ?: return null
        val caja = deteccion.boundingBox ?: return base
        return base.copy(posicion = CamaraPersonas.posicion(cajaNormalizada(caja, bitmap)))
    }

    /** Convierte una detección del detector en una persona, solo si es "person" y supera la confianza mínima. */
    private fun personaDe(deteccion: Detection, bitmap: Bitmap): PersonaCamara? {
        val categoria = deteccion.categories.maxByOrNull { it.score } ?: return null
        if (!categoria.label.equals("person", ignoreCase = true)) return null
        if (categoria.score < CamaraPersonas.CONFIANZA_MINIMA_PERSONA) return null
        val caja = deteccion.boundingBox ?: return null
        val normalizada = cajaNormalizada(caja, bitmap)
        if (normalizada.area <= 0f) return null
        return PersonaCamara(normalizada, categoria.score)
    }

    private fun esPersona(deteccion: Detection): Boolean =
        deteccion.categories.maxByOrNull { it.score }?.label?.equals("person", ignoreCase = true) == true

    /** Una señal de pantalla o periférico del detector, con su posición si trae caja. */
    private fun senalDePantalla(deteccion: Detection, bitmap: Bitmap): SenalEtiqueta? {
        val categoria = deteccion.categories.maxByOrNull { it.score } ?: return null
        if (!TraductorPantallas.esEtiquetaConocida(categoria.label, OrigenEtiqueta.DETECTOR)) return null
        val caja = deteccion.boundingBox
        return SenalEtiqueta(
            etiqueta = categoria.label,
            confianza = categoria.score,
            posicion = caja?.let { CamaraPersonas.posicion(cajaNormalizada(it, bitmap)) },
            origen = OrigenEtiqueta.DETECTOR,
            caja = caja?.let { cajaSenal(it, bitmap) },
        )
    }

    /** Medidas de la caja en fracción de la foto, para las reglas de tamaño. */
    private fun cajaSenal(caja: RectF, bitmap: Bitmap): CajaSenal {
        val normalizada = cajaNormalizada(caja, bitmap)
        return CajaSenal(ancho = normalizada.ancho, alto = normalizada.alto)
    }

    /** ¿La foto ya trae una pantalla, laptop o teclado? Se usa para descartar sillas del fondo. */
    private fun contienePantalla(detecciones: List<Detection>): Boolean =
        detecciones.any { deteccion ->
            val etiqueta = deteccion.categories.maxByOrNull { it.score }?.label?.lowercase()
            etiqueta == "tv" || etiqueta == "laptop" || etiqueta == "keyboard"
        }

    /** Aplica las reglas de tamaño y confianza antes de describir un objeto genérico. */
    private fun conservarObjeto(deteccion: Detection, bitmap: Bitmap, hayPantalla: Boolean): Boolean {
        val categoria = deteccion.categories.maxByOrNull { it.score } ?: return false
        val caja = deteccion.boundingBox?.let { cajaSenal(it, bitmap) }
        return ReglasEntorno.conservarObjeto(categoria.label, categoria.score, caja, hayPantalla)
    }

    private fun esSenalDePantalla(deteccion: Detection): Boolean =
        deteccion.categories.maxByOrNull { it.score }?.let {
            TraductorPantallas.esEtiquetaConocida(it.label, OrigenEtiqueta.DETECTOR)
        } == true

    /** Segunda opinión de ML Kit; solo Computer y Television existen en su modelo real. */
    private suspend fun senalesMlKitDePantalla(bitmap: Bitmap): List<SenalEtiqueta> = try {
        etiquetador.process(InputImage.fromBitmap(bitmap, 0)).await().mapNotNull { etiqueta ->
            if (TraductorPantallas.esEtiquetaConocida(etiqueta.text, OrigenEtiqueta.ETIQUETADOR)) {
                SenalEtiqueta(
                    etiqueta = etiqueta.text,
                    confianza = etiqueta.confidence,
                    origen = OrigenEtiqueta.ETIQUETADOR,
                )
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Log.d(TAG, "Sin segunda opinion para pantallas.")
        emptyList()
    }

    /**
     * Colores de la ropa de la persona más cercana. Arriba toma el torso y abajo las piernas, sin
     * los extremos de la caja para no mezclar el fondo. Si no hay un color claro, devuelve null.
     */
    private fun ropaDe(bitmap: Bitmap, caja: CajaCamara): RopaCamara {
        val px = cajaAPixeles(caja, bitmap)
        return RopaCamara(
            arriba = colorDeFranja(bitmap, px, 0.20f, 0.55f),
            abajo = colorDeFranja(bitmap, px, 0.55f, 0.95f),
        )
    }

    private fun cajaAPixeles(caja: CajaCamara, bitmap: Bitmap): RectF {
        val ancho = bitmap.width.toFloat()
        val alto = bitmap.height.toFloat()
        return RectF(
            caja.izquierda * ancho,
            caja.arriba * alto,
            caja.derecha * ancho,
            caja.abajo * alto,
        )
    }

    /**
     * Busca un gafete en la zona del pecho de la persona más cercana y solo devuelve un nombre
     * filtrado. El recorte se descarta al terminar; nunca se guarda ni se manda a ningún lado.
     */
    private suspend fun leerGafete(bitmap: Bitmap, caja: CajaCamara): String? {
        val px = cajaAPixeles(caja, bitmap)
        val x0 = (px.left + px.width() * 0.15f).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = (px.right - px.width() * 0.15f).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = (px.top + px.height() * 0.25f).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = (px.top + px.height() * 0.60f).toInt().coerceIn(y0 + 1, bitmap.height)
        val ancho = x1 - x0
        val alto = y1 - y0
        if (ancho < 24 || alto < 24) return null
        val recorte = try {
            Bitmap.createBitmap(bitmap, x0, y0, ancho, alto)
        } catch (e: Exception) {
            Log.d(TAG, "No se pudo recortar la zona del gafete.")
            return null
        }
        return try {
            val texto = reconocedorTexto.process(InputImage.fromBitmap(recorte, 0)).await()
            Gafete.elegirNombre(texto.textBlocks.map { it.text })
        } catch (e: Exception) {
            Log.d(TAG, "El OCR del gafete no dio un nombre claro.")
            null
        } finally {
            recorte.recycle()
        }
    }

    private fun colorDeFranja(
        bitmap: Bitmap,
        caja: RectF,
        desde: Float,
        hasta: Float,
    ): String? {
        val ancho = caja.width()
        val alto = caja.height()
        if (ancho <= 0f || alto <= 0f) return null
        val x0 = (caja.left + ancho * 0.20f).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = (caja.right - ancho * 0.20f).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = (caja.top + alto * desde).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = (caja.top + alto * hasta).toInt().coerceIn(y0 + 1, bitmap.height)
        val paso = maxOf(1, minOf(x1 - x0, y1 - y0) / 30)
        val colores = ArrayList<String>()
        var brillo = 0.0
        var cuenta = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val pixel = bitmap.getPixel(x, y)
                colores += NombreColor.nombrePixel(pixel)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                brillo += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                cuenta++
                x += paso
            }
            y += paso
        }
        if (cuenta == 0) return null
        return CamaraPersonas.colorRopa(
            MuestraRopa(colores, brilloMedio = (brillo / cuenta).toFloat()),
        )
    }

    private fun cajaNormalizada(caja: RectF, bitmap: Bitmap): CajaCamara {
        val ancho = bitmap.width.toFloat().coerceAtLeast(1f)
        val alto = bitmap.height.toFloat().coerceAtLeast(1f)
        return CajaCamara(
            izquierda = (caja.left / ancho).coerceIn(0f, 1f),
            arriba = (caja.top / alto).coerceIn(0f, 1f),
            derecha = (caja.right / ancho).coerceIn(0f, 1f),
            abajo = (caja.bottom / alto).coerceIn(0f, 1f),
        )
    }

    /** Matriz de grises pequeña, igual que en el Lector de Documentos, para medir luz y nitidez. */
    private fun matrizGrises(bitmap: Bitmap, lado: Int = 100): Array<DoubleArray> {
        val escalada = Bitmap.createScaledBitmap(bitmap, lado, lado, true)
        return try {
            Array(lado) { y ->
                DoubleArray(lado) { x ->
                    val pixel = escalada.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    0.299 * r + 0.587 * g + 0.114 * b
                }
            }
        } finally {
            if (escalada !== bitmap) escalada.recycle()
        }
    }

    /** Nombres de las clases del modelo de monedas, leídos una sola vez desde assets. Vacío si el archivo no está. */
    private val etiquetasMonedas: List<String> by lazy {
        try {
            val etiquetas = context.assets.open(ARCHIVO_ETIQUETAS_MONEDAS).bufferedReader().use { lector ->
                lector.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
            Log.i(TAG_MONEDA, "Etiquetas cargadas desde assets (${etiquetas.size}) sin excepción: $etiquetas")
            if (etiquetas.size != NUM_CLASES_ESPERADAS) {
                Log.w(TAG_MONEDA, "Se esperaban $NUM_CLASES_ESPERADAS etiquetas, hay ${etiquetas.size}.")
            }
            etiquetas
        } catch (e: Exception) {
            Log.w(TAG_MONEDA, "No se pudo leer $ARCHIVO_ETIQUETAS_MONEDAS.", e)
            emptyList()
        }
    }

    /** Comprueba si hay una red con internet validada, para no esperar el tiempo de conexión de Gemini. */
    private fun hayInternet(): Boolean = try {
        val administrador = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val red = administrador.activeNetwork ?: return false
        val capacidades = administrador.getNetworkCapabilities(red) ?: return false
        capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        // Si no se puede consultar, se intenta Gemini; su tiempo de espera decide.
        true
    }

    /**
     * Reconoce una moneda con Gemini cuando hay internet, como antes. Si no hay conexión, la API
     * falla o la IA en internet está deshabilitada, usa el modelo local para no dejar el comando
     * sin respuesta. Si Gemini sí contesta (aunque diga que no ve moneda), se respeta su respuesta.
     */
    private suspend fun reconocerMonedaConRespaldo(bitmap: Bitmap): ResultadoMoneda {
        val deGemini = if (hayInternet()) {
            geminiMoneda.reconocer(bitmap)
        } else {
            ResultadoMoneda(null, 0f, MotivoMoneda.SIN_CONEXION)
        }
        if (deGemini.motivo != MotivoMoneda.SIN_CONEXION && deGemini.motivo != MotivoMoneda.IA_DESHABILITADA) {
            return deGemini
        }
        val local = reconocerMoneda(bitmap)
        return if (local.motivo == MotivoMoneda.SIN_MODELO) deGemini else local
    }

    /** Clasifica la foto con el modelo de monedas. Sin modelo o si algo falla, lo dice sin cerrar la app. */
    private suspend fun reconocerMoneda(bitmap: Bitmap, automatico: Boolean = false): ResultadoMoneda {
        val interpreter = interpreteMonedas ?: return ResultadoMoneda(null, 0f, MotivoMoneda.SIN_MODELO)
        return try {
            // El número de clases sale del propio modelo, así que esto sirve igual si se vuelve a entrenar con más
            // clases (por ejemplo "sin_moneda" y "varias"). Si monedas_labels.txt no coincide con el modelo, los
            // nombres quedan como "clase_N", que MonedaMx nunca toma por una moneda.
            val numClases = interpreter.getOutputTensor(0).shape().last()
            val nombres = if (etiquetasMonedas.size == numClases) {
                etiquetasMonedas
            } else {
                Log.w(TAG_MONEDA, "El modelo tiene $numClases clases y monedas_labels.txt ${etiquetasMonedas.size}.")
                (0 until numClases).map { "clase_$it" }
            }
            val espejo = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postScale(-1f, 1f) }, true)
            val centro = recorteCentral(bitmap)
            val variantes = listOf(bitmap, espejo, centro)
            val porVariante: List<FloatArray> = try {
                withContext(Dispatchers.Default) {
                    variantes.map { variante ->
                        val scaled = Bitmap.createScaledBitmap(variante, LADO_MONEDA, LADO_MONEDA, true)
                        val salida = Array(1) { FloatArray(numClases) }
                        interpreter.run(prepararEntrada(scaled), salida)
                        if (scaled !== variante) scaled.recycle()
                        salida[0]
                    }
                }
            } finally {
                // Solo se liberan las copias; la foto original se sigue usando después.
                espejo.recycle()
                if (centro !== bitmap) centro.recycle()
            }
            val promedios = FloatArray(numClases)
            porVariante.forEach { arr -> for (i in promedios.indices) promedios[i] += arr[i] / porVariante.size }

            fun comoLecturas(probabilidades: FloatArray) = probabilidades.mapIndexed { i, p -> nombres[i] to p }
            val lecturas = comoLecturas(promedios)
            val lecturasPorVariante = porVariante.map { comoLecturas(it) }

            Log.d(TAG_MONEDA, "probabilidades promedio: " + lecturas.joinToString { "${it.first}=${"%.4f".format(it.second)}" })
            listOf("completa", "espejo", "centro").forEachIndexed { i, nombre ->
                Log.d(TAG_MONEDA, "  variante $nombre: " + lecturasPorVariante[i].joinToString { "${it.first}=${"%.3f".format(it.second)}" })
            }
            MonedaMx.decidir(lecturas, lecturasPorVariante, automatico).also {
                Log.d(TAG_MONEDA, "decisión (${if (automatico) "automática" else "pedida"}): $it")
            }
        } catch (e: Exception) {
            Log.e(TAG_MONEDA, "Falló la clasificación de la moneda", e)
            ResultadoMoneda(null, 0f, MotivoMoneda.NO_CLARO)
        }
    }

    /** Prepara un bitmap 224×224 como tensor float32 normalizado con MobileNet V2 preprocessing. */
    private fun prepararEntrada(bitmap: Bitmap): ByteBuffer {
        val entrada = ByteBuffer.allocateDirect(4 * LADO_MONEDA * LADO_MONEDA * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(LADO_MONEDA * LADO_MONEDA)
        bitmap.getPixels(pixels, 0, LADO_MONEDA, 0, 0, LADO_MONEDA, LADO_MONEDA)
        for (px in pixels) {
            entrada.putFloat((((px shr 16) and 0xFF) - 127.5f) / 127.5f)
            entrada.putFloat((((px shr 8) and 0xFF) - 127.5f) / 127.5f)
            entrada.putFloat(((px and 0xFF) - 127.5f) / 127.5f)
        }
        entrada.rewind()
        return entrada
    }

    /** Recorte central cuadrado para dar más peso a la moneda que al fondo. */
    private fun recorteCentral(original: Bitmap): Bitmap {
        val lado = minOf(original.width, original.height)
        val x = (original.width - lado) / 2
        val y = (original.height - lado) / 2
        return Bitmap.createBitmap(original, x, y, lado, lado)
    }

    /** Guarda el resultado de la moneda en el historial (si hay usuario) y lo devuelve listo para decir en voz alta. */
    private suspend fun guardarMoneda(
        usuarioId: Long?,
        rutaImagen: String,
        resultado: ResultadoMoneda,
        inicio: Long,
    ): EnvironmentResult {
        val frase = MonedaMx.frase(resultado)
        val d = resultado.denominacion
        val etiqueta = if (resultado.esSegura && d != null) "moneda de ${d.valor} pesos" else "moneda sin identificar"
        val etiquetas = listOf(etiqueta)
        val escaneoId = usuarioId?.let { id ->
            val nuevoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = id, rutaImagen = rutaImagen))
            objetoDao.insertAll(
                listOf(ObjetoDetectadoEntity(escaneoId = nuevoId, etiqueta = etiqueta, confianza = resultado.confianza)),
            )
            descripcionDao.insert(DescripcionEntornoEntity(escaneoId = nuevoId, textoDescripcion = frase))
            nuevoId
        }
        Log.d(TAG, "Moneda lista en ${SystemClock.elapsedRealtime() - inicio} ms: $frase")
        return EnvironmentResult(escaneoId, frase, etiquetas, monedas = resultado)
    }


    /** Guarda el resultado del billete en el historial (si hay usuario) y lo devuelve listo para decir en voz alta. */
    private suspend fun guardarBillete(
        usuarioId: Long?,
        rutaImagen: String,
        resultado: ResultadoBillete,
        inicio: Long,
    ): EnvironmentResult {
        val frase = fraseBillete(resultado)
        // Una etiqueta por cada denominación confirmada; si no se pudo leer ninguna, solo "billete".
        val etiquetas = resultado.todas.map {
            val n = resultado.cantidadDe(it)
            if (n > 1) "$n billetes de ${it.valor} pesos" else "billete de ${it.valor} pesos"
        }.ifEmpty { listOf("billete") }
        val escaneoId = usuarioId?.let { id ->
            val nuevoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = id, rutaImagen = rutaImagen))
            objetoDao.insertAll(
                etiquetas.map { ObjetoDetectadoEntity(escaneoId = nuevoId, etiqueta = it, confianza = 1f) },
            )
            descripcionDao.insert(DescripcionEntornoEntity(escaneoId = nuevoId, textoDescripcion = frase))
            nuevoId
        }
        Log.d(TAG, "Billete listo en ${SystemClock.elapsedRealtime() - inicio} ms: $frase")
        return EnvironmentResult(escaneoId, frase, etiquetas, billetes = resultado)
    }

    /**
     * En una foto normal, ¿hay un billete del que se lea la denominación con claridad? Devuelve null si no, y
     * entonces sigue la descripción normal del entorno. Es más exigente que "qué billete es": aquí nadie dijo que
     * fuera un billete, así que hace falta un indicio (palabras del billete, etiqueta de imagen o color).
     */
    private suspend fun detectarBilleteAutomatico(bitmap: Bitmap): ResultadoBillete? {
        return try {
            val etiquetas = try {
                etiquetadorBilletes.process(InputImage.fromBitmap(bitmap, 0)).await().map { it.text.lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
            val diceBillete = etiquetas.any { it in ETIQUETAS_DE_BILLETE }
            val dicePapel = etiquetas.any { it in ETIQUETAS_DE_PAPEL }
            val colores = BilleteMx.colorDominante(pixelesDelCentro(bitmap))

            // Derecha primero; si hay pinta de billete o papel y no salió nada, también girado.
            val giros = if (diceBillete || dicePapel) intArrayOf(0, 90, 270) else intArrayOf(0)
            for (grados in giros) {
                val imagen = if (grados == 0) bitmap else girar(bitmap, grados.toFloat())
                val palabras = try {
                    leerPalabras(imagen)
                } catch (e: Exception) {
                    emptyList()
                } finally {
                    if (imagen !== bitmap) imagen.recycle()
                }
                // 1) Palabras del billete o etiqueta de "billete/dinero": basta con que se lea la denominación.
                val seguro = BilleteMx.reconocer(palabras, esBilletePorImagen = diceBillete, colores = colores)
                if (seguro.denominacion != null && seguro.esBilleteSeguro) {
                    Log.d(TAG_BILLETE, "automático ($grados): $seguro")
                    return seguro
                }
                // 2) Sin palabras del billete: se acepta si el color dominante respalda la denominación leída
                //    (con menos exigencia si la etiqueta de imagen al menos dice "papel").
                val porColor = BilleteMx.reconocer(palabras, esBilletePorImagen = true, colores = colores)
                val d = porColor.denominacion
                // Aquí el color solo respalda a una denominación; con varias no hay forma de respaldar a todas,
                // y un cartel con dos precios grandes no debe pasar por billetes.
                if (d != null && porColor.adicionales.isEmpty()) {
                    val apoyo = colores[d] ?: 0f
                    if (apoyo >= (if (dicePapel) 0.35f else 0.5f)) {
                        Log.d(TAG_BILLETE, "automático por color ($grados, apoyo $apoyo): $porColor")
                        return porColor
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG_BILLETE, "Falló la revisión automática de billetes", e)
            null
        }
    }

    /** Qué dice en voz alta la app según lo que se pudo leer del billete. */
    private fun fraseBillete(r: ResultadoBillete): String = when {
        // Varios billetes (distintos o iguales): se dice cuántos hay de cada denominación, de mayor a menor. El
        // conteo es aproximado (billetes pegados o encimados se cuentan de menos), y se avisa.
        r.denominacion != null && r.hayVarios ->
            "Veo ${unir(r.todas.sortedByDescending { it.valor }.map { frasePorDenominacion(it, r.cantidadDe(it)) })}. " +
                "El conteo es aproximado; si están juntos o encimados puede fallar. " +
                "Para estar segura, muéstramelos de uno en uno."
        r.denominacion != null -> "Es un billete de ${r.denominacion.nombreHablado}."
        r.esBilleteSeguro && r.sugerida != null ->
            "Parece un billete de ${r.sugerida.nombreHablado}, pero no estoy segura. " +
                "Ponlo plano, acércalo y con buena luz, y pregunta otra vez."
        r.esBilleteSeguro ->
            "Veo un billete, pero no alcanzo a leer cuánto es. Ponlo plano frente a la cámara, " +
                "que se vean los números grandes y con buena luz, y pregunta otra vez."
        else -> "No veo un billete. Ponlo plano frente a la cámara, con buena luz, y pregunta otra vez."
    }

    /**
     * Lee el billete con OCR (palabra por palabra, con su tamaño) y apoya la decisión con el color. El billete
     * puede estar acostado o de pie respecto al teléfono, así que si no sale a la primera se prueba girado.
     */
    private suspend fun leerBillete(bitmap: Bitmap): ResultadoBillete {
        val colores = BilleteMx.colorDominante(pixelesDelCentro(bitmap))
        var mejor: ResultadoBillete? = null
        for (grados in intArrayOf(0, 90, 270)) {
            val imagen = if (grados == 0) bitmap else girar(bitmap, grados.toFloat())
            val palabras = try {
                leerPalabras(imagen)
            } catch (e: Exception) {
                Log.e(TAG, "Falló el OCR del billete ($grados grados)", e)
                emptyList()
            } finally {
                if (imagen !== bitmap) imagen.recycle()
            }
            val resultado = BilleteMx.reconocer(
                palabras = palabras,
                esBilletePorImagen = false,
                usuarioPideBillete = true,
                colores = colores,
            )
            Log.d(TAG_BILLETE, "giro $grados: ${palabras.size} palabras -> $resultado (todas: ${resultado.todas})")
            if (resultado.denominacion != null) return resultado
            if (mejor == null || (resultado.sugerida != null && mejor.sugerida == null)) mejor = resultado
        }
        return mejor ?: ResultadoBillete(null, true, true)
    }

    private suspend fun leerPalabras(bitmap: Bitmap): List<PalabraOcr> {
        val texto = reconocedorTexto.process(InputImage.fromBitmap(bitmap, 0)).await()
        return texto.textBlocks.flatMap { it.lines }.flatMap { it.elements }.map { e ->
            // Alto de la letra: la menor de las dos medidas de la caja sirve aunque la palabra esté girada.
            val caja = e.boundingBox
            if (caja == null) {
                PalabraOcr(e.text)
            } else {
                PalabraOcr(e.text, minOf(caja.width(), caja.height()), caja.centerX(), caja.centerY())
            }
        }
    }

    private fun girar(bitmap: Bitmap, grados: Float): Bitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(grados) }, true)

    /** Muestra de píxeles de la mitad central de la foto, donde está el billete, para estimar su color. */
    private fun pixelesDelCentro(bitmap: Bitmap): IntArray {
        val ancho = bitmap.width
        val alto = bitmap.height
        val x0 = ancho / 4
        val y0 = alto / 4
        val paso = maxOf(1, minOf(ancho, alto) / 120)
        val salida = ArrayList<Int>()
        var y = y0
        while (y < alto - alto / 4) {
            var x = x0
            while (x < ancho - ancho / 4) {
                salida += bitmap.getPixel(x, y)
                x += paso
            }
            y += paso
        }
        return salida.toIntArray()
    }

    /** Usa la etiqueta y la caja del detector específico; evita volver a adivinar con el modelo genérico. */
    private fun describirObjetoDetectado(deteccion: Detection, bitmap: Bitmap): ObjetoDescrito? {
        val categoria = deteccion.categories.maxByOrNull { it.score } ?: return null
        val nombre = EtiquetasEntorno.traducir(categoria.label) ?: run {
            Log.d(TAG_ETIQUETAS, "Clase COCO sin traducción: ${categoria.label} (${categoria.score})")
            return null
        }
        val caja = deteccion.boundingBox
        val color = if (EtiquetasEntorno.admiteColor(categoria.label)) {
            colorDeLaCaja(bitmap, caja, categoria.score)
        } else {
            null
        }
        return ObjetoDescrito(
            nombre = nombre,
            color = color,
            posicion = posicionHorizontal(caja, bitmap.width),
            confianza = categoria.score,
            area = (caja.width() * caja.height()).toInt(),
            dudoso = categoria.score < CONFIANZA_SEGURA_DETECTOR,
        )
    }

    /**
     * Color dominante del centro de la caja del objeto (el 60 % del medio, para no tomar el fondo). Si el objeto
     * mezcla varios colores sin que uno domine, no se dice ninguno en lugar de adivinar. Tampoco se dice si el
     * detector no está seguro: un color equivocado confunde más que no mencionarlo.
     */
    private fun colorDeLaCaja(bitmap: Bitmap, caja: RectF, confianza: Float): String? {
        if (confianza < ReglasEntorno.UMBRAL_COLOR) return null
        val ancho = caja.width()
        val alto = caja.height()
        val x0 = (caja.left + ancho * 0.2f).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = (caja.right - ancho * 0.2f).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = (caja.top + alto * 0.2f).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = (caja.bottom - alto * 0.2f).toInt().coerceIn(y0 + 1, bitmap.height)
        val paso = maxOf(1, minOf(x1 - x0, y1 - y0) / 40)
        val muestra = ArrayList<Int>()
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                muestra += bitmap.getPixel(x, y)
                x += paso
            }
            y += paso
        }
        val dominante = NombreColor.dominante(muestra.toIntArray(), 0.45f) ?: return null
        return if (ReglasEntorno.colorConfiable(confianza, dominante.fraccion)) dominante.nombre else null
    }

    /**
     * Sin cajas del detector: etiqueta la foto completa y devuelve lo más seguro, sin posición. Primero se buscan
     * etiquetas concretas ("mano", "teléfono celular"); las vagas ("dispositivo", "piel") solo si no hay otra.
     */
    private suspend fun describirFotoCompleta(
        bitmap: Bitmap,
        excluirPersonas: Boolean = false,
        excluirPantallas: Boolean = false,
    ): ObjetoDescrito? {
        val etiquetas = etiquetador.process(InputImage.fromBitmap(bitmap, 0)).await()
            .sortedByDescending { it.confidence }
        Log.d(TAG_ETIQUETAS, "Etiquetas de la foto: " + etiquetas.joinToString { "${it.text} (${"%.2f".format(it.confidence)})" })
        val candidatas = etiquetas.filter {
            !EtiquetasEntorno.esDemasiadoGeneral(it.text) &&
                (!excluirPersonas || !esEtiquetaPersona(it.text)) &&
                (!excluirPantallas ||
                    !TraductorPantallas.esEtiquetaConocida(it.text, OrigenEtiqueta.ETIQUETADOR))
        }
        val elegida = candidatas.firstOrNull { !EtiquetasEntorno.esDebil(it.text) && EtiquetasEntorno.traducir(it.text) != null }
            ?: candidatas.firstOrNull { EtiquetasEntorno.traducir(it.text) != null }
        if (elegida == null) {
            etiquetas.filter { !EtiquetasEntorno.esIgnorada(it.text) }
                .forEach { Log.d(TAG_ETIQUETAS, "Sin traducción: ${it.text} (${it.confidence})") }
            return null
        }
        val nombre = EtiquetasEntorno.traducir(elegida.text) ?: return null
        val color = if (EtiquetasEntorno.admiteColor(elegida.text)) {
            val dominante = NombreColor.dominante(pixelesDelCentro(bitmap), 0.5f)
            if (dominante != null && ReglasEntorno.colorConfiable(elegida.confidence, dominante.fraccion)) {
                dominante.nombre
            } else {
                null
            }
        } else {
            null
        }
        return ObjetoDescrito(
            nombre = nombre,
            color = color,
            posicion = null,
            confianza = elegida.confidence,
            area = bitmap.width * bitmap.height,
            dudoso = elegida.confidence < CONFIANZA_SEGURA_RESPALDO || EtiquetasEntorno.esDebil(elegida.text),
        )
    }

    private fun esEtiquetaPersona(texto: String): Boolean =
        texto.equals("person", ignoreCase = true) || texto.equals("persona", ignoreCase = true)

    private fun posicionHorizontal(caja: RectF, anchoImagen: Int): String {
        val centro = caja.centerX() / anchoImagen.toFloat()
        return when {
            centro < 0.33f -> "a la izquierda"
            centro > 0.66f -> "a la derecha"
            else -> "al centro"
        }
    }

    private fun ordenHorizontal(posicion: String?): Int = when (posicion) {
        "a la izquierda" -> 0
        "al centro" -> 1
        "a la derecha" -> 2
        else -> 3
    }

    /** "un billete de veinte pesos", "dos billetes de cien pesos". */
    private fun frasePorDenominacion(d: DenominacionBillete, n: Int): String = when (n) {
        1 -> "un billete de ${d.nombreHablado}"
        else -> "${numeroEnPalabras(n)} billetes de ${d.nombreHablado}"
    }

    private fun numeroEnPalabras(n: Int): String = when (n) {
        2 -> "dos"; 3 -> "tres"; 4 -> "cuatro"; 5 -> "cinco"; 6 -> "seis"; 7 -> "siete"; 8 -> "ocho"; 9 -> "nueve"
        10 -> "diez"
        else -> n.toString()
    }

    private fun unir(frases: List<String>): String = when (frases.size) {
        0 -> ""
        1 -> frases[0]
        else -> frases.dropLast(1).joinToString(", ") + " y " + frases.last()
    }

    /** Carga la foto, la reduce si es enorme y la gira según el EXIF para que quede derecha. */
    private fun cargarBitmapOrientado(ruta: String): Bitmap? {
        return try {
            val bordes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(ruta, bordes)
            if (bordes.outWidth <= 0 || bordes.outHeight <= 0) return null

            var muestreo = 1
            while (maxOf(bordes.outWidth, bordes.outHeight) / muestreo > LADO_MAXIMO) muestreo *= 2

            val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
            val original = BitmapFactory.decodeFile(ruta, opciones) ?: return null

            val grados = when (
                ExifInterface(ruta).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (grados == 0f) return original

            val matriz = Matrix().apply { postRotate(grados) }
            val girado = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)
            if (girado !== original) original.recycle()
            girado
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val TAG = "EntornoTiempo"
        const val TAG_ETIQUETAS = "EntornoEtiquetas"
        const val TAG_DETECCIONES = "EntornoDetecciones"
        const val TAG_BILLETE = "EntornoBillete"
        val ETIQUETAS_DE_BILLETE = setOf("banknote", "money", "currency", "cash", "coin")
        val ETIQUETAS_DE_PAPEL = setOf("paper", "paper product", "document", "rectangle")
        const val MODELO_DE_OBJETOS = "efficientdet_lite0.tflite"
        const val TAG_MONEDA = "EntornoMoneda"
        const val MODELO_DE_MONEDAS = "monedas.tflite"
        const val ARCHIVO_ETIQUETAS_MONEDAS = "monedas_labels.txt"
        const val NUM_CLASES_ESPERADAS = 6
        const val MAX_RESULTADOS_MONEDA = 5
        const val LADO_MONEDA = 224
        const val MAX_OBJETOS = 5
        const val CONFIANZA_MINIMA = 0.4f
        const val CONFIANZA_RESPALDO = 0.55f
        // Por debajo de esto se dice "posiblemente" en lugar de afirmarlo.
        const val CONFIANZA_SEGURA_DETECTOR = 0.6f
        const val CONFIANZA_SEGURA_RESPALDO = 0.8f
        const val LADO_MAXIMO = 1280
    }
}

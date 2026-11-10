package com.cecapi.app.feature.modulo4_lectordocumentos

import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Indicación de voz para encuadrar el documento antes de tomar la foto. */
enum class FramingHint(val mensaje: String) {
    SIN_TEXTO("No veo texto. Aleja un poco el teléfono o muévelo despacio sobre el papel."),
    ACERCA("Acércalo un poco."),
    ALEJA("Aléjalo un poco, el texto no cabe completo."),
    IZQUIERDA("Mueve el teléfono a la izquierda."),
    DERECHA("Mueve el teléfono a la derecha."),
    ARRIBA("Mueve el teléfono hacia arriba."),
    ABAJO("Mueve el teléfono hacia abajo."),
    LISTO("Así está bien. Toca el botón o di toma la foto."),
}

/**
 * Revisa la vista previa de la cámara con ML Kit unas veces por segundo y reporta hacia dónde
 * mover el teléfono para que el texto quede completo y centrado en la foto.
 */
class TextFramingAnalyzer(private val onHint: (FramingHint) -> Unit) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var ultimoAnalisis = 0L

    @Volatile
    private var cerrado = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val ahora = SystemClock.elapsedRealtime()
        val mediaImage = imageProxy.image
        if (cerrado || mediaImage == null || ahora - ultimoAnalisis < INTERVALO_MS) {
            imageProxy.close()
            return
        }
        ultimoAnalisis = ahora

        // ML Kit devuelve las cajas en la imagen ya girada (derecha), así que el ancho y alto se intercambian a 90°/270°.
        val rotacion = imageProxy.imageInfo.rotationDegrees
        val ancho = if (rotacion % 180 == 0) imageProxy.width else imageProxy.height
        val alto = if (rotacion % 180 == 0) imageProxy.height else imageProxy.width

        recognizer.process(InputImage.fromMediaImage(mediaImage, rotacion))
            .addOnSuccessListener { texto ->
                if (cerrado) return@addOnSuccessListener
                val cajas = texto.textBlocks
                    .filter { it.text.trim().length >= MIN_CARACTERES }
                    .mapNotNull { it.boundingBox }
                onHint(evaluarEncuadre(cajas, ancho, alto))
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        cerrado = true
        recognizer.close()
    }

    private companion object {
        const val INTERVALO_MS = 700L

        /** Bloques más cortos suelen ser ruido (sombras, bordes) y no cuentan como texto. */
        const val MIN_CARACTERES = 3
    }
}

/**
 * Decide la indicación a partir de las cajas del texto en una imagen de [ancho] x [alto].
 * Primero evita que el texto quede cortado, luego lo centra y al final revisa que se vea grande.
 */
internal fun evaluarEncuadre(cajas: List<Rect>, ancho: Int, alto: Int): FramingHint {
    if (cajas.isEmpty() || ancho <= 0 || alto <= 0) return FramingHint.SIN_TEXTO

    val izquierda = cajas.minOf { it.left } / ancho.toFloat()
    val derecha = cajas.maxOf { it.right } / ancho.toFloat()
    val arriba = cajas.minOf { it.top } / alto.toFloat()
    val abajo = cajas.maxOf { it.bottom } / alto.toFloat()

    val cortaIzquierda = izquierda < MARGEN_BORDE
    val cortaDerecha = derecha > 1f - MARGEN_BORDE
    val cortaArriba = arriba < MARGEN_BORDE
    val cortaAbajo = abajo > 1f - MARGEN_BORDE

    val centroX = (izquierda + derecha) / 2f
    val centroY = (arriba + abajo) / 2f
    val area = (derecha - izquierda) * (abajo - arriba)

    return when {
        (cortaIzquierda && cortaDerecha) || (cortaArriba && cortaAbajo) -> FramingHint.ALEJA
        cortaIzquierda -> FramingHint.IZQUIERDA
        cortaDerecha -> FramingHint.DERECHA
        cortaArriba -> FramingHint.ARRIBA
        cortaAbajo -> FramingHint.ABAJO
        centroX < 0.5f - DESCENTRADO -> FramingHint.IZQUIERDA
        centroX > 0.5f + DESCENTRADO -> FramingHint.DERECHA
        centroY < 0.5f - DESCENTRADO -> FramingHint.ARRIBA
        centroY > 0.5f + DESCENTRADO -> FramingHint.ABAJO
        area < AREA_MINIMA -> FramingHint.ACERCA
        else -> FramingHint.LISTO
    }
}

/** Fracción del borde de la imagen donde se considera que el texto quedó cortado. */
private const val MARGEN_BORDE = 0.03f

/** Qué tan lejos del centro (en fracción de la imagen) puede quedar el texto antes de pedir moverlo. */
private const val DESCENTRADO = 0.15f

/** El texto debe ocupar al menos esta fracción de la foto para leerse bien. */
private const val AREA_MINIMA = 0.15f

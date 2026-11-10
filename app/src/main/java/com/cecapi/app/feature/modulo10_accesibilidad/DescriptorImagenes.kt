package com.cecapi.app.feature.modulo10_accesibilidad

import com.cecapi.app.feature.modulo7_entorno.EtiquetasEntorno

/** Una zona de la captura, en píxeles de la imagen. */
data class ZonaCaptura(
    val izquierda: Int,
    val arriba: Int,
    val derecha: Int,
    val abajo: Int,
) {
    val ancho: Int get() = (derecha - izquierda).coerceAtLeast(0)
    val alto: Int get() = (abajo - arriba).coerceAtLeast(0)
    val area: Long get() = ancho.toLong() * alto

    fun recortar(anchoTotal: Int, altoTotal: Int): ZonaCaptura = ZonaCaptura(
        izquierda = izquierda.coerceIn(0, anchoTotal),
        arriba = arriba.coerceIn(0, altoTotal),
        derecha = derecha.coerceIn(0, anchoTotal),
        abajo = abajo.coerceIn(0, altoTotal),
    )

    fun expandir(margen: Int): ZonaCaptura = ZonaCaptura(
        izquierda = izquierda - margen,
        arriba = arriba - margen,
        derecha = derecha + margen,
        abajo = abajo + margen,
    )

    fun seSolapaCon(otra: ZonaCaptura): Boolean =
        izquierda <= otra.derecha && otra.izquierda <= derecha &&
            arriba <= otra.abajo && otra.arriba <= abajo

    fun unir(otra: ZonaCaptura): ZonaCaptura = ZonaCaptura(
        izquierda = minOf(izquierda, otra.izquierda),
        arriba = minOf(arriba, otra.arriba),
        derecha = maxOf(derecha, otra.derecha),
        abajo = maxOf(abajo, otra.abajo),
    )
}

/** Una etiqueta que devolvió el etiquetador de imágenes del teléfono, en inglés. */
data class EtiquetaImagen(
    val texto: String,
    val confianza: Float,
)

/**
 * Decide qué imágenes se describen y cómo se dice la frase. Es código puro a propósito: no depende
 * de Android ni de ML Kit, así se prueba sin teléfono.
 *
 * El servicio le pasa las cajas de texto que encontró el OCR y las etiquetas del etiquetador. Aquí
 * se calculan las zonas que hay que tapar, se descarta la pantalla que es solo texto, se filtran
 * las etiquetas por confianza y se arma una frase sin conteos exactos.
 */
object DescriptorImagenes {

    const val CONFIANZA_MINIMA = 0.6f
    const val COBERTURA_TEXTO_MAXIMA = 0.6f
    const val MAX_ETIQUETAS = 3

    private const val MARGEN_MINIMO = 2
    private const val MARGEN_DIVISOR = 200

    private val ETIQUETAS_DE_TEXTO = setOf(
        "text", "font", "typeface", "letter", "word", "number", "symbol",
        "document", "paper", "paper product", "page", "sheet", "screenshot",
        "handwriting", "calligraphy", "menu", "web page", "website",
        "book", "publication", "ebook", "e-book", "comic book",
        "newspaper", "magazine", "poster", "graphic design", "logo", "brand",
    )

    /**
     * Zonas que hay que tapar antes de etiquetar. Recorta lo que se sale de la captura, amplía cada
     * caja para que no queden bordes de letras y une las cajas que se tocan.
     */
    fun zonasParaTapar(textos: List<ZonaCaptura>, ancho: Int, alto: Int): List<ZonaCaptura> {
        if (ancho <= 0 || alto <= 0) return emptyList()
        val margen = maxOf(MARGEN_MINIMO, minOf(ancho, alto) / MARGEN_DIVISOR)
        val recortadas = textos
            .map { it.expandir(margen).recortar(ancho, alto) }
            .filter { it.area > 0 }
        return fusionar(recortadas)
    }

    /** Proporción de la captura cubierta por las zonas de texto, de 0 a 1. */
    fun coberturaTexto(textos: List<ZonaCaptura>, ancho: Int, alto: Int): Float {
        if (ancho <= 0 || alto <= 0) return 0f
        val area = zonasParaTapar(textos, ancho, alto).sumOf { it.area }
        return (area.toFloat() / (ancho.toLong() * alto.toLong()).toFloat()).coerceIn(0f, 1f)
    }

    /**
     * La frase para leer en voz alta, o null si la pantalla es solo texto o no hay etiquetas con
     * confianza suficiente. Nunca cuenta objetos exactos.
     */
    fun descripcion(
        textos: List<ZonaCaptura>,
        etiquetas: List<EtiquetaImagen>,
        ancho: Int,
        alto: Int,
    ): String? {
        if (ancho <= 0 || alto <= 0) return null
        if (coberturaTexto(textos, ancho, alto) >= COBERTURA_TEXTO_MAXIMA) return null

        val elegidas = LinkedHashMap<String, EtiquetaImagen>()
        for (etiqueta in etiquetas) {
            val texto = etiqueta.texto.trim()
            if (texto.isEmpty() || etiqueta.confianza < CONFIANZA_MINIMA) continue
            if (esEtiquetaDeTexto(texto)) continue
            if (EtiquetasEntorno.esIgnorada(texto)) continue
            if (EtiquetasEntorno.esDemasiadoGeneral(texto)) continue
            val nombre = EtiquetasEntorno.traducir(texto) ?: continue

            val clave = nombre.texto.lowercase()
            val previa = elegidas[clave]
            if (previa == null || etiqueta.confianza > previa.confianza) elegidas[clave] = etiqueta
        }

        if (elegidas.isEmpty()) return null

        val frases = elegidas.values
            .sortedByDescending { it.confianza }
            .take(MAX_ETIQUETAS)
            .mapNotNull { EtiquetasEntorno.traducir(it.texto)?.conArticulo }

        if (frases.isEmpty()) return null
        return "Veo ${unir(frases)}."
    }

    private fun esEtiquetaDeTexto(texto: String): Boolean =
        texto.lowercase().trim() in ETIQUETAS_DE_TEXTO

    private fun fusionar(zonas: List<ZonaCaptura>): List<ZonaCaptura> {
        val resultado = zonas.toMutableList()
        var cambio = true
        while (cambio) {
            cambio = false
            for (i in resultado.indices) {
                for (j in i + 1 until resultado.size) {
                    if (resultado[i].seSolapaCon(resultado[j])) {
                        resultado[i] = resultado[i].unir(resultado[j])
                        resultado.removeAt(j)
                        cambio = true
                        break
                    }
                }
                if (cambio) break
            }
        }
        return resultado
    }

    private fun unir(frases: List<String>): String = when (frases.size) {
        0 -> ""
        1 -> frases[0]
        2 -> "${frases[0]} y ${frases[1]}"
        else -> frases.dropLast(1).joinToString(", ") + " y " + frases.last()
    }
}

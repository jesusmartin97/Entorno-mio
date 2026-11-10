package com.cecapi.app.feature.modulo7_entorno

import kotlin.math.max
import kotlin.math.min

/**
 * Nombre en español del color dominante de una zona de la foto ("azul", "rojo", "café"...).
 * Sin dependencias de Android para poder probarlo aparte.
 */
object NombreColor {

    /**
     * Un rojo muy oscuro se dice "café": en el habla común un vino/marrón rojizo (0xFF8B0000, brillo 0.545) no
     * se percibe como rojo. El umbral queda por debajo del rojo ladrillo (0xFFB22222, brillo 0.698) y de
     * cualquier rojo vivo, para no cambiar los casos que ya funcionaban.
     */
    private const val BRILLO_MAXIMO_CAFE_ROJIZO = 0.56f

    /** Resultado: el color y qué tan dominante es (0 a 1, la fracción de los píxeles que son de ese color). */
    data class Dominante(val nombre: String, val fraccion: Float)

    /**
     * El color que más píxeles tiene, o null si no hay uno claro (muchos colores mezclados) o no hay píxeles.
     * @param minimoFraccion qué fracción de los píxeles debe ser de ese color para decirlo.
     */
    fun dominante(pixeles: IntArray, minimoFraccion: Float = 0.4f): Dominante? {
        if (pixeles.isEmpty()) return null
        val cuenta = HashMap<String, Int>()
        for (px in pixeles) {
            val nombre = nombrePixel(px)
            cuenta[nombre] = (cuenta[nombre] ?: 0) + 1
        }
        val mejor = cuenta.maxByOrNull { it.value } ?: return null
        val fraccion = mejor.value.toFloat() / pixeles.size
        return if (fraccion >= minimoFraccion) Dominante(mejor.key, fraccion) else null
    }

    /** Nombre del color de un píxel ARGB. */
    fun nombrePixel(px: Int): String {
        val r = ((px shr 16) and 0xFF) / 255f
        val g = ((px shr 8) and 0xFF) / 255f
        val b = (px and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val delta = mx - mn
        val v = mx
        val s = if (mx == 0f) 0f else delta / mx

        // Sin color: negro, gris o blanco según qué tan claro es.
        if (v < 0.18f) return "negro"
        if (s < 0.16f) return when {
            v > 0.82f -> "blanco"
            v < 0.30f -> "negro"
            else -> "gris"
        }

        val matiz = when (mx) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }.let { if (it < 0f) it + 360f else it }

        return when {
            matiz < 12f || matiz >= 345f -> when {
                s < 0.45f && v > 0.7f -> "rosa"
                v < BRILLO_MAXIMO_CAFE_ROJIZO -> "café"
                else -> "rojo"
            }
            matiz < 40f -> if (v < 0.62f) "café" else "naranja"
            matiz < 70f -> if (v < 0.5f) "café" else "amarillo"
            matiz < 170f -> "verde"
            matiz < 255f -> "azul"
            matiz < 300f -> "morado"
            else -> "rosa" // 300 a 345: magenta, fucsia, rosa
        }
    }
}

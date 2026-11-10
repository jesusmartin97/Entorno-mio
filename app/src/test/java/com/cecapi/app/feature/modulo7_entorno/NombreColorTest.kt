package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Pruebas de la asignación de nombre a un color. Sin Android: [NombreColor] es código puro.
 *
 * El caso que originó estas pruebas es el café oscuro (0xFF8B0000), que antes se decía "rojo".
 */
class NombreColorTest {

    private fun nombre(px: Int): String = NombreColor.nombrePixel(px)

    @Test
    fun `cafe oscuro se llama cafe y no rojo`() {
        // Vino/marrón rojizo: brillo 0.545, antes caía en la regla de rojo.
        assertEquals("café", nombre(0xFF8B0000.toInt()))
    }

    @Test
    fun `cafe terroso sigue siendo cafe`() {
        assertEquals("café", nombre(0xFF8B4513.toInt()))
    }

    @Test
    fun `los rojos claros siguen siendo rojos`() {
        // El umbral de café (0.56) no debe alcanzar a estos rojos que ya funcionaban.
        assertEquals("rojo", nombre(0xFFFF0000.toInt())) // rojo puro
        assertEquals("rojo", nombre(0xFFDC143C.toInt())) // carmesí
        assertEquals("rojo", nombre(0xFFB22222.toInt())) // ladrillo, brillo 0.698
        assertEquals("rojo", nombre(0xFFCD5C5C.toInt())) // indio
        assertEquals("rojo", nombre(0xFFFF6347.toInt())) // jitomate
        assertEquals("rojo", nombre(0xFF990000.toInt())) // rojo oscuro, brillo 0.60
    }

    @Test
    fun `colores basicos`() {
        assertEquals("negro", nombre(0xFF000000.toInt()))
        assertEquals("blanco", nombre(0xFFFFFFFF.toInt()))
        assertEquals("gris", nombre(0xFF808080.toInt()))
        assertEquals("rojo", nombre(0xFFFF0000.toInt()))
        assertEquals("verde", nombre(0xFF008000.toInt()))
        assertEquals("azul", nombre(0xFF0000FF.toInt()))
        assertEquals("amarillo", nombre(0xFFFFFF00.toInt()))
        assertEquals("naranja", nombre(0xFFFF8000.toInt()))
        assertEquals("morado", nombre(0xFF6A0DAD.toInt()))
        assertEquals("rosa", nombre(0xFFFFC0CB.toInt()))
    }

    @Test
    fun `dominante devuelve el color mas frecuente`() {
        val pixeles = IntArray(10) { 0xFF0000FF.toInt() }
        val d = NombreColor.dominante(pixeles)
        assertNotNull(d)
        assertEquals("azul", d!!.nombre)
        assertEquals(1f, d.fraccion, 0.0001f)
    }

    @Test
    fun `dominante sin mayoria clara devuelve nulo`() {
        // Tres colores repartidos: ninguno llega al mínimo de 0.4.
        val pixeles = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFF0000.toInt(), 0xFFFF0000.toInt(),
            0xFF00FF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FF00.toInt(),
            0xFF0000FF.toInt(), 0xFF0000FF.toInt(), 0xFF0000FF.toInt(),
        )
        assertNull(NombreColor.dominante(pixeles))
    }

    @Test
    fun `dominante sin pixeles devuelve nulo`() {
        assertNull(NombreColor.dominante(IntArray(0)))
    }
}

package com.cecapi.app.feature.modulo10_accesibilidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la detección de TalkBack. Código puro: no necesita Android. */
class DetectorTalkBackTest {

    @Test
    fun `TalkBack activo`() {
        val paquetes = listOf(DetectorTalkBack.PAQUETE_TALKBACK)

        assertEquals(EstadoLectorPantalla.TALKBACK, DetectorTalkBack.estado(paquetes))
        assertTrue(DetectorTalkBack.estaActivo(paquetes))
    }

    @Test
    fun `TalkBack de Samsung tambien cuenta`() {
        val paquetes = listOf(DetectorTalkBack.PAQUETE_TALKBACK_SAMSUNG)

        assertEquals(EstadoLectorPantalla.TALKBACK, DetectorTalkBack.estado(paquetes))
        assertTrue(DetectorTalkBack.estaActivo(paquetes))
    }

    @Test
    fun `TalkBack inactivo`() {
        assertEquals(EstadoLectorPantalla.NINGUNO, DetectorTalkBack.estado(emptyList()))
        assertFalse(DetectorTalkBack.estaActivo(emptyList()))
    }

    @Test
    fun `otro lector de pantalla activo`() {
        val paquetes = listOf("com.ejemplo.otro.lector")

        assertEquals(EstadoLectorPantalla.OTRO, DetectorTalkBack.estado(paquetes))
        assertFalse(DetectorTalkBack.estaActivo(paquetes))
    }

    @Test
    fun `TalkBack gana aunque haya otros lectores`() {
        val paquetes = listOf("com.ejemplo.otro.lector", DetectorTalkBack.PAQUETE_TALKBACK)

        assertEquals(EstadoLectorPantalla.TALKBACK, DetectorTalkBack.estado(paquetes))
        assertTrue(DetectorTalkBack.estaActivo(paquetes))
    }

    @Test
    fun `los paquetes vacios no cuentan`() {
        assertEquals(EstadoLectorPantalla.NINGUNO, DetectorTalkBack.estado(listOf("", "   ")))
    }
}

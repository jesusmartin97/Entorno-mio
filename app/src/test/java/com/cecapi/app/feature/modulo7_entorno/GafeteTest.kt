package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GafeteTest {

    @Test
    fun `acepta nombres comunes`() {
        assertTrue(Gafete.esNombre("Dylan"))
        assertTrue(Gafete.esNombre("María López"))
        assertTrue(Gafete.esNombre("Ana"))
        assertTrue(Gafete.esNombre("  Dylan  "))
        assertTrue(Gafete.esNombre("Dylan."))
    }

    @Test
    fun `acepta hasta tres palabras`() {
        assertTrue(Gafete.esNombre("Dylan Alejandro Ramírez"))
        assertFalse(Gafete.esNombre("Dylan Alejandro Ramírez Cruz"))
    }

    @Test
    fun `rechaza palabras de la escuela y la etiqueta`() {
        assertFalse(Gafete.esNombre("CECAPI"))
        assertFalse(Gafete.esNombre("Cecapi"))
        assertFalse(Gafete.esNombre("alumno"))
        assertFalse(Gafete.esNombre("Alumno"))
        assertFalse(Gafete.esNombre("CECAPI Dylan"))
        assertFalse(Gafete.esNombre("maestro"))
        assertFalse(Gafete.esNombre("UTD"))
    }

    @Test
    fun `rechaza numeros y letras sin forma de nombre`() {
        assertFalse(Gafete.esNombre("123"))
        assertFalse(Gafete.esNombre("ABC123"))
        assertFalse(Gafete.esNombre("a"))
        assertFalse(Gafete.esNombre("ABC"))
        assertFalse(Gafete.esNombre(""))
    }

    @Test
    fun `rechaza todo en minusculas`() {
        assertFalse(Gafete.esNombre("dylan"))
        assertFalse(Gafete.esNombre("maría lópez"))
    }

    @Test
    fun `rechaza frases largas`() {
        assertFalse(Gafete.esNombre("Este es el nombre completo del alumno de la escuela"))
    }

    @Test
    fun `elige el primer texto que si parece nombre`() {
        assertEquals("María López", Gafete.elegirNombre(listOf("123", "CECAPI", "María López")))
        assertEquals("Dylan", Gafete.elegirNombre(listOf("Dylan\nGrupo 3")))
        assertNull(Gafete.elegirNombre(listOf("CECAPI", "123", "alumno")))
        assertNull(Gafete.elegirNombre(emptyList()))
    }
}

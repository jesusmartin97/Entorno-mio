package com.cecapi.app.feature.modulo10_accesibilidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de cómo se arma la lectura de la pantalla (Fase 5). Código puro: no necesita Android.
 *
 * Escritas según la Fase 5; no se corren con Gradle todavía (JUnit sigue sin autorizar).
 */
class LectorPantallaTest {

    @Test
    fun `el texto en blanco no se lee`() {
        assertNull(LectorPantalla.textoDe(NodoPantalla("   ")))
        assertNull(LectorPantalla.textoDe(NodoPantalla("")))
        assertNull(LectorPantalla.fraseDe(NodoPantalla("   ")))
        assertEquals("Hola", LectorPantalla.textoDe(NodoPantalla("  Hola  ")))
    }

    @Test
    fun `un boton se anuncia como boton`() {
        assertEquals("Iniciar sesión, botón", LectorPantalla.fraseDe(NodoPantalla("Iniciar sesión", esBoton = true)))
        assertEquals("Aceptar", LectorPantalla.fraseDe(NodoPantalla("Aceptar")))
    }

    @Test
    fun `un elemento desactivado se anuncia`() {
        assertEquals(
            "Guardar, botón, desactivado",
            LectorPantalla.fraseDe(NodoPantalla("Guardar", esBoton = true, deshabilitado = true)),
        )
    }

    @Test
    fun `la lectura junta los elementos en frases`() {
        val lectura = LectorPantalla.lectura(
            listOf(
                NodoPantalla("Menú"),
                NodoPantalla("Iniciar sesión", esBoton = true),
                NodoPantalla("Crear cuenta", esBoton = true),
            ),
        )
        assertEquals("Menú. Iniciar sesión, botón. Crear cuenta, botón.", lectura)
    }

    @Test
    fun `la lectura salta lo vacío y no repite lo mismo seguido`() {
        val lectura = LectorPantalla.lectura(
            listOf(
                NodoPantalla("Usuario"),
                NodoPantalla("   "),
                NodoPantalla("Usuario"),
                NodoPantalla("Contraseña"),
            ),
        )
        assertEquals("Usuario. Contraseña.", lectura)
    }

    @Test
    fun `una pantalla sin nada que leer lo dice`() {
        assertEquals(LectorPantalla.SIN_CONTENIDO, LectorPantalla.lectura(emptyList()))
        assertEquals(LectorPantalla.SIN_CONTENIDO, LectorPantalla.lectura(listOf(NodoPantalla("  "))))
    }

    @Test
    fun `la lectura no pasa del tope`() {
        val nodos = (0 until LectorPantalla.MAXIMO + 10).map { NodoPantalla("elemento $it") }
        val lectura = LectorPantalla.lectura(nodos)
        assertTrue(lectura.contains("elemento 0"))
        assertTrue(lectura.contains("elemento ${LectorPantalla.MAXIMO - 1}"))
        assertFalse(lectura.contains("elemento ${LectorPantalla.MAXIMO}"))
    }
}

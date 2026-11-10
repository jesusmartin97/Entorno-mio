package com.cecapi.app.feature.modulo10_accesibilidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la lectura priorizada que dispara el doble volumen abajo. Código puro. */
class LectorPantallaTextoTest {

    @Test
    fun `sin texto no hay lectura`() {
        assertNull(LectorPantalla.lecturaDeTexto(emptyList()))
        assertNull(LectorPantalla.lecturaDeTexto(listOf(NodoPantalla("   "))))
    }

    @Test
    fun `prioriza parrafos y deja fuera las etiquetas cortas`() {
        val parrafo = "Este es un párrafo largo que explica lo que hay en la pantalla y sigue con más texto"
        val lectura = LectorPantalla.lecturaDeTexto(
            listOf(
                NodoPantalla("Título", esEncabezado = true),
                NodoPantalla("Abrir", esBoton = true),
                NodoPantalla(parrafo, esParrafo = true),
                NodoPantalla("Etiqueta corta"),
            ),
        )

        assertEquals("Título. $parrafo.", lectura)
    }

    @Test
    fun `un texto largo cuenta como principal`() {
        val largo = "palabra ".repeat(10).trim()
        val lectura = LectorPantalla.lecturaDeTexto(
            listOf(
                NodoPantalla("Corto"),
                NodoPantalla(largo),
            ),
        )

        assertEquals("$largo.", lectura)
    }

    @Test
    fun `si no hay parrafos lee todos los textos`() {
        val lectura = LectorPantalla.lecturaDeTexto(
            listOf(
                NodoPantalla("Uno"),
                NodoPantalla("Dos"),
                NodoPantalla("Tres"),
            ),
        )

        assertEquals("Uno. Dos. Tres.", lectura)
    }

    @Test
    fun `mantiene el orden de arriba hacia abajo`() {
        val primero = "Primer párrafo de la pantalla con suficiente texto para contar como principal"
        val segundo = "Segundo párrafo de la pantalla con suficiente texto para contar como principal"
        val lectura = LectorPantalla.lecturaDeTexto(
            listOf(
                NodoPantalla(primero, esParrafo = true),
                NodoPantalla("Un botón", esBoton = true),
                NodoPantalla(segundo, esParrafo = true),
            ),
        )

        assertTrue(lectura != null)
        assertTrue(lectura!!.indexOf(primero) < lectura.indexOf(segundo))
    }

    @Test
    fun `no repite el mismo texto seguido`() {
        val parrafo = "Un párrafo que se repite en dos nodos seguidos y solo debe leerse una vez"
        val lectura = LectorPantalla.lecturaDeTexto(
            listOf(
                NodoPantalla(parrafo, esParrafo = true),
                NodoPantalla(parrafo, esParrafo = true),
            ),
        )

        assertEquals("$parrafo.", lectura)
    }

    @Test
    fun `no pasa del tope de elementos`() {
        val nodos = (0 until LectorPantalla.MAXIMO + 5).map {
            NodoPantalla("párrafo número $it con texto suficiente para ser principal", esParrafo = true)
        }
        val lectura = LectorPantalla.lecturaDeTexto(nodos)!!

        assertTrue(lectura.contains("párrafo número 0"))
        assertTrue(lectura.contains("párrafo número ${LectorPantalla.MAXIMO - 1}"))
        assertFalse(lectura.contains("párrafo número ${LectorPantalla.MAXIMO}"))
    }
}

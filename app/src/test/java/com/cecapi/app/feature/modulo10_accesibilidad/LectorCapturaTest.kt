package com.cecapi.app.feature.modulo10_accesibilidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la lectura de una captura. Código puro: no necesita Android ni OCR real. */
class LectorCapturaTest {

    @Test
    fun `sin texto no se habla`() {
        assertNull(LectorCaptura.textoParaLeer(emptyList()))
        assertNull(LectorCaptura.textoParaLeer(listOf(BloqueCaptura("   "))))
    }

    @Test
    fun `los signos sueltos no cuentan como texto`() {
        val bloques = listOf(
            BloqueCaptura("•"),
            BloqueCaptura("---"),
            BloqueCaptura("|"),
        )

        assertNull(LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `lee los textos cortos de arriba hacia abajo`() {
        val bloques = listOf(
            BloqueCaptura("Atrás", arriba = 20, izquierda = 0),
            BloqueCaptura("Configuración", arriba = 100, izquierda = 0),
            BloqueCaptura("Guardar", arriba = 300, izquierda = 0),
        )

        assertEquals(
            "Atrás. Configuración. Guardar.",
            LectorCaptura.textoParaLeer(bloques),
        )
    }

    @Test
    fun `lee tambien los textos cortos aunque haya parrafos largos`() {
        val parrafo = "Este es un párrafo largo que sí se puede leer completo y contiene la idea principal de la pantalla."
        val bloques = listOf(
            BloqueCaptura("Título", arriba = 0),
            BloqueCaptura(parrafo, arriba = 100),
            BloqueCaptura("Aceptar", arriba = 200),
        )

        assertEquals(
            "Título. $parrafo Aceptar.",
            LectorCaptura.textoParaLeer(bloques),
        )
    }

    @Test
    fun `ordena por posicion aunque el OCR venga desordenado`() {
        val bloques = listOf(
            BloqueCaptura("Tercero", arriba = 300),
            BloqueCaptura("Primero", arriba = 100),
            BloqueCaptura("Segundo", arriba = 200),
        )

        assertEquals(
            "Primero. Segundo. Tercero.",
            LectorCaptura.textoParaLeer(bloques),
        )
    }

    @Test
    fun `no repite el mismo texto seguido`() {
        val bloques = listOf(
            BloqueCaptura("Nombre", arriba = 100),
            BloqueCaptura("Nombre", arriba = 110),
        )

        assertEquals("Nombre.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `junta las letras separadas de un titulo`() {
        val bloques = listOf(BloqueCaptura("T. Í. T. U. L. O.", arriba = 100))

        assertEquals("Título.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `junta letras separadas con espacios`() {
        val bloques = listOf(BloqueCaptura("H O L A mundo", arriba = 100))

        assertEquals("Hola mundo.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `une una palabra cortada al final de una linea`() {
        val bloques = listOf(BloqueCaptura("invi-\ntas", arriba = 100, abajo = 140))

        assertEquals("invitas.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `une una palabra partida en dos bloques de la misma linea`() {
        val bloques = listOf(
            BloqueCaptura("Po-", arriba = 100, izquierda = 0, abajo = 140),
            BloqueCaptura("ne", arriba = 100, izquierda = 40, abajo = 140),
        )

        assertEquals("Pone.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `une bloques de la misma linea sin meter punto`() {
        val bloques = listOf(
            BloqueCaptura("El texto", arriba = 100, izquierda = 0, abajo = 140),
            BloqueCaptura("importante", arriba = 100, izquierda = 100, abajo = 140),
        )

        assertEquals("El texto importante.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `no corta una linea que continua en minuscula`() {
        val bloques = listOf(
            BloqueCaptura("una línea", arriba = 100, abajo = 140),
            BloqueCaptura("continúa", arriba = 150, abajo = 190),
        )

        assertEquals("una línea continúa.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `quita caracteres invisibles dentro de una palabra`() {
        val bloques = listOf(BloqueCaptura("in\u200Bvitas", arriba = 100))

        assertEquals("invitas.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `no junta palabras normales de una letra`() {
        val bloques = listOf(BloqueCaptura("a y o", arriba = 100))

        assertEquals("a y o.", LectorCaptura.textoParaLeer(bloques))
    }

    @Test
    fun `normaliza mayusculas y confusiones tipicas del ocr`() {
        val bloques = listOf(
            BloqueCaptura(
                "LA INVITASA LA CENA. SE PONEA CANTAR. TI0S V ACEROUE. TİAS. PINATA.",
                arriba = 100,
            ),
        )

        val lectura = LectorCaptura.textoParaLeer(bloques)!!

        assertTrue(lectura.contains("Invitas a"))
        assertTrue(lectura.contains("Pone a"))
        assertTrue(lectura.contains("Tíos"))
        assertTrue(lectura.contains("Acerque"))
        assertTrue(lectura.contains("Tías"))
        assertTrue(lectura.contains("Piñata"))
        assertFalse(lectura.contains("PONEA"))
        assertFalse(lectura.contains("OUE"))
    }

    @Test
    fun `junta las lineas de un mismo bloque`() {
        val bloques = listOf(
            BloqueCaptura("Primera línea\nsegunda línea", arriba = 100),
        )

        assertEquals("Primera línea segunda línea.", LectorCaptura.textoParaLeer(bloques))
    }
}

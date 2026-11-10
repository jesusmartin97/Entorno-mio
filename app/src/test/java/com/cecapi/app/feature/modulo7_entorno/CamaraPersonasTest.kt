package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas puras de la cámara: posición y frases de objetos. */
class CamaraPersonasTest {

    private fun caja(izquierda: Float, arriba: Float, derecha: Float, abajo: Float) =
        CajaCamara(izquierda, arriba, derecha, abajo)

    @Test
    fun `una caja a la izquierda se dice a tu izquierda`() {
        assertEquals("a tu izquierda", CamaraPersonas.posicion(caja(0.05f, 0.2f, 0.25f, 0.8f)))
    }

    @Test
    fun `una caja al centro se dice al centro`() {
        assertEquals("al centro", CamaraPersonas.posicion(caja(0.40f, 0.2f, 0.60f, 0.8f)))
    }

    @Test
    fun `una caja a la derecha se dice a tu derecha`() {
        assertEquals("a tu derecha", CamaraPersonas.posicion(caja(0.75f, 0.2f, 0.95f, 0.8f)))
    }

    @Test
    fun `las cajas cercanas al centro no se van a los lados`() {
        assertEquals("al centro", CamaraPersonas.posicion(caja(0.34f, 0.0f, 0.40f, 1.0f)))
        assertEquals("al centro", CamaraPersonas.posicion(caja(0.60f, 0.0f, 0.66f, 1.0f)))
    }

    @Test
    fun `frase de objetos une hasta tres descripciones`() {
        val frase = CamaraPersonas.fraseObjetos(
            listOf(
                "una silla a tu izquierda",
                "una mesa al centro",
                "una botella a tu derecha",
                "un cuadro que no debe entrar",
            ),
        )
        assertEquals(
            "Veo una silla a tu izquierda, una mesa al centro y una botella a tu derecha.",
            frase,
        )
        assertFalse(frase!!.contains("cuadro"))
    }

    @Test
    fun `sin objetos no se inventa una frase`() {
        assertNull(CamaraPersonas.fraseObjetos(emptyList()))
        assertNull(CamaraPersonas.fraseObjetos(listOf(" ", "")))
    }

    @Test
    fun `la frase de objetos vacia no habla de ropa`() {
        val frase = CamaraPersonas.fraseObjetos(listOf("una silla al centro"))
        assertTrue(frase!!.contains("silla"))
        assertFalse(frase.contains("ropa"))
    }
}

/** Pruebas de selección de personas, encuadre, ropa y frases de la Fase B. */
class CamaraPersonasFaseBTest {

    private fun caja(izquierda: Float = 0.3f, arriba: Float = 0.1f, derecha: Float = 0.7f, abajo: Float = 0.9f) =
        CajaCamara(izquierda, arriba, derecha, abajo)

    private fun persona(izquierda: Float = 0.3f, derecha: Float = 0.7f, confianza: Float = 0.9f) =
        PersonaCamara(caja(izquierda = izquierda, derecha = derecha), confianza)

    @Test
    fun `una sola persona se conserva`() {
        val lista = CamaraPersonas.personas(listOf(persona()))
        assertEquals(1, lista.size)
        assertEquals(0.9f, lista[0].confianza)
    }

    @Test
    fun `varias personas se ordenan de la mas grande a la mas pequena`() {
        val lejana = persona(izquierda = 0.45f, derecha = 0.55f, confianza = 0.95f)
        val cercana = persona(izquierda = 0.2f, derecha = 0.8f, confianza = 0.8f)
        val lista = CamaraPersonas.personas(listOf(lejana, cercana))
        assertEquals(2, lista.size)
        assertEquals(0.8f, lista[0].confianza)
        assertEquals(0.95f, lista[1].confianza)
    }

    @Test
    fun `sin personas la lista queda vacia`() {
        assertTrue(CamaraPersonas.personas(emptyList()).isEmpty())
    }

    @Test
    fun `una persona con confianza baja no cuenta`() {
        val lista = CamaraPersonas.personas(listOf(persona(confianza = 0.49f)))
        assertTrue(lista.isEmpty())
    }

    @Test
    fun `la confianza minima exacta si cuenta`() {
        assertEquals(1, CamaraPersonas.personas(listOf(persona(confianza = 0.5f))).size)
    }

    @Test
    fun `una persona muy baja esta lejos`() {
        assertEquals(Encuadre.LEJOS, CamaraPersonas.encuadre(caja(arriba = 0.1f, abajo = 0.19f)))
    }

    @Test
    fun `una persona pegada a la orilla esta cortada`() {
        assertEquals(Encuadre.CORTADA, CamaraPersonas.encuadre(caja(izquierda = 0.02f)))
        assertEquals(Encuadre.CORTADA, CamaraPersonas.encuadre(caja(abajo = 0.95f)))
    }

    @Test
    fun `una persona completa y cerca no tiene problema de encuadre`() {
        assertNull(CamaraPersonas.encuadre(caja(izquierda = 0.2f, derecha = 0.8f, abajo = 0.88f)))
    }

    @Test
    fun `el color dominante de la ropa se acepta`() {
        val muestra = MuestraRopa(colores = List(8) { "blanco" } + List(2) { "negro" }, brilloMedio = 0.9f)
        assertEquals("blanco", CamaraPersonas.colorRopa(muestra))
    }

    @Test
    fun `una ropa muy mezclada no tiene color claro`() {
        val muestra = MuestraRopa(colores = List(5) { "blanco" } + List(5) { "negro" }, brilloMedio = 0.5f)
        assertNull(CamaraPersonas.colorRopa(muestra))
    }

    @Test
    fun `una zona oscura que no es negra no se adivina`() {
        val muestra = MuestraRopa(colores = List(10) { "azul" }, brilloMedio = 0.05f)
        assertNull(CamaraPersonas.colorRopa(muestra))
    }

    @Test
    fun `un negro claro si se acepta como negro`() {
        val muestra = MuestraRopa(colores = List(10) { "negro" }, brilloMedio = 0.03f)
        assertEquals("negro", CamaraPersonas.colorRopa(muestra))
    }

    @Test
    fun `sin pixeles no hay color de ropa`() {
        assertNull(CamaraPersonas.colorRopa(MuestraRopa(emptyList())))
    }

    @Test
    fun `una persona sin gafete se describe con posicion y ropa`() {
        val frase = CamaraPersonas.frasePersonas(
            cantidad = 1,
            posicion = "al centro",
            encuadre = null,
            ropa = RopaCamara("blanco", "azul"),
        )
        assertEquals("Hay una persona al centro con ropa blanca arriba y azul abajo.", frase)
        assertFalse(frase!!.contains("gafete"))
    }

    @Test
    fun `con gafete se agrega el nombre al final`() {
        val frase = CamaraPersonas.frasePersonas(
            cantidad = 1,
            posicion = "a tu izquierda",
            encuadre = null,
            ropa = RopaCamara("blanco", "azul"),
            nombre = "Dylan",
        )
        assertEquals(
            "Hay una persona a tu izquierda con ropa blanca arriba y azul abajo que tiene un gafete que dice Dylan.",
            frase,
        )
    }

    @Test
    fun `varias personas dicen cuantas y describen a la mas cercana`() {
        val frase = CamaraPersonas.frasePersonas(
            cantidad = 3,
            posicion = "a tu derecha",
            encuadre = null,
            ropa = RopaCamara(null, null),
        )
        assertEquals("Hay 3 personas. La más cercana está a tu derecha con ropa de color no claro.", frase)
    }

    @Test
    fun `la ropa no clara se dice sin adivinar`() {
        val frase = CamaraPersonas.frasePersonas(
            cantidad = 1,
            posicion = "al centro",
            encuadre = null,
            ropa = RopaCamara("gris", null),
        )
        assertEquals("Hay una persona al centro con ropa gris arriba y de color no claro abajo.", frase)
    }

    @Test
    fun `lejos solo avisa y no describe ropa`() {
        val frase = CamaraPersonas.frasePersonas(
            cantidad = 1,
            posicion = "al centro",
            encuadre = Encuadre.LEJOS,
            ropa = RopaCamara("blanco", "azul"),
        )
        assertEquals("La persona está lejos, acércate.", frase)
    }

    @Test
    fun `cortada solo avisa y no describe ropa`() {
        val frase = CamaraPersonas.frasePersonas(
            cantidad = 2,
            posicion = "a tu izquierda",
            encuadre = Encuadre.CORTADA,
            ropa = RopaCamara("blanco", "azul"),
        )
        assertEquals("Hay 2 personas. La más cercana está cortada, aléjate un poco.", frase)
    }

    @Test
    fun `sin personas no hay frase ni se habla de ropa`() {
        assertNull(
            CamaraPersonas.frasePersonas(
                cantidad = 0,
                posicion = "al centro",
                encuadre = null,
                ropa = RopaCamara("blanco", "azul"),
            ),
        )
    }
}

package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraductorPantallasTest {

    private fun detector(etiqueta: String, confianza: Float = 0.9f, posicion: String? = "al centro") =
        SenalEtiqueta(etiqueta, confianza, posicion, OrigenEtiqueta.DETECTOR)

    private fun mlkit(etiqueta: String, confianza: Float = 0.9f, posicion: String? = null) =
        SenalEtiqueta(etiqueta, confianza, posicion, OrigenEtiqueta.ETIQUETADOR)

    private fun celular(
        confianza: Float = 0.8f,
        ancho: Float = 0.08f,
        alto: Float = 0.20f,
        posicion: String? = "al centro",
    ) = SenalEtiqueta(
        etiqueta = "cell phone",
        confianza = confianza,
        posicion = posicion,
        origen = OrigenEtiqueta.DETECTOR,
        caja = CajaSenal(ancho, alto),
    )

    @Test
    fun `cell phone con caja pequena se dice celular`() {
        assertEquals(
            "Hay un celular al centro.",
            TraductorPantallas.fraseIndividual(celular()),
        )
        assertEquals("celular", TraductorPantallas.describir(listOf(celular()))?.etiqueta)
    }

    @Test
    fun `cell phone sin caja no se afirma`() {
        assertNull(
            TraductorPantallas.describir(
                listOf(SenalEtiqueta("cell phone", 0.90f, "al centro")),
            ),
        )
    }

    @Test
    fun `una laptop grande con teclado no se dice celular`() {
        val resultado = TraductorPantallas.describir(
            listOf(
                celular(confianza = 0.57f, ancho = 0.60f, alto = 0.20f),
                detector("keyboard", 0.70f),
            ),
        )
        assertEquals("Hay un laptop al centro.", resultado?.frase)
        assertFalse(resultado!!.frase.contains("celular"))
    }

    @Test
    fun `una caja ancha sin teclado se queda en pantalla`() {
        val resultado = TraductorPantallas.describir(
            listOf(celular(confianza = 0.57f, ancho = 0.60f, alto = 0.20f)),
        )
        assertEquals("Hay una pantalla al centro.", resultado?.frase)
        assertFalse(resultado!!.frase.contains("celular"))
    }

    @Test
    fun `una caja ancha con Computer de ML Kit se dice laptop`() {
        val resultado = TraductorPantallas.describir(
            listOf(
                celular(confianza = 0.57f, ancho = 0.60f, alto = 0.20f),
                mlkit("Computer", 0.80f),
            ),
        )
        assertEquals("Hay un laptop al centro.", resultado?.frase)
    }

    @Test
    fun `un celular pequeno y seguro si se dice celular`() {
        val resultado = TraductorPantallas.describir(
            listOf(celular(confianza = 0.80f, ancho = 0.08f, alto = 0.20f)),
        )
        assertEquals("Hay un celular al centro.", resultado?.frase)
        assertEquals("celular", resultado?.etiqueta)
    }

    @Test
    fun `un cell phone pequeno con confianza media no se menciona`() {
        assertNull(
            TraductorPantallas.describir(
                listOf(celular(confianza = 0.55f, ancho = 0.08f, alto = 0.20f)),
            ),
        )
    }

    @Test
    fun `una caja apaisada no se dice celular`() {
        val resultado = TraductorPantallas.describir(
            listOf(celular(confianza = 0.80f, ancho = 0.20f, alto = 0.10f)),
        )
        assertEquals("Hay una pantalla al centro.", resultado?.frase)
    }

    @Test
    fun `traduce tv a pantalla`() {
        assertEquals(
            "Hay una pantalla al centro.",
            TraductorPantallas.fraseIndividual(detector("tv")),
        )
    }

    @Test
    fun `traduce laptop a laptop con su posicion`() {
        assertEquals(
            "Hay un laptop a tu izquierda.",
            TraductorPantallas.fraseIndividual(detector("laptop", posicion = "a tu izquierda")),
        )
    }

    @Test
    fun `traduce keyboard a teclado`() {
        assertEquals(
            "Hay un teclado al centro.",
            TraductorPantallas.fraseIndividual(detector("keyboard")),
        )
    }

    @Test
    fun `traduce mouse a raton`() {
        assertEquals(
            "Hay un ratón a tu derecha.",
            TraductorPantallas.fraseIndividual(detector("mouse", posicion = "a tu derecha")),
        )
    }

    @Test
    fun `traduce remote a control remoto`() {
        assertEquals(
            "Hay un control remoto al centro.",
            TraductorPantallas.fraseIndividual(detector("remote")),
        )
    }

    @Test
    fun `traduce Computer de ML Kit a pantalla sin afirmar marca`() {
        val frase = TraductorPantallas.fraseIndividual(mlkit("Computer"))
        assertEquals("Hay una pantalla.", frase)
        assertFalse(frase!!.contains("televisor"))
        assertFalse(frase.contains("monitor"))
    }

    @Test
    fun `traduce Television de ML Kit a pantalla sin afirmar televisor`() {
        val frase = TraductorPantallas.fraseIndividual(mlkit("Television"))
        assertEquals("Hay una pantalla.", frase)
        assertFalse(frase!!.contains("televisor"))
        assertFalse(frase.contains("monitor"))
    }

    @Test
    fun `pantalla teclado y raton forman una computadora de escritorio`() {
        val resultado = TraductorPantallas.describir(
            listOf(detector("tv"), detector("keyboard"), detector("mouse")),
        )
        assertEquals(
            "Hay una computadora de escritorio: pantalla, teclado y ratón.",
            resultado?.frase,
        )
        assertEquals("computadora de escritorio", resultado?.etiqueta)
    }

    @Test
    fun `con laptop ademas se dice computadora de escritorio o laptop`() {
        val resultado = TraductorPantallas.describir(
            listOf(detector("tv"), detector("laptop"), detector("keyboard"), detector("mouse")),
        )
        assertEquals(
            "Hay una computadora de escritorio o laptop: pantalla, teclado y ratón.",
            resultado?.frase,
        )
    }

    @Test
    fun `Computer de ML Kit con teclado y raton completa la computadora`() {
        val resultado = TraductorPantallas.describir(
            listOf(mlkit("Computer"), detector("keyboard"), detector("mouse")),
        )
        assertEquals(
            "Hay una computadora de escritorio: pantalla, teclado y ratón.",
            resultado?.frase,
        )
    }

    @Test
    fun `pantalla sola no se convierte en computadora`() {
        assertEquals(
            "Hay una pantalla al centro.",
            TraductorPantallas.describir(listOf(detector("tv")))?.frase,
        )
    }

    @Test
    fun `la pantalla y el control extra solo sugieren televisor o monitor`() {
        val frase = TraductorPantallas.describir(
            listOf(detector("tv"), detector("remote")),
        )?.frase
        assertTrue(frase!!.contains("Hay una pantalla al centro"))
        assertTrue(frase.contains("parece televisor o monitor"))
        assertFalse(frase.contains("es un televisor"))
        assertFalse(frase.contains("es un monitor"))
    }

    @Test
    fun `sin control no se menciona televisor ni monitor`() {
        val frase = TraductorPantallas.describir(listOf(detector("tv")))?.frase
        assertFalse(frase!!.contains("televisor"))
        assertFalse(frase.contains("monitor"))
    }

    @Test
    fun `un laptop contradice a Television de ML Kit y gana el laptop`() {
        val frase = TraductorPantallas.describir(
            listOf(detector("laptop"), mlkit("Television")),
        )?.frase
        assertEquals("Hay un laptop al centro.", frase)
        assertFalse(frase!!.contains("televisor"))
        assertFalse(frase.contains("monitor"))
    }

    @Test
    fun `tv y Television no duplican la pantalla`() {
        val frase = TraductorPantallas.describir(
            listOf(detector("tv"), mlkit("Television")),
        )?.frase
        assertEquals("Hay una pantalla al centro.", frase)
        assertEquals(1, Regex("pantalla").findAll(frase!!).count())
    }

    @Test
    fun `el umbral de pantalla es mas alto`() {
        assertNull(TraductorPantallas.describir(listOf(detector("tv", 0.64f))))
        assertEquals(
            "Hay una pantalla al centro.",
            TraductorPantallas.describir(listOf(detector("tv", 0.65f)))?.frase,
        )
    }

    @Test
    fun `cada clase tiene su umbral`() {
        assertNull(TraductorPantallas.describir(listOf(detector("laptop", 0.54f))))
        assertEquals(
            "Hay un laptop al centro.",
            TraductorPantallas.describir(listOf(detector("laptop", 0.55f)))?.frase,
        )
        assertNull(TraductorPantallas.describir(listOf(detector("keyboard", 0.49f))))
        assertEquals(
            "Hay un teclado al centro.",
            TraductorPantallas.describir(listOf(detector("keyboard", 0.50f)))?.frase,
        )
        assertNull(TraductorPantallas.describir(listOf(mlkit("Television", 0.74f))))
        assertEquals(
            "Hay una pantalla.",
            TraductorPantallas.describir(listOf(mlkit("Television", 0.75f)))?.frase,
        )
    }

    @Test
    fun `no usa etiquetas que no existen en los modelos reales`() {
        assertFalse(TraductorPantallas.esEtiquetaConocida("monitor", OrigenEtiqueta.ETIQUETADOR))
        assertFalse(TraductorPantallas.esEtiquetaConocida("screen", OrigenEtiqueta.ETIQUETADOR))
        assertFalse(TraductorPantallas.esEtiquetaConocida("remote control", OrigenEtiqueta.DETECTOR))
        assertFalse(TraductorPantallas.esEtiquetaConocida("laptop", OrigenEtiqueta.ETIQUETADOR))
        assertFalse(TraductorPantallas.esEtiquetaConocida("tv", OrigenEtiqueta.ETIQUETADOR))
        assertNull(TraductorPantallas.fraseIndividual(mlkit("Monitor", 0.99f)))
        assertNull(TraductorPantallas.describir(listOf(mlkit("Monitor", 0.99f))))
        assertNull(TraductorPantallas.describir(listOf(detector("computer", 0.99f))))
    }

    @Test
    fun `sin senales no hay frase`() {
        assertNull(TraductorPantallas.describir(emptyList()))
    }
}

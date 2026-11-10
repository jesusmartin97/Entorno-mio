package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas puras de tamaño y confianza. Los umbrales se pasan explícitos para probar la lógica sin
 * depender de los valores provisionales, que cambian cuando se ajustan con fotos reales.
 */
class ReglasEntornoTest {

    private fun caja(ancho: Float, alto: Float) = CajaSenal(ancho, alto)

    @Test
    fun `sin caja no se afirma que sea celular`() {
        assertEquals(JuicioCelular.NADA, ReglasEntorno.juicioCelular(0.95f, null))
    }

    @Test
    fun `una caja ancha no es celular aunque la confianza sea alta`() {
        assertEquals(
            JuicioCelular.PANTALLA,
            ReglasEntorno.juicioCelular(
                confianza = 0.80f,
                caja = caja(0.40f, 0.50f),
                anchoMaximo = 0.25f,
                aspectoMaximo = 1.2f,
            ),
        )
    }

    @Test
    fun `una caja apaisada no es celular aunque sea pequena`() {
        assertEquals(
            JuicioCelular.PANTALLA,
            ReglasEntorno.juicioCelular(
                confianza = 0.80f,
                caja = caja(0.22f, 0.10f),
                anchoMaximo = 0.25f,
                aspectoMaximo = 1.2f,
            ),
        )
    }

    @Test
    fun `una caja pequena con confianza alta si es celular`() {
        assertEquals(
            JuicioCelular.CELULAR,
            ReglasEntorno.juicioCelular(
                confianza = 0.80f,
                caja = caja(0.08f, 0.20f),
                anchoMaximo = 0.25f,
                aspectoMaximo = 1.2f,
                umbralCelular = 0.60f,
            ),
        )
    }

    @Test
    fun `una caja pequena con confianza baja no se menciona`() {
        assertEquals(
            JuicioCelular.NADA,
            ReglasEntorno.juicioCelular(
                confianza = 0.55f,
                caja = caja(0.08f, 0.20f),
                anchoMaximo = 0.25f,
                aspectoMaximo = 1.2f,
                umbralCelular = 0.60f,
            ),
        )
    }

    @Test
    fun `una caja ancha con confianza baja no alcanza ni para pantalla`() {
        assertEquals(
            JuicioCelular.NADA,
            ReglasEntorno.juicioCelular(
                confianza = 0.45f,
                caja = caja(0.40f, 0.50f),
                anchoMaximo = 0.25f,
                umbralPantalla = 0.50f,
            ),
        )
    }

    @Test
    fun `una silla chica o lejana se descarta`() {
        assertFalse(
            ReglasEntorno.sillaValida(
                confianza = 0.90f,
                caja = caja(0.31f, 0.20f),
                hayPantalla = false,
                areaMinima = 0.30f,
                umbral = 0.60f,
            ),
        )
    }

    @Test
    fun `una silla grande y segura se permite`() {
        assertTrue(
            ReglasEntorno.sillaValida(
                confianza = 0.72f,
                caja = caja(0.57f, 0.90f),
                hayPantalla = false,
                areaMinima = 0.30f,
                umbral = 0.60f,
            ),
        )
    }

    @Test
    fun `una silla con pantalla laptop o teclado en la foto se descarta`() {
        assertFalse(
            ReglasEntorno.sillaValida(
                confianza = 0.90f,
                caja = caja(0.80f, 0.80f),
                hayPantalla = true,
                areaMinima = 0.30f,
                umbral = 0.60f,
            ),
        )
    }

    @Test
    fun `una silla sin caja no se puede afirmar`() {
        assertFalse(ReglasEntorno.sillaValida(0.90f, null, false, 0.30f, 0.60f))
    }

    @Test
    fun `el color necesita confianza y color uniforme`() {
        assertTrue(ReglasEntorno.colorConfiable(0.80f, 0.60f, umbral = 0.75f))
        assertFalse(ReglasEntorno.colorConfiable(0.50f, 0.90f, umbral = 0.75f))
        assertFalse(ReglasEntorno.colorConfiable(0.90f, 0.30f, umbral = 0.75f))
    }

    @Test
    fun `los objetos que no son silla no se filtran`() {
        assertTrue(
            ReglasEntorno.conservarObjeto("laptop", 0.10f, caja = null, hayPantallaEnFoto = false),
        )
        assertTrue(
            ReglasEntorno.conservarObjeto("bottle", 0.10f, caja = null, hayPantallaEnFoto = true),
        )
    }

    @Test
    fun `con los umbrales provisionales un celular pequeno y seguro si se dice`() {
        assertEquals(
            JuicioCelular.CELULAR,
            ReglasEntorno.juicioCelular(0.80f, caja(0.08f, 0.20f)),
        )
    }

    @Test
    fun `con los umbrales provisionales una caja ancha no es celular`() {
        assertEquals(
            JuicioCelular.PANTALLA,
            ReglasEntorno.juicioCelular(0.57f, caja(0.60f, 0.20f)),
        )
    }

    @Test
    fun `con los umbrales provisionales una silla chica no se menciona`() {
        assertFalse(
            ReglasEntorno.conservarObjeto(
                etiqueta = "chair",
                confianza = 0.90f,
                caja = caja(0.31f, 0.20f),
                hayPantallaEnFoto = false,
            ),
        )
    }

    @Test
    fun `con los umbrales provisionales una silla grande y segura se permite`() {
        assertTrue(
            ReglasEntorno.conservarObjeto(
                etiqueta = "chair",
                confianza = 0.72f,
                caja = caja(0.57f, 0.90f),
                hayPantallaEnFoto = false,
            ),
        )
    }

    @Test
    fun `con los umbrales provisionales una silla con pantalla no se menciona`() {
        assertFalse(
            ReglasEntorno.conservarObjeto(
                etiqueta = "chair",
                confianza = 0.72f,
                caja = caja(0.57f, 0.90f),
                hayPantallaEnFoto = true,
            ),
        )
    }

    @Test
    fun `los umbrales provisionales quedan en constantes faciles de ajustar`() {
        assertEquals(0.55f, ReglasEntorno.ANCHO_MAXIMO_CELULAR, 0.0001f)
        assertEquals(1.20f, ReglasEntorno.ASPECTO_MAXIMO_CELULAR, 0.0001f)
        assertEquals(0.60f, ReglasEntorno.UMBRAL_CELULAR, 0.0001f)
        assertEquals(0.30f, ReglasEntorno.AREA_MINIMA_SILLA, 0.0001f)
        assertEquals(0.60f, ReglasEntorno.UMBRAL_SILLA, 0.0001f)
        assertEquals(0.75f, ReglasEntorno.UMBRAL_COLOR, 0.0001f)
    }
}

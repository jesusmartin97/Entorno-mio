package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la decisión de [MonedaMx] con probabilidades simuladas. No corre el modelo: fija el
 * comportamiento de los umbrales provisionales (0.85 pedida / 0.95 automática) y de las reglas de acuerdo.
 */
class MonedaMxTest {

    private fun lecturas(vararg pares: Pair<String, Float>) = pares.toList()

    @Test
    fun `etiqueta con numero se convierte en denominacion`() {
        assertEquals(DenominacionMoneda.M5, MonedaMx.desdeEtiqueta("5pesos"))
        assertEquals(DenominacionMoneda.M10, MonedaMx.desdeEtiqueta("$10"))
        assertEquals(DenominacionMoneda.M20, MonedaMx.desdeEtiqueta("20 pesos"))
        assertEquals(DenominacionMoneda.M1, MonedaMx.desdeEtiqueta("1 peso"))
        assertEquals(DenominacionMoneda.M2, MonedaMx.desdeEtiqueta("2"))
    }

    @Test
    fun `nombre de clase generico no se toma por moneda`() {
        // Si monedas_labels.txt no coincide con el modelo, las clases quedan como "clase_2": no es "2 pesos".
        assertNull(MonedaMx.desdeEtiqueta("clase_2"))
        assertNull(MonedaMx.desdeEtiqueta("moneda"))
    }

    @Test
    fun `sin lecturas no se decide nada`() {
        val r = MonedaMx.decidir(emptyList())
        assertNull(r.denominacion)
        assertEquals(MotivoMoneda.NO_CLARO, r.motivo)
        assertFalse(r.esSegura)
    }

    @Test
    fun `moneda pedida con confianza alta y acuerdo es segura`() {
        val r = MonedaMx.decidir(
            lecturas("5pesos" to 0.90f, "1peso" to 0.10f),
            listOf(
                lecturas("5pesos" to 0.91f, "1peso" to 0.09f),
                lecturas("5pesos" to 0.88f, "1peso" to 0.12f),
                lecturas("1peso" to 0.55f, "5pesos" to 0.45f), // una variante se equivoca: con 2 de 3 basta
            ),
        )
        assertTrue(r.esSegura)
        assertEquals(DenominacionMoneda.M5, r.denominacion)
    }

    @Test
    fun `moneda pedida con confianza media es dudosa`() {
        val r = MonedaMx.decidir(lecturas("5pesos" to 0.70f, "1peso" to 0.15f))
        assertFalse(r.esSegura)
        assertEquals(MotivoMoneda.DUDOSA, r.motivo)
        assertEquals(DenominacionMoneda.M5, r.denominacion)
    }

    @Test
    fun `moneda pedida con confianza baja no se reconoce`() {
        val r = MonedaMx.decidir(lecturas("5pesos" to 0.50f, "1peso" to 0.20f))
        assertNull(r.denominacion)
        assertEquals(MotivoMoneda.NO_CLARO, r.motivo)
    }

    @Test
    fun `foto normal exige que las tres variantes coincidan`() {
        val r = MonedaMx.decidir(
            lecturas("10pesos" to 0.97f, "20pesos" to 0.03f),
            listOf(
                lecturas("10pesos" to 0.97f),
                lecturas("10pesos" to 0.95f),
                lecturas("20pesos" to 0.80f), // el espejo ve otra cosa: el modelo está adivinando
            ),
            automatico = true,
        )
        assertFalse(r.esSegura)
        assertEquals(MotivoMoneda.DUDOSA, r.motivo)
    }

    @Test
    fun `foto normal con el modelo seguro es segura`() {
        val r = MonedaMx.decidir(
            lecturas("10pesos" to 0.97f, "20pesos" to 0.03f),
            listOf(
                lecturas("10pesos" to 0.97f),
                lecturas("10pesos" to 0.96f),
                lecturas("10pesos" to 0.92f),
            ),
            automatico = true,
        )
        assertTrue(r.esSegura)
        assertEquals(DenominacionMoneda.M10, r.denominacion)
    }

    @Test
    fun `foto normal con una variante floja es dudosa`() {
        val r = MonedaMx.decidir(
            lecturas("10pesos" to 0.97f, "20pesos" to 0.03f),
            listOf(
                lecturas("10pesos" to 0.97f),
                lecturas("10pesos" to 0.96f),
                lecturas("10pesos" to 0.65f), // por debajo de 0.70
            ),
            automatico = true,
        )
        assertFalse(r.esSegura)
        assertEquals(MotivoMoneda.DUDOSA, r.motivo)
    }

    @Test
    fun `clase especial sin_moneda`() {
        assertEquals(MotivoMoneda.NO_HAY_MONEDA, MonedaMx.decidir(lecturas("sin_moneda" to 0.80f)).motivo)
        assertEquals(MotivoMoneda.NO_CLARO, MonedaMx.decidir(lecturas("sin_moneda" to 0.50f)).motivo)
    }

    @Test
    fun `clase especial varias`() {
        assertEquals(MotivoMoneda.VARIAS, MonedaMx.decidir(lecturas("varias" to 0.90f)).motivo)
    }

    @Test
    fun `solo se dice como hecho lo seguro`() {
        assertTrue(
            MonedaMx.frase(MonedaMx.decidir(lecturas("5pesos" to 0.95f)))
                .startsWith("Es una moneda de cinco pesos."),
        )
        assertTrue(
            MonedaMx.frase(MonedaMx.decidir(lecturas("5pesos" to 0.70f)))
                .startsWith("Parece una moneda de cinco pesos"),
        )
    }
}

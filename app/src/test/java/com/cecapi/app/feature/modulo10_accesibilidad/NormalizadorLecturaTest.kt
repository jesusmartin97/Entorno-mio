package com.cecapi.app.feature.modulo10_accesibilidad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizadorLecturaTest {

    @Test
    fun `lee pesos y no dolares cuando el texto trae el simbolo de peso`() {
        val texto = NormalizadorLectura.normalizar("PRECIO $1,200.00. OTRO $50.")

        assertTrue(texto.contains("1,200 pesos"))
        assertTrue(texto.contains("50 pesos"))
        assertFalse(texto.contains("dólares"))
    }

    @Test
    fun `respeta los dolares cuando el texto dice USD`() {
        val texto = NormalizadorLectura.normalizar("US$20, USD 35 y $40 USD.")

        assertTrue(texto.contains("20 dólares"))
        assertTrue(texto.contains("35 dólares"))
        assertTrue(texto.contains("40 dólares"))
        assertFalse(texto.contains("pesos"))
    }

    @Test
    fun `convierte pulgadas voltios hercios y vatios`() {
        val texto = NormalizadorLectura.normalizar(
            "PANTALLA DE 28\" (71 cm). 100-240 V~ 50/60 Hz. CONSUMO 45 W.",
        )

        assertTrue(texto.contains("28 pulgadas"))
        assertTrue(texto.contains("71 centímetros"))
        assertTrue(texto.contains("100 a 240 voltios"))
        assertTrue(texto.contains("50 o 60 hercios"))
        assertTrue(texto.contains("45 vatios"))
    }

    @Test
    fun `convierte Wh mAh y medidas con singular y plural`() {
        val texto = NormalizadorLectura.normalizar(
            "BATERIA 5000 mAh. ENERGIA 31.98 Wh. PESO 2.5 kg. CABLE 1 m. CAJA 1 lb.",
        )

        assertTrue(texto.contains("5000 miliamperios hora"))
        assertTrue(texto.contains("31.98 vatios hora"))
        assertTrue(texto.contains("2.5 kilogramos"))
        assertTrue(texto.contains("1 metro"))
        assertTrue(texto.contains("1 libra"))
    }

    @Test
    fun `convierte abreviaturas comunes de etiquetas`() {
        val texto = NormalizadorLectura.normalizar(
            "NOM-001. RFC ABC010101. C.P. 01000. Tel. 5555555555. S.A. de C.V. IVA incluido.",
        )

        assertTrue(texto.contains("Norma Oficial Mexicana"))
        assertTrue(texto.contains("Registro Federal de Contribuyentes"))
        assertTrue(texto.contains("Código Postal"))
        assertTrue(texto.contains("Teléfono"))
        assertTrue(texto.contains("Sociedad Anónima de Capital Variable"))
        assertTrue(texto.contains("iva"))
    }

    @Test
    fun `lee unidades escritas en ingles`() {
        val texto = NormalizadorLectura.normalizar("45 watts, 120 volts, 28 inches, 5 pounds, 2 gallons.")

        assertTrue(texto.contains("45 vatios"))
        assertTrue(texto.contains("120 voltios"))
        assertTrue(texto.contains("28 pulgadas"))
        assertTrue(texto.contains("5 libras"))
        assertTrue(texto.contains("2 galones"))
    }

    @Test
    fun `pronuncia modelo en minusculas para que no lo deletree`() {
        val texto = NormalizadorLectura.normalizar("MODELO: VLEDTV2814")

        assertTrue(texto.contains("modelo:"))
        assertFalse(texto.contains("Modelo:"))
    }

    @Test
    fun `no convierte una a en amperios`() {
        val texto = NormalizadorLectura.normalizar("DE 5 a 10 PIEZAS")

        assertTrue(texto.contains("5 a 10"))
        assertFalse(texto.contains("amperios"))
    }
}

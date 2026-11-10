package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalidadFotoTest {

    private fun matriz(valor: Int, lado: Int = 40): Array<DoubleArray> =
        Array(lado) { DoubleArray(lado) { valor.toDouble() } }

    @Test
    fun `una foto casi negra se rechaza por oscura`() {
        assertEquals(CalidadFoto.Problema.MUY_OSCURA, CalidadFoto.evaluar(matriz(10)))
        assertEquals(
            "La foto salió muy oscura. Busca más luz e intenta de nuevo.",
            CalidadFoto.mensaje(CalidadFoto.Problema.MUY_OSCURA),
        )
    }

    @Test
    fun `una foto plana y clara se rechaza por movida`() {
        assertEquals(CalidadFoto.Problema.MOVIDA, CalidadFoto.evaluar(matriz(200)))
        assertEquals(
            "La foto salió movida. Sostén el teléfono firme e intenta de nuevo.",
            CalidadFoto.mensaje(CalidadFoto.Problema.MOVIDA),
        )
    }

    @Test
    fun `una foto con bordes fuertes pasa la revision`() {
        val grises = Array(40) { y ->
            DoubleArray(40) { x -> if ((x / 4 + y / 4) % 2 == 0) 30.0 else 230.0 }
        }
        assertNull(CalidadFoto.evaluar(grises))
    }

    @Test
    fun `una matriz vacia no se rechaza`() {
        assertNull(CalidadFoto.evaluar(emptyArray()))
        assertNull(CalidadFoto.evaluar(arrayOf(DoubleArray(0))))
    }
}

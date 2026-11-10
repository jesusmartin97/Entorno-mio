package com.cecapi.app.feature.modulo10_accesibilidad.funciones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la lógica del chequeo de funciones del teléfono (Fase 3). Código puro: no necesita Android.
 *
 * Escritas según la Fase 3; no se corren con Gradle todavía (JUnit sigue sin autorizar).
 */
class FuncionesTelefonoTest {

    /** Un teléfono con las diez funciones. */
    private fun completo() = EstadoFunciones(
        tieneCamara = true,
        tieneFlash = true,
        tieneMicrofono = true,
        tieneReconocimientoVoz = true,
        tieneMotorVoz = true,
        tieneVibracion = true,
        tieneSensores = true,
        tieneLuzAmbiental = true,
        tieneUbicacion = true,
        versionAdecuada = true,
    )

    @Test
    fun `un telefono completo no avisa nada`() {
        val faltan = ChequeoFunciones.faltantes(completo())
        assertTrue(faltan.isEmpty())
        assertNull(ChequeoFunciones.aviso(faltan))
    }

    @Test
    fun `cada funcion que falta se detecta sola`() {
        // Se apaga una función a la vez y se comprueba que es justo la que falta.
        val apagables = listOf(
            FuncionTelefono.CAMARA to completo().copy(tieneCamara = false),
            FuncionTelefono.FLASH to completo().copy(tieneFlash = false),
            FuncionTelefono.MICROFONO to completo().copy(tieneMicrofono = false),
            FuncionTelefono.RECONOCIMIENTO_VOZ to completo().copy(tieneReconocimientoVoz = false),
            FuncionTelefono.MOTOR_VOZ to completo().copy(tieneMotorVoz = false),
            FuncionTelefono.VIBRACION to completo().copy(tieneVibracion = false),
            FuncionTelefono.SENSORES to completo().copy(tieneSensores = false),
            FuncionTelefono.LUZ_AMBIENTAL to completo().copy(tieneLuzAmbiental = false),
            FuncionTelefono.UBICACION to completo().copy(tieneUbicacion = false),
            FuncionTelefono.VERSION_ANDROID to completo().copy(versionAdecuada = false),
        )
        apagables.forEach { (funcion, estado) ->
            assertEquals("debería faltar solo $funcion", listOf(funcion), ChequeoFunciones.faltantes(estado))
        }
    }

    @Test
    fun `las funciones que faltan van en el orden de revision`() {
        val estado = completo().copy(tieneCamara = false, tieneVibracion = false, tieneUbicacion = false)
        assertEquals(
            listOf(FuncionTelefono.CAMARA, FuncionTelefono.VIBRACION, FuncionTelefono.UBICACION),
            ChequeoFunciones.faltantes(estado),
        )
    }

    @Test
    fun `el aviso habla solo de lo que falta`() {
        val estado = completo().copy(tieneMicrofono = false, tieneMotorVoz = false)
        val aviso = ChequeoFunciones.aviso(ChequeoFunciones.faltantes(estado))
        assertTrue(aviso!!.contains(FuncionTelefono.MICROFONO.aviso))
        assertTrue(aviso.contains(FuncionTelefono.MOTOR_VOZ.aviso))
        // Nada de lo que sí existe debe aparecer.
        listOf(FuncionTelefono.CAMARA, FuncionTelefono.FLASH, FuncionTelefono.VIBRACION).forEach {
            assertFalse("no debería hablar de $it", aviso.contains(it.aviso))
        }
    }

    @Test
    fun `una sola funcion que falta usa el encabezado en singular`() {
        val aviso = ChequeoFunciones.aviso(listOf(FuncionTelefono.CAMARA))
        assertTrue(aviso!!.startsWith("Este teléfono no puede con una cosa"))
    }

    @Test
    fun `la lista vacia de faltantes no produce aviso`() {
        assertNull(ChequeoFunciones.aviso(emptyList()))
    }

    @Test
    fun `claves de funcion ida y vuelta`() {
        FuncionTelefono.entries.forEach { assertEquals(it, FuncionTelefono.desdeClave(it.clave)) }
        assertNull(FuncionTelefono.desdeClave("funcion_inventada"))
        assertEquals(FuncionTelefono.VERSION_ANDROID, FuncionTelefono.desdeClave("  VERSION_ANDROID  "))
    }

    @Test
    fun `el orden cubre exactamente las diez funciones`() {
        assertEquals(FuncionTelefono.entries.size, ChequeoFunciones.ORDEN.size)
        assertEquals(FuncionTelefono.entries.toSet(), ChequeoFunciones.ORDEN.toSet())
    }
}

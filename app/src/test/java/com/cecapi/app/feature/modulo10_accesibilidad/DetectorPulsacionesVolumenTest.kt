package com.cecapi.app.feature.modulo10_accesibilidad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del detector de doble pulsación de volumen. Código puro: no necesita Android. */
class DetectorPulsacionesVolumenTest {

    @Test
    fun `una sola pulsacion no dispara`() {
        val detector = DetectorPulsacionesVolumen()

        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_000))
    }

    @Test
    fun `dos pulsaciones de la misma tecla dentro de 500 ms disparan`() {
        val detector = DetectorPulsacionesVolumen()

        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_000))
        assertTrue(detector.registrar(TeclaVolumen.BAJAR, 1_300))
    }

    @Test
    fun `dos pulsaciones justo en el limite de 500 ms disparan`() {
        val detector = DetectorPulsacionesVolumen()

        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_000))
        assertTrue(detector.registrar(TeclaVolumen.BAJAR, 1_500))
    }

    @Test
    fun `dos pulsaciones fuera de la ventana no disparan`() {
        val detector = DetectorPulsacionesVolumen()

        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_000))
        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_501))
    }

    @Test
    fun `arriba y abajo mezclados no disparan`() {
        val detector = DetectorPulsacionesVolumen()

        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_000))
        assertFalse(detector.registrar(TeclaVolumen.SUBIR, 1_100))
        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_200))
    }

    @Test
    fun `despues de un doble la siguiente pulsacion empieza de cero`() {
        val detector = DetectorPulsacionesVolumen()

        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_000))
        assertTrue(detector.registrar(TeclaVolumen.BAJAR, 1_200))
        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_300))
        assertTrue(detector.registrar(TeclaVolumen.BAJAR, 1_500))
    }

    @Test
    fun `reiniciar olvida la pulsacion pendiente`() {
        val detector = DetectorPulsacionesVolumen()

        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_000))
        detector.reiniciar()
        assertFalse(detector.registrar(TeclaVolumen.BAJAR, 1_200))
    }
}

package com.cecapi.app.feature.modulo10_accesibilidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del motor gesto → acción. Código puro: no necesita Android ni la base. */
class GestorGestosTest {

    private val perfil = PerfilGestos(
        id = 1,
        nombre = "Prueba",
        enlaces = mapOf(
            Gesto.DESLIZAR_ARRIBA to listOf(Accion(TipoAccion.LEER_PANTALLA)),
            Gesto.DESLIZAR_ABAJO to listOf(Accion(TipoAccion.VOLVER_AL_MENU)),
            Gesto.TOQUE_DOBLE to listOf(
                Accion(TipoAccion.HABLAR, "Hola"),
                Accion(TipoAccion.SILENCIO),
            ),
            Gesto.DOS_DEDOS_ABAJO to listOf(Accion(TipoAccion.DETENER)),
            Gesto.DOS_DEDOS_TRIPLE_TOQUE to listOf(Accion(TipoAccion.NADA)),
            Gesto.DOS_DEDOS_ARRIBA to listOf(
                Accion(TipoAccion.HABLAR, "Primero"),
                Accion(TipoAccion.COMANDO_VOZ, "detente"),
            ),
        ),
    )

    @Test
    fun `gesto configurado devuelve su accion`() {
        assertEquals(
            listOf(Accion(TipoAccion.LEER_PANTALLA)),
            GestorGestos.accionesDe(perfil, Gesto.DESLIZAR_ARRIBA),
        )
        assertEquals(
            listOf(Accion(TipoAccion.VOLVER_AL_MENU)),
            GestorGestos.accionesDe(perfil, Gesto.DESLIZAR_ABAJO),
        )
    }

    @Test
    fun `gesto sin configurar no hace nada`() {
        assertTrue(GestorGestos.accionesDe(perfil, Gesto.DESLIZAR_DERECHA).isEmpty())
    }

    @Test
    fun `la accion NADA se descarta`() {
        assertTrue(GestorGestos.accionesDe(perfil, Gesto.DOS_DEDOS_TRIPLE_TOQUE).isEmpty())
    }

    @Test
    fun `las acciones de control van primero`() {
        val acciones = GestorGestos.accionesDe(perfil, Gesto.TOQUE_DOBLE)
        assertEquals(2, acciones.size)
        assertEquals(TipoAccion.SILENCIO, acciones.first().tipo)
        assertEquals(TipoAccion.HABLAR, acciones.last().tipo)
    }

    @Test
    fun `si hay control, esa accion gana y descarta las demas`() {
        assertEquals(
            Accion(TipoAccion.SILENCIO),
            GestorGestos.accionGanadora(GestorGestos.accionesDe(perfil, Gesto.TOQUE_DOBLE)),
        )
        assertEquals(
            Accion(TipoAccion.DETENER),
            GestorGestos.accionGanadora(GestorGestos.accionesDe(perfil, Gesto.DOS_DEDOS_ABAJO)),
        )
    }

    @Test
    fun `un comando de voz de control cuenta como control`() {
        val acciones = GestorGestos.accionesDe(perfil, Gesto.DOS_DEDOS_ARRIBA)
        assertEquals(Accion(TipoAccion.COMANDO_VOZ, "detente"), GestorGestos.accionGanadora(acciones))
    }

    @Test
    fun `sin control no hay accion ganadora`() {
        assertNull(GestorGestos.accionGanadora(listOf(Accion(TipoAccion.HABLAR, "Hola"))))
        assertNull(GestorGestos.accionGanadora(emptyList()))
    }

    @Test
    fun `frases de silencio de la decision 0006`() {
        listOf("silencio", "espera", "espérame", "cállate", "un momento", "un segundo").forEach {
            assertTrue("debería ser silencio: $it", GestorGestos.esSilencio(it))
            assertTrue(GestorGestos.esFraseDeControl(it))
        }
    }

    @Test
    fun `frases de detener de la decision 0006`() {
        listOf("para", "detente", "alto", "basta", "para ya", "ya para", "hasta luego", "adiós", "apágate").forEach {
            assertTrue("debería ser detener: $it", GestorGestos.esDetener(it))
            assertTrue(GestorGestos.esFraseDeControl(it))
        }
    }

    @Test
    fun `solo cuentan como frase completa`() {
        // "para" también es una palabra común: no debe disparar la orden dentro de otra frase.
        assertFalse(GestorGestos.esFraseDeControl("para qué sirve esto"))
        assertFalse(GestorGestos.esFraseDeControl("esperando el camión"))
        assertFalse(GestorGestos.esFraseDeControl("alto rendimiento"))
        assertFalse(GestorGestos.esFraseDeControl(""))
        assertFalse(GestorGestos.esFraseDeControl(null))
    }

    @Test
    fun `codigos de gesto y accion ida y vuelta`() {
        Gesto.entries.forEach { assertEquals(it, Gesto.desdeCodigo(it.codigo)) }
        TipoAccion.entries.forEach { assertEquals(it, TipoAccion.desdeCodigo(it.codigo)) }
        assertNull(Gesto.desdeCodigo("gesto_inventado"))
        assertNull(TipoAccion.desdeCodigo("accion_inventada"))
        assertEquals(Gesto.DESLIZAR_ARRIBA, Gesto.desdeCodigo("  DESLIZAR_ARRIBA  "))
    }

    @Test
    fun `el perfil basico trae los gestos propuestos`() {
        val basico = PerfilesPredeterminados.basico()
        assertNotNull(basico.enlaces[Gesto.DESLIZAR_ARRIBA])
        assertNotNull(basico.enlaces[Gesto.DESLIZAR_ABAJO])
        assertEquals(
            listOf(Accion(TipoAccion.SILENCIO)),
            GestorGestos.accionesDe(basico, Gesto.TOQUE_DOBLE_MANTENER),
        )
        assertEquals(
            listOf(Accion(TipoAccion.DETENER)),
            GestorGestos.accionesDe(basico, Gesto.DOS_DEDOS_ABAJO),
        )
    }
}

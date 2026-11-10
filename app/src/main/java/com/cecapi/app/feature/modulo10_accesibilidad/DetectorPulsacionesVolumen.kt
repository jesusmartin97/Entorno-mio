package com.cecapi.app.feature.modulo10_accesibilidad

/** Las teclas de volumen que el servicio puede observar. */
enum class TeclaVolumen { SUBIR, BAJAR }

/**
 * Detecta dos pulsaciones seguidas de la misma tecla de volumen dentro de una ventana corta.
 *
 * Es código puro a propósito: no depende de Android ni de un reloj. Quien lo usa (el servicio de
 * accesibilidad) le pasa la tecla y el tiempo de cada pulsación. Así se puede probar sin teléfono.
 *
 * Una pulsación de otra tecla reinicia la cuenta, para que subir y bajar mezclados no se confundan
 * con dos pulsaciones de la misma tecla.
 */
class DetectorPulsacionesVolumen(
    private val ventanaMs: Long = VENTANA_MS,
) {

    private var anterior: Pulsacion? = null

    /**
     * Registra una pulsación y devuelve true solo cuando completa dos de la misma tecla dentro de la
     * ventana. Después de un doble, la siguiente pulsación empieza una cuenta nueva.
     */
    fun registrar(tecla: TeclaVolumen, ahoraMs: Long): Boolean {
        val previa = anterior
        val esDoble = previa != null &&
            previa.tecla == tecla &&
            ahoraMs >= previa.ahoraMs &&
            ahoraMs - previa.ahoraMs <= ventanaMs

        anterior = if (esDoble) null else Pulsacion(tecla, ahoraMs)
        return esDoble
    }

    /** Olvida la pulsación pendiente, por ejemplo si el servicio se desconecta. */
    fun reiniciar() {
        anterior = null
    }

    private data class Pulsacion(val tecla: TeclaVolumen, val ahoraMs: Long)

    companion object {
        /** Tiempo máximo entre las dos pulsaciones de un doble gesto. */
        const val VENTANA_MS = 500L
    }
}

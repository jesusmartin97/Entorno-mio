package com.cecapi.app.feature.modulo10_accesibilidad

/** Qué lector de pantalla está activo, si alguno. */
enum class EstadoLectorPantalla { NINGUNO, TALKBACK, OTRO }

/**
 * Decide si TalkBack está activo a partir de los paquetes de los servicios de accesibilidad que
 * hablan. Es código puro: el ViewModel le pasa lo que devuelve `AccessibilityManager` con
 * `FEEDBACK_SPOKEN`, así se puede probar sin Android.
 */
object DetectorTalkBack {

    const val PAQUETE_TALKBACK = "com.google.android.marvin.talkback"

    /**
     * Samsung trae su propia versión de TalkBack en varios Galaxy. Es el mismo lector para la
     * persona, así que también cuenta como TalkBack aunque el paquete no sea el de Google.
     */
    const val PAQUETE_TALKBACK_SAMSUNG = "com.samsung.android.accessibility.talkback"

    private val PAQUETES_TALKBACK = setOf(PAQUETE_TALKBACK, PAQUETE_TALKBACK_SAMSUNG)

    fun estado(paquetesHablados: Collection<String>): EstadoLectorPantalla {
        val paquetes = paquetesHablados.filter { it.isNotBlank() }
        return when {
            paquetes.any { it in PAQUETES_TALKBACK } -> EstadoLectorPantalla.TALKBACK
            paquetes.isNotEmpty() -> EstadoLectorPantalla.OTRO
            else -> EstadoLectorPantalla.NINGUNO
        }
    }

    /** True solo si TalkBack es uno de los lectores activos. */
    fun estaActivo(paquetesHablados: Collection<String>): Boolean =
        estado(paquetesHablados) == EstadoLectorPantalla.TALKBACK
}

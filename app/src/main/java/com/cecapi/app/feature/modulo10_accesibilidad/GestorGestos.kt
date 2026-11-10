package com.cecapi.app.feature.modulo10_accesibilidad

import com.cecapi.app.core.voice.VoiceText

/**
 * Gestos que el servicio de accesibilidad puede distinguir y que un perfil puede reasignar.
 * El [codigo] es lo que se guarda en la columna `gestos.codigo`, para no atar la base al entero de Android.
 */
enum class Gesto(val codigo: String, val nombreHablado: String) {
    DESLIZAR_ARRIBA("deslizar_arriba", "Deslizar hacia arriba"),
    DESLIZAR_ABAJO("deslizar_abajo", "Deslizar hacia abajo"),
    DESLIZAR_IZQUIERDA("deslizar_izquierda", "Deslizar hacia la izquierda"),
    DESLIZAR_DERECHA("deslizar_derecha", "Deslizar hacia la derecha"),
    DESLIZAR_ARRIBA_ABAJO("deslizar_arriba_abajo", "Deslizar hacia arriba y hacia abajo"),
    DESLIZAR_IZQUIERDA_DERECHA("deslizar_izquierda_derecha", "Deslizar hacia los lados"),
    TOQUE_DOBLE("doble_toque", "Tocar dos veces"),
    TOQUE_DOBLE_MANTENER("doble_toque_sostenido", "Tocar dos veces y mantener"),
    DOS_DEDOS_TOQUE("dos_dedos_toque", "Tocar con dos dedos"),
    DOS_DEDOS_DOBLE_TOQUE("dos_dedos_doble_toque", "Tocar dos veces con dos dedos"),
    DOS_DEDOS_TRIPLE_TOQUE("dos_dedos_triple_toque", "Tocar tres veces con dos dedos"),
    DOS_DEDOS_ARRIBA("dos_dedos_arriba", "Dos dedos hacia arriba"),
    DOS_DEDOS_ABAJO("dos_dedos_abajo", "Dos dedos hacia abajo"),
    DOS_DEDOS_IZQUIERDA("dos_dedos_izquierda", "Dos dedos hacia la izquierda"),
    DOS_DEDOS_DERECHA("dos_dedos_derecha", "Dos dedos hacia la derecha"),
    ;

    companion object {
        /** El gesto con ese código, o null si la fila de la base ya no corresponde a un gesto conocido. */
        fun desdeCodigo(codigo: String): Gesto? = entries.firstOrNull { it.codigo == codigo.trim().lowercase() }
    }
}

/** Qué puede hacer la app cuando se hace un gesto. */
enum class TipoAccion(val codigo: String) {
    /** Leer en voz alta lo que hay en la pantalla (se completa en la siguiente entrega). */
    LEER_PANTALLA("leer_pantalla"),

    /** Abrir CECAPI en el menú principal. */
    VOLVER_AL_MENU("volver_al_menu"),

    /** Decir un texto fijo, guardado en [Accion.parametro]. */
    HABLAR("hablar"),

    /** Ejecutar un comando de voz ya conocido por la app, guardado en [Accion.parametro]. */
    COMANDO_VOZ("comando_voz"),

    /** Callar al asistente, como decir "silencio". */
    SILENCIO("silencio"),

    /** Detener todo, como decir "para". */
    DETENER("detener"),

    /** Aviso por vibración. */
    VIBRAR("vibrar"),

    /** Sin efecto: el gesto queda libre a propósito. */
    NADA("nada"),
    ;

    companion object {
        fun desdeCodigo(codigo: String): TipoAccion? = entries.firstOrNull { it.codigo == codigo.trim().lowercase() }
    }
}

/** Una acción resuelta, lista para que el servicio la ejecute. */
data class Accion(val tipo: TipoAccion, val parametro: String? = null)

/** Un perfil de accesibilidad ya traducido a algo que el motor puede usar sin tocar la base. */
data class PerfilGestos(
    val id: Long = 0,
    val nombre: String,
    val enlaces: Map<Gesto, List<Accion>>,
)

/**
 * Traduce un gesto a las acciones del perfil activo. Es código puro a propósito: no depende de Android
 * ni de Room, así se prueba con pruebas unitarias normales.
 */
object GestorGestos {

    /**
     * Acciones que corresponden a [gesto] en [perfil]. Vacío si el gesto no está configurado.
     *
     * Las acciones de control (decisión 0006: "silencio" y "para") se devuelven primero, siempre, para
     * que quien las ejecute las atienda antes que cualquier otra cosa.
     */
    fun accionesDe(perfil: PerfilGestos, gesto: Gesto): List<Accion> {
        val acciones = perfil.enlaces[gesto].orEmpty().filter { it.tipo != TipoAccion.NADA }
        return acciones.filter(::esControl) + acciones.filterNot(::esControl)
    }

    /**
     * Si el gesto pide callar o detener, esa acción gana y las demás del mismo gesto no se ejecutan: no
     * tiene sentido empezar a hablar justo después de callar al asistente.
     */
    fun accionGanadora(acciones: List<Accion>): Accion? = acciones.firstOrNull(::esControl)

    /** True si la acción es callar, detener o un comando de voz que es una de esas dos frases. */
    fun esControl(accion: Accion): Boolean = when (accion.tipo) {
        TipoAccion.SILENCIO, TipoAccion.DETENER -> true
        TipoAccion.COMANDO_VOZ -> esFraseDeControl(accion.parametro)
        else -> false
    }

    /**
     * Las frases completas de "silencio" o "para" (decisión 0006), comparadas como lo hace el motor de
     * voz. Copiadas de `VoiceEngine.SILENCE_PHRASES` / `STOP_PHRASES` porque ahí son privadas; hay que
     * mantenerlas iguales (pendiente: que Equipo 1 las exponga en un lugar compartido).
     */
    fun esFraseDeControl(frase: String?): Boolean {
        if (frase.isNullOrBlank()) return false
        return VoiceText.normalize(frase) in FRASES_SILENCIO || VoiceText.normalize(frase) in FRASES_DETENER
    }

    /** True si la frase es la orden de callar (decisión 0006). */
    fun esSilencio(frase: String?): Boolean =
        !frase.isNullOrBlank() && VoiceText.normalize(frase) in FRASES_SILENCIO

    /** True si la frase es la orden de detener (decisión 0006). */
    fun esDetener(frase: String?): Boolean =
        !frase.isNullOrBlank() && VoiceText.normalize(frase) in FRASES_DETENER

    // De decisión 0006. Deben ser frases completas: "para" también es una palabra común.
    val FRASES_SILENCIO = setOf("silencio", "espera", "esperame", "callate", "un momento", "un segundo")
    val FRASES_DETENER = setOf(
        "para", "detente", "alto", "basta", "para ya", "ya para", "hasta luego", "adios", "apagate",
    )
}

/**
 * Perfil de partida mientras CECAPI no confirme los suyos. Es una propuesta: dice qué gestos existen y
 * qué se puede esperar de ellos, sin inventar todavía una lista de lugares ni de objetos.
 */
object PerfilesPredeterminados {

    fun basico(): PerfilGestos = PerfilGestos(
        id = 0,
        nombre = "Básico",
        enlaces = mapOf(
            Gesto.DESLIZAR_ARRIBA to listOf(Accion(TipoAccion.LEER_PANTALLA)),
            Gesto.DESLIZAR_ABAJO to listOf(Accion(TipoAccion.VOLVER_AL_MENU)),
            Gesto.TOQUE_DOBLE_MANTENER to listOf(Accion(TipoAccion.SILENCIO)),
            Gesto.DOS_DEDOS_ABAJO to listOf(Accion(TipoAccion.DETENER)),
        ),
    )
}

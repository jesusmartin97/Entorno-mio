package com.cecapi.app.feature.modulo10_accesibilidad.funciones

/**
 * Las diez funciones del teléfono que CECAPI necesita, con lo que se dice cuando alguna falta
 * (Fase 3, lista ya aprobada).
 *
 * A propósito es código puro: no depende de Android, así se prueba con pruebas unitarias normales.
 * El orden en que se revisan y se dicen es [ChequeoFunciones.ORDEN].
 */
enum class FuncionTelefono(
    /** Identificador estable para guardar o comparar sin atarse al nombre de la constante. */
    val clave: String,
    /** Lo que se dice en voz alta cuando esta función falta. */
    val aviso: String,
) {
    CAMARA(
        "camara",
        "No hay cámara, así que no podré describir lo que tienes enfrente.",
    ),
    FLASH(
        "flash",
        "No hay flash, así que en un lugar oscuro no podré iluminar la foto.",
    ),
    MICROFONO(
        "microfono",
        "No hay micrófono, así que no podré escucharte.",
    ),
    RECONOCIMIENTO_VOZ(
        "reconocimiento_voz",
        "El teléfono no tiene reconocimiento de voz, así que no entenderé lo que dices.",
    ),
    MOTOR_VOZ(
        "motor_voz",
        "No hay motor de voz, así que no podré hablarte.",
    ),
    VIBRACION(
        "vibracion",
        "No hay vibración, así que no podré avisarte con el tacto.",
    ),
    SENSORES(
        "sensores",
        "No encuentro los sensores de movimiento, así que no sabré cómo mueves el teléfono.",
    ),
    LUZ_AMBIENTAL(
        "luz_ambiental",
        "No hay sensor de luz, así que no podré ajustar el brillo según el lugar.",
    ),
    UBICACION(
        "ubicacion",
        "El teléfono no tiene ubicación, así que no podré saber dónde estás.",
    ),
    VERSION_ANDROID(
        "version_android",
        "La versión de Android es más vieja de la que la app necesita, así que algunas funciones podrían fallar.",
    ),
    ;

    companion object {
        /** La función con esa clave, o null si no corresponde a ninguna (por ejemplo, texto guardado viejo). */
        fun desdeClave(clave: String): FuncionTelefono? =
            entries.firstOrNull { it.clave == clave.trim().lowercase() }
    }
}

/** Qué funciones tiene el teléfono, ya medido. Cada campo dice si esa función está disponible. */
data class EstadoFunciones(
    val tieneCamara: Boolean,
    val tieneFlash: Boolean,
    val tieneMicrofono: Boolean,
    val tieneReconocimientoVoz: Boolean,
    val tieneMotorVoz: Boolean,
    val tieneVibracion: Boolean,
    val tieneSensores: Boolean,
    val tieneLuzAmbiental: Boolean,
    val tieneUbicacion: Boolean,
    val versionAdecuada: Boolean,
)

/**
 * Decide qué funciones faltan y qué decir. No toca Android: recibe un [EstadoFunciones] ya medido
 * (lo mide [RevisorFuncionesTelefono]), así la decisión se prueba sola.
 */
object ChequeoFunciones {

    /** Orden fijo de revisión: primero lo que más se usa para describir el entorno y para hablar. */
    val ORDEN: List<FuncionTelefono> = listOf(
        FuncionTelefono.CAMARA,
        FuncionTelefono.FLASH,
        FuncionTelefono.MICROFONO,
        FuncionTelefono.RECONOCIMIENTO_VOZ,
        FuncionTelefono.MOTOR_VOZ,
        FuncionTelefono.VIBRACION,
        FuncionTelefono.SENSORES,
        FuncionTelefono.LUZ_AMBIENTAL,
        FuncionTelefono.UBICACION,
        FuncionTelefono.VERSION_ANDROID,
    )

    /** Si esa función está disponible en el teléfono medido. */
    fun presente(estado: EstadoFunciones, funcion: FuncionTelefono): Boolean = when (funcion) {
        FuncionTelefono.CAMARA -> estado.tieneCamara
        FuncionTelefono.FLASH -> estado.tieneFlash
        FuncionTelefono.MICROFONO -> estado.tieneMicrofono
        FuncionTelefono.RECONOCIMIENTO_VOZ -> estado.tieneReconocimientoVoz
        FuncionTelefono.MOTOR_VOZ -> estado.tieneMotorVoz
        FuncionTelefono.VIBRACION -> estado.tieneVibracion
        FuncionTelefono.SENSORES -> estado.tieneSensores
        FuncionTelefono.LUZ_AMBIENTAL -> estado.tieneLuzAmbiental
        FuncionTelefono.UBICACION -> estado.tieneUbicacion
        FuncionTelefono.VERSION_ANDROID -> estado.versionAdecuada
    }

    /** Las funciones que faltan, en el orden de [ORDEN]. Vacío si el teléfono tiene todo. */
    fun faltantes(estado: EstadoFunciones): List<FuncionTelefono> =
        ORDEN.filterNot { presente(estado, it) }

    /**
     * Lo que se dice en voz alta: una frase por cada función que falta. Devuelve null cuando no falta
     * nada, para no avisar de lo que sí existe (regla de la Fase 3).
     */
    fun aviso(faltantes: List<FuncionTelefono>): String? {
        if (faltantes.isEmpty()) return null
        val encabezado = if (faltantes.size == 1) {
            "Este teléfono no puede con una cosa que necesito."
        } else {
            "Este teléfono no puede con ${faltantes.size} cosas que necesito."
        }
        return encabezado + " " + faltantes.joinToString(" ") { it.aviso }
    }
}

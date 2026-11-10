package com.cecapi.app.feature.modulo7_entorno

/** Caja de una detección, normalizada a la imagen: 0 es el borde izquierdo/superior y 1 el derecho/inferior. */
data class CajaCamara(
    val izquierda: Float,
    val arriba: Float,
    val derecha: Float,
    val abajo: Float,
) {
    val ancho: Float get() = (derecha - izquierda).coerceAtLeast(0f)
    val alto: Float get() = (abajo - arriba).coerceAtLeast(0f)
    val area: Float get() = ancho * alto
    val centroX: Float get() = (izquierda + derecha) / 2f
}

/**
 * Lógica de la cámara de personas, sin Android ni ML Kit. Decide la posición, el encuadre, el color
 * de la ropa y arma las frases. Todo lo que no se puede afirmar con claridad se deja fuera.
 */
data class PersonaCamara(
    val caja: CajaCamara,
    val confianza: Float,
)

/** Colores ya nombrados de una franja de ropa y su brillo medio (0 a 1). */
data class MuestraRopa(
    val colores: List<String>,
    val brilloMedio: Float = 1f,
)

/** Color de la ropa arriba y abajo. null significa "de color no claro". */
data class RopaCamara(
    val arriba: String?,
    val abajo: String?,
)

enum class Encuadre {
    LEJOS,
    CORTADA,
}

object CamaraPersonas {

    const val MAX_OBJETOS = 3
    const val CONFIANZA_MINIMA_PERSONA = 0.5f
    const val ALTO_MINIMO_PERSONA = 0.20f
    const val MARGEN_BORDE = 0.10f
    const val FRACCION_COLOR_MINIMA = 0.60f
    const val BRILLO_MINIMO_ROPA = 0.08f
    const val FRASE_SIN_RECONOCIMIENTO =
        "No reconozco bien lo que hay. Intenta con más luz o acércate."

    fun posicion(caja: CajaCamara): String = when {
        caja.centroX < 0.33f -> "a tu izquierda"
        caja.centroX > 0.66f -> "a tu derecha"
        else -> "al centro"
    }

    /** Personas con confianza suficiente, de la más grande (más cercana) a la más pequeña. */
    fun personas(detecciones: List<PersonaCamara>): List<PersonaCamara> =
        detecciones
            .filter { it.confianza >= CONFIANZA_MINIMA_PERSONA && it.caja.area > 0f }
            .sortedByDescending { it.caja.area }

    /**
     * Revisa si la persona cabe en la foto. Primero la distancia (caja muy baja) y después el
     * borde: si la caja está a menos del 10 % de cualquier orilla, se considera cortada.
     */
    fun encuadre(caja: CajaCamara): Encuadre? = when {
        caja.alto < ALTO_MINIMO_PERSONA -> Encuadre.LEJOS
        caja.izquierda < MARGEN_BORDE || caja.arriba < MARGEN_BORDE ||
            caja.derecha > 1f - MARGEN_BORDE || caja.abajo > 1f - MARGEN_BORDE -> Encuadre.CORTADA
        else -> null
    }

    /**
     * Color dominante de una franja. Si hay muchos colores mezclados, o la zona está muy oscura y
     * no es claramente negra, se devuelve null para decir "ropa de color no claro" en vez de
     * adivinar.
     */
    fun colorRopa(muestra: MuestraRopa): String? {
        if (muestra.colores.isEmpty()) return null
        val cuenta = HashMap<String, Int>()
        for (color in muestra.colores) {
            cuenta[color] = (cuenta[color] ?: 0) + 1
        }
        val (nombre, veces) = cuenta.maxByOrNull { it.value } ?: return null
        val fraccion = veces.toFloat() / muestra.colores.size
        if (fraccion < FRACCION_COLOR_MINIMA) return null
        if (muestra.brilloMedio < BRILLO_MINIMO_ROPA && (nombre != "negro" || fraccion < 0.85f)) {
            return null
        }
        return nombre
    }

    /** Hasta tres descripciones ya armadas ("una silla a tu izquierda"), en el orden recibido. */
    fun fraseObjetos(descripciones: List<String>): String? {
        val limpias = descripciones.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_OBJETOS)
        if (limpias.isEmpty()) return null
        return "Veo ${unir(limpias)}."
    }

    /**
     * Frase de una o varias personas. Con [cantidad] cero no hay frase: la foto no habla de ropa ni
     * de personas. Si el encuadre no sirve, solo se avisa y no se describe la ropa.
     */
    fun frasePersonas(
        cantidad: Int,
        posicion: String,
        encuadre: Encuadre?,
        ropa: RopaCamara,
        nombre: String? = null,
    ): String? {
        if (cantidad <= 0) return null
        if (encuadre != null) {
            val aviso = when (encuadre) {
                Encuadre.LEJOS -> "lejos, acércate"
                Encuadre.CORTADA -> "cortada, aléjate un poco"
            }
            return if (cantidad == 1) {
                "La persona está $aviso."
            } else {
                "Hay $cantidad personas. La más cercana está $aviso."
            }
        }
        val base = if (cantidad == 1) {
            "Hay una persona $posicion"
        } else {
            "Hay $cantidad personas. La más cercana está $posicion"
        }
        val gafete = if (nombre.isNullOrBlank()) "" else " que tiene un gafete que dice ${nombre.trim()}"
        return "$base con ${textoRopa(ropa)}$gafete."
    }

    private fun textoRopa(ropa: RopaCamara): String {
        val arriba = ropa.arriba?.let(::femenino)
        val abajo = ropa.abajo?.let(::femenino)
        return when {
            arriba != null && abajo != null -> "ropa $arriba arriba y $abajo abajo"
            arriba != null -> "ropa $arriba arriba y de color no claro abajo"
            abajo != null -> "ropa de color no claro arriba y $abajo abajo"
            else -> "ropa de color no claro"
        }
    }

    /** "Ropa" es femenino: blanco → blanca, negro → negra; azul, gris y verde no cambian. */
    private fun femenino(color: String): String = when (color) {
        "blanco" -> "blanca"
        "negro" -> "negra"
        "rojo" -> "roja"
        "amarillo" -> "amarilla"
        "morado" -> "morada"
        else -> color
    }

    private fun unir(frases: List<String>): String = when (frases.size) {
        0 -> ""
        1 -> frases[0]
        2 -> "${frases[0]} y ${frases[1]}"
        else -> frases.dropLast(1).joinToString(", ") + " y " + frases.last()
    }
}

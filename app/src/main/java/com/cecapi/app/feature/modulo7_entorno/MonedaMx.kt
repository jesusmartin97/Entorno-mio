package com.cecapi.app.feature.modulo7_entorno

/** Denominaciones de las monedas mexicanas que reconoce la app y cómo se dicen en voz alta. */
enum class DenominacionMoneda(val valor: Int, val nombreHablado: String) {
    M1(1, "un peso"),
    M2(2, "dos pesos"),
    M5(5, "cinco pesos"),
    M10(10, "diez pesos"),
    M20(20, "veinte pesos"),
}

/** Por qué el resultado es el que es: de eso depende lo que se dice en voz alta. */
enum class MotivoMoneda {
    /** Se reconoció con confianza alta y sin duda entre dos valores. */
    SEGURA,

    /** Hay una candidata, pero la confianza no alcanza para decirla como un hecho. */
    DUDOSA,

    /** El modelo no se decide por ninguna clase. */
    NO_CLARO,

    /** El modelo dice que en la foto no hay una moneda (clase opcional "sin_moneda"). */
    NO_HAY_MONEDA,

    /** El modelo dice que hay varias monedas juntas (clase opcional "varias"). */
    VARIAS,

    /** Falta el archivo del modelo (assets/monedas.tflite): todavía no se ha entrenado o instalado. */
    SIN_MODELO,

    /** No se pudo consultar a Gemini (sin internet, sin clave o error del servicio). */
    SIN_CONEXION,

    /** La persona tiene apagada la inteligencia artificial en internet en Configuración. */
    IA_DESHABILITADA,
}

/**
 * @property denominacion la candidata; solo se puede decir como un hecho si [esSegura].
 * @property confianza la probabilidad que dio el modelo a [denominacion] (0 a 1).
 */
data class ResultadoMoneda(
    val denominacion: DenominacionMoneda?,
    val confianza: Float,
    val motivo: MotivoMoneda,
) {
    val esSegura: Boolean get() = motivo == MotivoMoneda.SEGURA && denominacion != null
}

/**
 * Decide qué moneda es a partir de lo que devuelve el clasificador de imágenes (un modelo TFLite entrenado con
 * fotos de monedas). No lee números ni usa OCR: solo mira las probabilidades del modelo.
 *
 * Las clases del modelo son los nombres de las carpetas de entrenamiento: `1peso`, `2pesos`, `5pesos`,
 * `10pesos`, `20pesos` y, opcionales, `sin_moneda` y `varias`. De cada nombre se toma el número.
 */
object MonedaMx {
    const val ETIQUETA_SIN_MONEDA = "sin_moneda"
    const val ETIQUETA_VARIAS = "varias"

    // ---- Umbrales ----------------------------------------------------------------------------------------
    // Calibrados con las 824 fotos únicas del entrenamiento (incluidas manos, sombra, luz interior y poca
    // luz). Con UMBRAL_SEGURA=0.75 el modelo acertó el 99.5% de las veces que dijo "segura"; con 0.65
    // acertaba el 97.7%. Como Gemini es la primera opción y esto solo se usa sin internet, conviene ser
    // conservador: mejor decir "parece" que afirmar una moneda equivocada.
    //
    // La app clasifica 3 variantes de la misma foto (completa, espejo y recorte central) y promedia. Se dice
    // "segura" solo si el promedio es alto Y las variantes están de acuerdo; si el promedio es alto pero las
    // variantes se contradicen, el modelo está adivinando.

    /** Moneda pedida ("qué moneda es"): probabilidad promedio mínima para decirla como un hecho. */
    const val UMBRAL_SEGURA = 0.75f

    /** Moneda pedida: cuántas de las 3 variantes deben coincidir en la misma moneda. */
    const val ACUERDO_MINIMO = 2

    /**
     * Foto normal ("qué hay enfrente"): nadie pidió una moneda y el modelo solo conoce monedas (siempre elige
     * alguna, aunque la foto sea de una laptop). Por eso aquí se exige mucho más antes de interrumpir la
     * descripción del entorno con "Es una moneda de ...".
     */
    const val UMBRAL_SEGURA_AUTOMATICA = 0.95f

    /** Foto normal: además del promedio, CADA variante debe dar al menos esto a esa misma moneda. */
    const val UMBRAL_VARIANTE_AUTOMATICA = 0.70f

    /** Por debajo de esto ni siquiera se dice "parece". */
    const val UMBRAL_DUDOSA = 0.60f

    /** Probabilidad mínima para creerle al modelo que no hay moneda o que hay varias. */
    const val UMBRAL_CLASE_ESPECIAL = 0.60f

    // Solo nombres como "5pesos", "1peso", "20 pesos" o "$10". Un nombre como "clase_2" (el que se usa si no se
    // pudo leer monedas_labels.txt) NO debe tomarse por "2 pesos": diría una moneda equivocada con seguridad.
    private val FORMATO_ETIQUETA = Regex("""^[$]?(\d+)[ _]?(pesos?)?$""")

    /** La moneda que corresponde al nombre de una clase del modelo ("5pesos" -> 5 pesos), o null. */
    fun desdeEtiqueta(etiqueta: String): DenominacionMoneda? {
        val numero = FORMATO_ETIQUETA.matchEntire(etiqueta.trim().lowercase())
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return DenominacionMoneda.values().firstOrNull { it.valor == numero }
    }

    /**
     * @param lecturas pares (nombre de la clase, probabilidad) del promedio de las variantes, en cualquier orden.
     * @param porVariante lo mismo, pero de cada variante por separado. Vacío = no se sabe, y entonces solo
     *   cuenta el promedio.
     * @param automatico true si es una foto normal en la que nadie pidió una moneda (umbrales más estrictos).
     */
    fun decidir(
        lecturas: List<Pair<String, Float>>,
        porVariante: List<List<Pair<String, Float>>> = emptyList(),
        automatico: Boolean = false,
    ): ResultadoMoneda {
        val ordenadas = lecturas.sortedByDescending { it.second }
        val primera = ordenadas.firstOrNull() ?: return ResultadoMoneda(null, 0f, MotivoMoneda.NO_CLARO)
        val etiqueta = primera.first.trim().lowercase()
        val confianza = primera.second

        if (etiqueta == ETIQUETA_SIN_MONEDA) {
            val motivo = if (confianza >= UMBRAL_CLASE_ESPECIAL) MotivoMoneda.NO_HAY_MONEDA else MotivoMoneda.NO_CLARO
            return ResultadoMoneda(null, confianza, motivo)
        }
        if (etiqueta == ETIQUETA_VARIAS) {
            val motivo = if (confianza >= UMBRAL_CLASE_ESPECIAL) MotivoMoneda.VARIAS else MotivoMoneda.NO_CLARO
            return ResultadoMoneda(null, confianza, motivo)
        }

        val d = desdeEtiqueta(etiqueta) ?: return ResultadoMoneda(null, confianza, MotivoMoneda.NO_CLARO)

        // Qué dice cada variante sobre esta misma moneda, y por cuál se decidió cada una.
        val deEstaMoneda = porVariante.map { v -> v.firstOrNull { it.first.trim().lowercase() == etiqueta }?.second ?: 0f }
        val votos = porVariante.count { v -> v.maxByOrNull { it.second }?.first?.trim()?.lowercase() == etiqueta }
        val concuerdan = when {
            porVariante.isEmpty() -> true
            automatico -> votos == porVariante.size && deEstaMoneda.all { it >= UMBRAL_VARIANTE_AUTOMATICA }
            else -> votos >= minOf(ACUERDO_MINIMO, porVariante.size)
        }
        val umbral = if (automatico) UMBRAL_SEGURA_AUTOMATICA else UMBRAL_SEGURA

        return when {
            confianza >= umbral && concuerdan -> ResultadoMoneda(d, confianza, MotivoMoneda.SEGURA)
            confianza >= UMBRAL_DUDOSA -> ResultadoMoneda(d, confianza, MotivoMoneda.DUDOSA)
            else -> ResultadoMoneda(null, confianza, MotivoMoneda.NO_CLARO)
        }
    }

    /** Qué dice en voz alta la app. Una duda nunca se dice como un hecho. */
    fun frase(r: ResultadoMoneda): String = when {
        r.esSegura && r.denominacion != null -> "Es una moneda de ${r.denominacion.nombreHablado}."
        r.motivo == MotivoMoneda.DUDOSA && r.denominacion != null ->
            "Parece una moneda de ${r.denominacion.nombreHablado}, pero no estoy segura. " +
                "Ponla sola sobre una superficie lisa, con buena luz, y pregunta otra vez."
        r.motivo == MotivoMoneda.VARIAS ->
            "Veo varias monedas juntas. Muéstramelas de una en una para reconocerlas."
        r.motivo == MotivoMoneda.NO_HAY_MONEDA ->
            "No veo una moneda. Ponla sola frente a la cámara, con buena luz, y pregunta otra vez."
        r.motivo == MotivoMoneda.SIN_MODELO ->
            "Todavía no tengo instalado el modelo de monedas. Avisa al equipo para que lo agregue."
        r.motivo == MotivoMoneda.SIN_CONEXION ->
            "No se pudo reconocer la moneda. Intenta de nuevo."
        r.motivo == MotivoMoneda.IA_DESHABILITADA ->
            "Para reconocer monedas con Gemini, activa la inteligencia artificial en internet en Configuración."
        else ->
            "No logré reconocer la moneda. Ponla sola sobre una superficie lisa, con buena luz, y pregunta otra vez."
    }
}

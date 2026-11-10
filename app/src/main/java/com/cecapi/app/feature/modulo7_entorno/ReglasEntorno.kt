package com.cecapi.app.feature.modulo7_entorno

/**
 * Medidas de la caja de una detección, en fracción de la foto: 0 es nada y 1 es todo el ancho o alto.
 * No depende de Android para poder probarse con JUnit.
 */
data class CajaSenal(
    val ancho: Float,
    val alto: Float,
) {
    /** Relación ancho/alto. Una caja apaisada se parece a la pantalla de una laptop, no a un teléfono. */
    val aspecto: Float get() = if (alto <= 0f) Float.MAX_VALUE else ancho / alto

    /** Fracción del área de la foto que ocupa la caja. */
    val area: Float get() = (ancho * alto).coerceIn(0f, 1f)
}

/** Qué hacer con una detección que el detector llamó "cell phone". */
enum class JuicioCelular {
    /** Es un teléfono: caja pequeña y confianza suficiente. */
    CELULAR,

    /** La caja es ancha o apaisada: se dice "pantalla", nunca "celular". */
    PANTALLA,

    /** No hay base suficiente: mejor no decir nada. */
    NADA,
}

/**
 * Reglas puras (sin Android, red ni modelos) para no confundir objetos del entorno. El detector
 * EfficientDet lite0 etiqueta como "cell phone" la pantalla de una laptop y como "chair" un monitor
 * o una silla lejana; aquí se decide qué se puede afirmar con la confianza y el tamaño de la caja.
 *
 * Los umbrales viven en constantes para ajustarlos con fotos reales, sin tocar la lógica.
 */
object ReglasEntorno {

    /**
     * Ancho máximo (fracción de la foto) para llamar "celular" a una detección `cell phone`.
     * Una caja más ancha es la pantalla de una laptop vista de cerca.
     */
    const val ANCHO_MAXIMO_CELULAR = 0.55f

    /** Aspecto ancho/alto máximo de un celular. Arriba de esto la caja es apaisada, como una pantalla. */
    const val ASPECTO_MAXIMO_CELULAR = 1.20f

    /** Confianza mínima para decir "celular". */
    const val UMBRAL_CELULAR = 0.60f

    /** Confianza mínima para arriesgar "pantalla" cuando una caja ancha no es un celular. */
    const val UMBRAL_PANTALLA_DUDA = 0.50f

    /** Área mínima de la foto que debe ocupar una silla para mencionarla. */
    const val AREA_MINIMA_SILLA = 0.30f

    /** Confianza mínima para decir "silla". */
    const val UMBRAL_SILLA = 0.60f

    /** Confianza mínima del detector para decir el color de un objeto. */
    const val UMBRAL_COLOR = 0.75f

    /** Fracción mínima de píxeles del mismo color para considerarlo uniforme. */
    const val FRACCION_COLOR_UNIFORME = 0.45f

    /** Cuando no hay ninguna señal suficiente. */
    const val FRASE_SIN_RECONOCIMIENTO = "No reconozco bien lo que hay."

    /**
     * Decide qué hacer con un `cell phone`. Sin caja no se afirma nada. Una caja ancha (>25 % del
     * ancho) o apaisada (aspecto >1.2) no es un teléfono; si la confianza alcanza, se dice "pantalla".
     */
    fun juicioCelular(
        confianza: Float,
        caja: CajaSenal?,
        anchoMaximo: Float = ANCHO_MAXIMO_CELULAR,
        aspectoMaximo: Float = ASPECTO_MAXIMO_CELULAR,
        umbralCelular: Float = UMBRAL_CELULAR,
        umbralPantalla: Float = UMBRAL_PANTALLA_DUDA,
    ): JuicioCelular {
        if (caja == null) return JuicioCelular.NADA
        val ancha = caja.ancho > anchoMaximo
        val apaisada = caja.aspecto > aspectoMaximo
        if (ancha || apaisada) {
            return if (confianza >= umbralPantalla) JuicioCelular.PANTALLA else JuicioCelular.NADA
        }
        return if (confianza >= umbralCelular) JuicioCelular.CELULAR else JuicioCelular.NADA
    }

    /**
     * Una silla solo se menciona si ocupa buena parte de la foto y el detector está seguro. Si en la
     * misma foto hay televisor, laptop o teclado, la caja suele ser el mueble del fondo o el monitor:
     * no se dice "silla".
     */
    fun sillaValida(
        confianza: Float,
        caja: CajaSenal?,
        hayPantalla: Boolean,
        areaMinima: Float = AREA_MINIMA_SILLA,
        umbral: Float = UMBRAL_SILLA,
    ): Boolean = !hayPantalla && caja != null && caja.area > areaMinima && confianza >= umbral

    /** ¿Se puede decir el color? Solo con confianza alta y un color que domine la zona. */
    fun colorConfiable(
        confianza: Float,
        fraccion: Float,
        umbral: Float = UMBRAL_COLOR,
        fraccionMinima: Float = FRACCION_COLOR_UNIFORME,
    ): Boolean = confianza >= umbral && fraccion >= fraccionMinima

    /**
     * Filtro para los objetos que van por la descripción genérica. Hoy solo la silla necesita una
     * regla propia; el resto se conserva tal como lo describió el detector.
     */
    fun conservarObjeto(
        etiqueta: String,
        confianza: Float,
        caja: CajaSenal?,
        hayPantallaEnFoto: Boolean,
    ): Boolean = when (etiqueta.trim().lowercase()) {
        "chair" -> sillaValida(confianza, caja, hayPantallaEnFoto)
        else -> true
    }
}

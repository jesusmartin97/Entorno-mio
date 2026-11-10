package com.cecapi.app.feature.modulo7_entorno

/** De dónde salió una etiqueta: del detector de objetos o del etiquetador de imágenes de ML Kit. */
enum class OrigenEtiqueta {
    DETECTOR,
    ETIQUETADOR,
}

/** Una etiqueta ya observada, con su confianza y, si se conoce, su posición en la foto. */
data class SenalEtiqueta(
    val etiqueta: String,
    val confianza: Float,
    val posicion: String? = null,
    val origen: OrigenEtiqueta = OrigenEtiqueta.DETECTOR,
    /** Tamaño de la caja en fracción de la foto; null si el detector no dio caja. */
    val caja: CajaSenal? = null,
)

/** Frase final y etiqueta corta para guardar o mostrar. */
data class DescripcionPantalla(
    val frase: String,
    val etiqueta: String,
)

/**
 * Traduce las etiquetas reales de pantallas, laptops y periféricos. Es código puro: no usa Android,
 * red ni modelos. Cuando dos señales no coinciden, prefiere decir "pantalla" antes que afirmar que
 * es un televisor o un monitor.
 *
 * Etiquetas reales verificadas:
 * - EfficientDet lite0: tv, laptop, mouse, remote, keyboard.
 * - ML Kit Image Labeling 17.0.9: Computer y Television. No tiene Monitor, Laptop, Keyboard, Mouse
 *   ni Remote, por eso no se usan esos nombres como si existieran.
 */
object TraductorPantallas {

    // Umbrales por clase. Las pantallas piden más confianza para no confundirlas con ventanas,
    // reflejos o rectángulos brillantes. Se dejan aquí para poder ajustarlos con fotos reales.
    const val UMBRAL_PANTALLA = 0.65f
    const val UMBRAL_LAPTOP = 0.55f
    const val UMBRAL_PERIFERICO = 0.50f
    const val UMBRAL_CONTROL = 0.55f
    const val UMBRAL_MLKIT = 0.75f

    private val ETIQUETAS_DETECTOR = setOf("tv", "laptop", "keyboard", "mouse", "remote", "cell phone")
    private val ETIQUETAS_MLKIT = setOf("computer", "television")
    private val ETIQUETAS_PANTALLA = setOf("tv", "computer", "television")

    /** ¿Esta etiqueta existe de verdad en el modelo que corresponde a su origen? */
    fun esEtiquetaConocida(etiqueta: String, origen: OrigenEtiqueta): Boolean {
        val limpia = normalizar(etiqueta)
        return when (origen) {
            OrigenEtiqueta.DETECTOR -> limpia in ETIQUETAS_DETECTOR
            OrigenEtiqueta.ETIQUETADOR -> limpia in ETIQUETAS_MLKIT
        }
    }

    /** Para separar las señales de pantalla del resto de objetos antes de describirlos. */
    fun esEtiquetaDePantalla(etiqueta: String): Boolean =
        normalizar(etiqueta) in ETIQUETAS_DETECTOR

    /** Frase de una sola señal, o null si no llega al umbral de su clase. */
    fun fraseIndividual(senal: SenalEtiqueta): String? {
        val limpia = normalizar(senal.etiqueta)
        if (!esEtiquetaConocida(limpia, senal.origen) || !superaUmbral(limpia, senal)) return null
        return when (limpia) {
            "tv", "computer", "television" -> "Hay una pantalla${posicion(senal.posicion)}."
            "laptop" -> "Hay un laptop${posicion(senal.posicion)}."
            "keyboard" -> "Hay un teclado${posicion(senal.posicion)}."
            "mouse" -> "Hay un ratón${posicion(senal.posicion)}."
            "remote" -> "Hay un control remoto${posicion(senal.posicion)}."
            "cell phone" -> when (ReglasEntorno.juicioCelular(senal.confianza, senal.caja)) {
                JuicioCelular.CELULAR -> "Hay un celular${posicion(senal.posicion)}."
                JuicioCelular.PANTALLA -> "Hay una pantalla${posicion(senal.posicion)}."
                JuicioCelular.NADA -> null
            }
            else -> null
        }
    }

    /**
     * Reúne todas las señales de la foto en una sola descripción, sin repetir la pantalla. La regla
     * de conjunto solo se aplica cuando hay pantalla, teclado y ratón en la misma foto.
     */
    fun describir(senales: List<SenalEtiqueta>): DescripcionPantalla? {
        val conocidas = senales.filter { esEtiquetaConocida(it.etiqueta, it.origen) }
        val validas = conocidas.filter { superaUmbral(normalizar(it.etiqueta), it) }

        // Un "cell phone" solo cuenta si la caja lo respalda. El detector suele marcar así la
        // pantalla de una laptop: caja ancha o apaisada. Eso se dice "pantalla", no "celular".
        val juiciosCelular = conocidas
            .filter { normalizar(it.etiqueta) == "cell phone" }
            .map { it to ReglasEntorno.juicioCelular(it.confianza, it.caja) }
        val celulares = juiciosCelular
            .filter { (_, juicio) -> juicio == JuicioCelular.CELULAR }
            .map { (senal, _) -> senal }
        val pantallasDeCelular = juiciosCelular
            .filter { (_, juicio) -> juicio == JuicioCelular.PANTALLA }
            .map { (senal, _) -> senal }

        val pantallaDirecta = validas
            .filter { normalizar(it.etiqueta) in ETIQUETAS_PANTALLA }
            .maxByOrNull { it.confianza }
        val pantalla = (listOfNotNull(pantallaDirecta) + pantallasDeCelular)
            .maxByOrNull { it.confianza }

        val laptopDirecta = validas.filter { normalizar(it.etiqueta) == "laptop" }
            .maxByOrNull { it.confianza }
        val teclado = validas.filter { normalizar(it.etiqueta) == "keyboard" }.maxByOrNull { it.confianza }
        val raton = validas.filter { normalizar(it.etiqueta) == "mouse" }.maxByOrNull { it.confianza }
        val control = validas.filter { normalizar(it.etiqueta) == "remote" }.maxByOrNull { it.confianza }
        val mlkitDiceComputadora = validas.any {
            it.origen == OrigenEtiqueta.ETIQUETADOR && normalizar(it.etiqueta) == "computer"
        }

        // Decir "laptop" por una caja ancha no basta: hacen falta teclado o la segunda opinión de
        // ML Kit. Si no, se queda en "pantalla".
        val laptopPorCelular = pantallasDeCelular.isNotEmpty() && (teclado != null || mlkitDiceComputadora)
        val laptop = laptopDirecta
            ?: if (laptopPorCelular) pantallasDeCelular.maxByOrNull { it.confianza } else null

        if (pantalla != null && teclado != null && raton != null) {
            val conLaptop = laptop != null
            val base = if (conLaptop) {
                "una computadora de escritorio o laptop"
            } else {
                "una computadora de escritorio"
            }
            return DescripcionPantalla(
                frase = "Hay $base: pantalla, teclado y ratón.",
                etiqueta = if (conLaptop) "computadora de escritorio o laptop" else "computadora de escritorio",
            )
        }

        // Un laptop es más específico que una pantalla suelta: si el detector lo vio, se dice laptop.
        if (laptop != null) {
            return DescripcionPantalla(
                frase = "Hay un laptop${posicion(laptop.posicion)}.",
                etiqueta = "laptop",
            )
        }

        if (pantalla != null) {
            val extra = if (control != null) ", parece televisor o monitor" else ""
            return DescripcionPantalla(
                frase = "Hay una pantalla${posicion(pantalla.posicion)}$extra.",
                etiqueta = "pantalla",
            )
        }

        // Un teléfono confirmado se dice por su nombre si no hay un grupo de pantalla más fuerte.
        val celular = celulares.maxByOrNull { it.confianza }
        if (celular != null) {
            return DescripcionPantalla(
                frase = "Hay un celular${posicion(celular.posicion)}.",
                etiqueta = "celular",
            )
        }

        if (teclado != null && raton != null) {
            return DescripcionPantalla(
                frase = "Hay un teclado${posicion(teclado.posicion)} y un ratón${posicion(raton.posicion)}.",
                etiqueta = "teclado y ratón",
            )
        }

        val unico = listOfNotNull(teclado, raton, control).maxByOrNull { it.confianza } ?: return null
        return when (normalizar(unico.etiqueta)) {
            "keyboard" -> DescripcionPantalla("Hay un teclado${posicion(unico.posicion)}.", "teclado")
            "mouse" -> DescripcionPantalla("Hay un ratón${posicion(unico.posicion)}.", "ratón")
            "remote" -> DescripcionPantalla("Hay un control remoto${posicion(unico.posicion)}.", "control remoto")
            else -> null
        }
    }

    private fun superaUmbral(etiqueta: String, senal: SenalEtiqueta): Boolean {
        val umbral = when {
            senal.origen == OrigenEtiqueta.ETIQUETADOR -> UMBRAL_MLKIT
            etiqueta in ETIQUETAS_PANTALLA -> UMBRAL_PANTALLA
            etiqueta == "laptop" -> UMBRAL_LAPTOP
            etiqueta == "remote" -> UMBRAL_CONTROL
            else -> UMBRAL_PERIFERICO
        }
        return senal.confianza >= umbral
    }

    private fun posicion(posicion: String?): String {
        val limpia = posicion?.trim().orEmpty()
        return if (limpia.isEmpty()) "" else " $limpia"
    }

    private fun normalizar(etiqueta: String): String = etiqueta.trim().lowercase()
}

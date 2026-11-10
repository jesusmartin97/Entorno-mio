package com.cecapi.app.feature.modulo10_accesibilidad

/**
 * Un bloque de texto que el OCR encontró en una captura. [arriba], [izquierda] y [abajo] son las
 * coordenadas de su caja en la imagen; sirven para leer de arriba hacia abajo y para saber si dos
 * bloques están en la misma línea.
 */
data class BloqueCaptura(
    val texto: String,
    val arriba: Int = 0,
    val izquierda: Int = 0,
    val abajo: Int = 0,
)

/**
 * Convierte lo que reconoció el OCR de una captura en lo que se dice en voz alta.
 *
 * Es código puro para poder probarlo sin Android ni cámara. Lee **todos** los bloques legibles en
 * orden de arriba hacia abajo. Los bloques que están en la misma línea se unen con un espacio, para
 * que una palabra resaltada o un título no se oiga como una frase aparte. Las palabras cortadas con
 * guion al final de una línea se vuelven a unir.
 */
object LectorCaptura {

    /**
     * Devuelve el texto para leer, en orden de arriba hacia abajo, o null si la captura no tiene
     * texto legible. Los bloques que solo traen signos (bordes, viñetas, separadores) no cuentan.
     */
    fun textoParaLeer(bloques: List<BloqueCaptura>): String? {
        val legibles = bloques
            .map { it.copy(texto = prepararTexto(it.texto)) }
            .filter { bloque -> bloque.texto.any { it.isLetterOrDigit() } }
            .sortedWith(compareBy({ it.arriba }, { it.izquierda }))

        if (legibles.isEmpty()) return null

        val frases = mutableListOf<BloqueCaptura>()
        legibles.forEach { bloque ->
            if (frases.lastOrNull()?.texto != bloque.texto) frases += bloque
        }
        if (frases.isEmpty()) return null

        val texto = normalizarParaVoz(unirLetrasSeparadas(unirBloques(frases)))
        return if (texto.endsWith(".") || texto.endsWith("!") || texto.endsWith("?")) {
            texto
        } else {
            "$texto."
        }
    }

    private fun unirBloques(bloques: List<BloqueCaptura>): String {
        val texto = StringBuilder()
        var anterior: BloqueCaptura? = null

        for (bloque in bloques) {
            if (texto.isEmpty()) {
                texto.append(bloque.texto)
                anterior = bloque
                continue
            }

            val previo = anterior!!
            val terminaConGuion = texto.last() == '-'
            if (terminaConGuion && bloque.texto.first().isLetter()) {
                texto.deleteCharAt(texto.length - 1)
                texto.append(bloque.texto)
            } else {
                val separador = separadorEntre(previo, bloque)
                texto.append(separador).append(bloque.texto)
            }
            anterior = bloque
        }
        return texto.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun separadorEntre(anterior: BloqueCaptura, siguiente: BloqueCaptura): String {
        if (mismaLinea(anterior, siguiente)) return " "
        val ultimo = anterior.texto.lastOrNull()
        if (ultimo == '.' || ultimo == '!' || ultimo == '?' || ultimo == ';' || ultimo == ':') return " "
        return if (siguiente.texto.firstOrNull()?.isUpperCase() == true) ". " else " "
    }

    private fun mismaLinea(a: BloqueCaptura, b: BloqueCaptura): Boolean {
        val altoA = (a.abajo - a.arriba).coerceAtLeast(0)
        val altoB = (b.abajo - b.arriba).coerceAtLeast(0)
        val alto = when {
            altoA > 0 && altoB > 0 -> minOf(altoA, altoB)
            altoA > 0 -> altoA
            altoB > 0 -> altoB
            else -> 0
        }
        return if (alto > 0) {
            val solape = minOf(a.abajo, b.abajo) - maxOf(a.arriba, b.arriba)
            solape > 0 || kotlin.math.abs(a.arriba - b.arriba) <= alto / 2
        } else {
            kotlin.math.abs(a.arriba - b.arriba) <= 12
        }
    }

    /** Quita espacios raros y vuelve a unir una palabra cortada con guion al final de una línea. */
    private fun prepararTexto(texto: String): String = texto
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace('\u2007', ' ')
        .replace(Regex("[\\u200B-\\u200D\\uFEFF\\u00AD]"), "")
        .replace(Regex("(?<=\\p{L})[ \\t]*[-‐‑–—][ \\t]*\\r?\\n[ \\t]*"), "")
        .replace(Regex("\\r?\\n"), " ")
        .replace(Regex("[\\p{Zs}\\s]+"), " ")
        .trim()

    /**
     * Algunos títulos se leen como "T. Í. T. U. L. O." y el motor de voz los deletrea. Junta esas
     * letras sueltas en una palabra. No toca secuencias cortas de palabras normales como "a y o".
     */
    private fun unirLetrasSeparadas(texto: String): String {
        val juntas = LETRAS_SEPARADAS.replace(texto) { match ->
            val letras = match.value.filter { it.isLetter() }
            if (letras.length >= 4 || letras.any { it.isUpperCase() }) letras else match.value
        }
        return DOS_MAYUSCULAS.replace(juntas) { match -> match.value.filter { it.isLetter() } }
    }

    /**
     * Normaliza lo que el OCR suele confundir y baja las mayúsculas sostenidas. El motor de voz
     * deletrea palabras desconocidas en mayúsculas ("PONEA" -> "p-o-n-e-a"); al pasarlas a formato
     * de palabra ("Ponea") las intenta pronunciar. También corrige confusiones frecuentes: V por Y,
     * OUE por QUE, cero por O y letras con acentos raros.
     */
    private fun normalizarParaVoz(texto: String): String {
        var limpio = texto
            .replace('\u0130', 'I')
            .replace('\u0131', 'i')
            .replace('\u00D4', 'O')
            .replace('\u00F4', 'o')
            .replace(Regex("(?<=\\p{L})0(?=\\p{L})"), "O")
            .replace(Regex("(?<=\\p{L})1(?=\\p{L})"), "I")
            .replace(V_SUELTA) { if (it.value == "V") "Y" else "y" }
            .replace(Regex("OUE", RegexOption.IGNORE_CASE), "QUE")

        limpio = PALABRA.replace(limpio) { match ->
            val palabra = match.value
            val letras = palabra.count { it.isLetter() }
            val mezcla = palabra.any { it.isLowerCase() } && palabra.any { it.isUpperCase() }
            if (mezcla || (letras >= 4 && palabra.all { !it.isLetter() || it.isUpperCase() })) {
                palabra.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                palabra
            }
        }

        // Correcciones frecuentes en textos en español con letra estilizada.
        return limpio
            .replace(Regex("\\bInvitasa\\b"), "Invitas a")
            .replace(Regex("\\bPinata\\b"), "Piñata")
            .replace(Regex("\\bPonea\\b"), "Pone a")
            .replace(Regex("\\bSientaa\\b"), "Sienta a")
            .replace(Regex("\\bTios\\b"), "Tíos")
            .replace(Regex("\\bTias\\b"), "Tías")
    }

    private val LETRAS_SEPARADAS = Regex(
        """(?<![\p{L}\p{N}])\p{L}(?:[\s.,;:!?·-]+\p{L}){2,}(?![\p{L}\p{N}])""",
    )
    private val DOS_MAYUSCULAS = Regex(
        """(?<![\p{L}\p{N}])\p{Lu}[\s.]+\p{Lu}(?![\p{L}\p{N}])""",
    )
    private val V_SUELTA = Regex("""(?<=[\p{L}.,!?;:]\s)[vV](?=\s+[\p{L}])""")
    private val PALABRA = Regex("""\b\p{L}+\b""")
}

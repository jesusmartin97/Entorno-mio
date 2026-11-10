package com.cecapi.app.feature.modulo10_accesibilidad

/**
 * Prepara el texto que se va a leer en voz alta para que las unidades, monedas y abreviaturas
 * suenen como se dicen en México, no como las interpreta el motor de voz.
 *
 * Ejemplos:
 * - `$1,200.00` -> `1,200 pesos`
 * - `28"` -> `28 pulgadas`
 * - `100-240 V` -> `100 a 240 voltios`
 * - `50/60 Hz` -> `50 o 60 hercios`
 * - `45 W` -> `45 vatios`
 * - `5000 mAh` -> `5000 miliamperios hora`
 *
 * Es código puro: no depende de Android y se puede probar con JUnit.
 */
object NormalizadorLectura {

    fun normalizar(texto: String): String {
        if (texto.isBlank()) return texto
        var limpio = normalizarPalabras(texto)
        limpio = normalizarMoneda(limpio)
        limpio = normalizarRangos(limpio)
        limpio = normalizarUnidades(limpio)
        limpio = normalizarAbreviaturas(limpio)
        return limpio.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Baja las mayúsculas sostenidas y arregla letras que el OCR o el árbol de accesibilidad
     * entregan en formatos que el motor de voz deletrea. "MODELO" termina como "modelo"; una
     * palabra común en minúsculas se pronuncia, no se deletrea.
     */
    private fun normalizarPalabras(texto: String): String {
        var limpio = texto
            .replace('\u0130', 'I')
            .replace('\u0131', 'i')
            .replace('\u00D4', 'O')
            .replace('\u00F4', 'o')
            .replace(Regex("(?<=\\p{L})0(?=\\p{L})"), "O")
            .replace(Regex("(?<=\\p{L})1(?=\\p{L})"), "I")

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

        // Es una palabra común de las etiquetas; en minúsculas el motor de voz la pronuncia.
        return limpio.replace(Regex("""\bModelo\b"""), "modelo")
    }

    // ---- Moneda ---------------------------------------------------------------------------------

    private fun normalizarMoneda(texto: String): String {
        var t = texto

        // Dólares explícitos. Si el texto dice USD, sí son dólares.
        t = t.replace(Regex("""\$\s*($MONTO)\s*USD""", RegexOption.IGNORE_CASE)) { montoDolares(it.groupValues[1]) }
        t = t.replace(Regex("""USD\s*\$?\s*($MONTO)""", RegexOption.IGNORE_CASE)) { montoDolares(it.groupValues[1]) }
        t = t.replace(Regex("""US\$\s*($MONTO)""", RegexOption.IGNORE_CASE)) { montoDolares(it.groupValues[1]) }
        t = t.replace(Regex("""($MONTO)\s*USD""", RegexOption.IGNORE_CASE)) { montoDolares(it.groupValues[1]) }

        // Pesos explícitos y el símbolo $, que en México se lee como pesos.
        t = t.replace(Regex("""(?:MX\$|MXN)\s*\$?\s*($MONTO)""", RegexOption.IGNORE_CASE)) { montoPesos(it.groupValues[1]) }
        t = t.replace(Regex("""($MONTO)\s*(?:MXN|M\.N\.)""", RegexOption.IGNORE_CASE)) { montoPesos(it.groupValues[1]) }
        t = t.replace(Regex("""\$\s*($MONTO)\s*MXN""", RegexOption.IGNORE_CASE)) { montoPesos(it.groupValues[1]) }
        t = t.replace(Regex("""\$\s*($MONTO)""")) { montoPesos(it.groupValues[1]) }
        t = t.replace(Regex("""($MONTO)\s*\$""")) { montoPesos(it.groupValues[1]) }

        t = t.replace(Regex("""\bUSD\b""", RegexOption.IGNORE_CASE), "dólares")
        t = t.replace(Regex("""\b(?:dls|dlls)\b\.?""", RegexOption.IGNORE_CASE), "dólares")
        t = t.replace(Regex("""\bMXN\b""", RegexOption.IGNORE_CASE), "pesos")
        t = t.replace("M.N.", "pesos")
        t = t.replace("MX$", "pesos")
        t = t.replace("US$", "dólares")
        t = t.replace("€", "euros")
        t = t.replace("£", "libras")
        t = t.replace(Regex("""\bEUR\b""", RegexOption.IGNORE_CASE), "euros")
        t = t.replace(Regex("""\bdollars?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "dólares" else "dólar"
        }
        t = t.replace("$", "pesos")
        return t
    }

    private fun montoPesos(cantidad: String): String = monto(cantidad, "peso", "pesos")

    private fun montoDolares(cantidad: String): String = monto(cantidad, "dólar", "dólares")

    private fun monto(cantidad: String, singular: String, plural: String): String {
        val limpio = cantidad.replace(Regex("""[.,]00$"""), "")
        return "$limpio ${if (esSingular(limpio)) singular else plural}"
    }

    // ---- Rangos ---------------------------------------------------------------------------------

    private fun normalizarRangos(texto: String): String {
        var t = RANGO_HZ.replace(texto) { match ->
            "${match.groupValues[1]} o ${match.groupValues[2]} ${nombreUnidad("Hz", "2")}"
        }
        t = RANGO_UNIDAD.replace(t) { match ->
            val nombre = nombreUnidad(match.groupValues[3], "2") ?: match.groupValues[3]
            "${match.groupValues[1]} a ${match.groupValues[2]} $nombre"
        }
        return t
    }

    // ---- Unidades -------------------------------------------------------------------------------

    private fun normalizarUnidades(texto: String): String {
        var t = UNIDAD_REGEX.replace(texto) { match ->
            val numero = match.groupValues[1]
            val nombre = nombreUnidad(match.groupValues[2], numero) ?: match.groupValues[2]
            "$numero $nombre"
        }

        // La A sola se trata aparte para no confundir la preposición "a" con amperios.
        t = AMPERIOS.replace(t) { match ->
            val numero = match.groupValues[1]
            "$numero ${if (esSingular(numero)) "amperio" else "amperios"}"
        }

        t = PULGADAS_COMILLAS.replace(t) { match ->
            val numero = match.groupValues[1]
            "$numero ${if (esSingular(numero)) "pulgada" else "pulgadas"}"
        }

        t = PIES_APOSTROFO.replace(t) { match ->
            val numero = match.groupValues[1]
            "$numero ${if (esSingular(numero)) "pie" else "pies"}"
        }

        // En etiquetas, V~ marca corriente alterna. El símbolo suelto no se debe leer.
        t = t.replace(Regex("""\b(voltios|vatios|hercios)\s*~""")) { it.groupValues[1] }

        return t
    }

    private fun nombreUnidad(unidad: String, numero: String): String? {
        val clave = unidad.lowercase().trimEnd('.')
        val encontrada = UNIDADES.firstOrNull { it.clave == clave } ?: return null
        return if (esSingular(numero)) encontrada.singular else encontrada.plural
    }

    private fun esSingular(numero: String): Boolean = Regex("""^1(?:[.,]0{1,2})?$""").matches(numero)

    // ---- Abreviaturas y palabras en inglés ------------------------------------------------------

    private fun normalizarAbreviaturas(texto: String): String {
        var t = texto

        t = t.replace(
            Regex("""\bS\.A\.\s+de\s+C\.V\.?""", RegexOption.IGNORE_CASE),
            "Sociedad Anónima de Capital Variable",
        )
        t = t.replace(Regex("""\bS\.A\.(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE), "Sociedad Anónima")
        t = t.replace(Regex("""\bC\.V\.(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE), "Capital Variable")
        t = t.replace(Regex("""\bEE\.UU\.""", RegexOption.IGNORE_CASE), "Estados Unidos")
        t = t.replace(Regex("""\bMéx\.(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE), "México")

        t = t.replace(Regex("""\bNo\.\s*($MONTO)""", RegexOption.IGNORE_CASE)) { "número ${it.groupValues[1]}" }
        t = t.replace(Regex("""\bN\.?[º°]\s*($MONTO)""", RegexOption.IGNORE_CASE)) { "número ${it.groupValues[1]}" }
        t = t.replace(Regex("""#\s*($MONTO)""")) { "número ${it.groupValues[1]}" }

        t = t.replace(
            Regex("""\bNOM\s*-\s*""", RegexOption.IGNORE_CASE),
            "Norma Oficial Mexicana ",
        )
        t = t.replace(Regex("""\bNOM\b"""), "Norma Oficial Mexicana")
        t = t.replace(Regex("""\bRFC\b"""), "Registro Federal de Contribuyentes")
        t = t.replace(Regex("""\bIVA\b"""), "iva")
        t = t.replace(Regex("""\bC\.P\.(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE), "Código Postal")
        t = t.replace(Regex("""\bTel\.\s*""", RegexOption.IGNORE_CASE), "Teléfono ")
        t = t.replace(Regex("""\bAv\.\s*""", RegexOption.IGNORE_CASE), "Avenida ")
        t = t.replace(Regex("""\bDra\.\s*""", RegexOption.IGNORE_CASE), "Doctora ")
        t = t.replace(Regex("""\bDr\.\s*""", RegexOption.IGNORE_CASE), "Doctor ")
        t = t.replace(Regex("""\bIng\.\s*""", RegexOption.IGNORE_CASE), "Ingeniero ")
        t = t.replace(Regex("""\bLic\.\s*""", RegexOption.IGNORE_CASE), "Licenciado ")
        t = t.replace(Regex("""\bpágs\.\s*""", RegexOption.IGNORE_CASE), "páginas ")
        t = t.replace(Regex("""\bpág\.\s*""", RegexOption.IGNORE_CASE), "página ")
        t = t.replace(Regex("""\betc\.(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE), "etcétera")

        t = t.replace(Regex("""\bTV\b"""), "televisión")
        t = t.replace(Regex("""\bLED\b"""), "led")
        t = t.replace(Regex("""\bWi-?Fi\b""", RegexOption.IGNORE_CASE), "wifi")

        // Unidades escritas en inglés.
        t = t.replace(Regex("""\bwatts?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "vatios" else "vatio"
        }
        t = t.replace(Regex("""\bvolts?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "voltios" else "voltio"
        }
        t = t.replace(Regex("""\binches?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "pulgadas" else "pulgada"
        }
        t = t.replace(Regex("""\bhertz\b""", RegexOption.IGNORE_CASE), "hercios")
        t = t.replace(Regex("""\bpounds?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "libras" else "libra"
        }
        t = t.replace(Regex("""\bounces?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "onzas" else "onza"
        }
        t = t.replace(Regex("""\bgallons?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "galones" else "galón"
        }
        t = t.replace(Regex("""\byards?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "yardas" else "yarda"
        }
        t = t.replace(Regex("""\bdegrees?\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase().endsWith("s")) "grados" else "grado"
        }
        t = t.replace(Regex("""\b(?:feet|foot)\b""", RegexOption.IGNORE_CASE)) {
            if (it.value.lowercase() == "feet") "pies" else "pie"
        }
        t = t.replace(Regex("""($MONTO)\s*miles?\b""", RegexOption.IGNORE_CASE)) {
            val numero = it.groupValues[1]
            "$numero ${if (esSingular(numero)) "milla" else "millas"}"
        }
        t = t.replace(Regex("""($MONTO)\s*whats?\b""", RegexOption.IGNORE_CASE)) {
            val numero = it.groupValues[1]
            "$numero ${if (esSingular(numero)) "vatio" else "vatios"}"
        }

        t = t.replace("@", "arroba")
        t = t.replace("&", "y")
        return t
    }

    // ---- Patrones -------------------------------------------------------------------------------

    private const val MONTO = """(\d[\d.,]*\d|\d)"""

    private val PALABRA = Regex("""\b\p{L}+\b""")

    private data class Unidad(
        val patron: String,
        val clave: String,
        val singular: String,
        val plural: String,
        val ignorarMayusculas: Boolean = true,
    )

    private val UNIDADES = listOf(
        Unidad("kWh", "kwh", "kilovatio hora", "kilovatios hora"),
        Unidad("MWh", "mwh", "megavatio hora", "megavatios hora"),
        Unidad("Wh", "wh", "vatio hora", "vatios hora"),
        Unidad("mAh", "mah", "miliamperio hora", "miliamperios hora"),
        Unidad("Ah", "ah", "amperio hora", "amperios hora"),
        Unidad("kHz", "khz", "kilohercio", "kilohercios"),
        Unidad("MHz", "mhz", "megahercio", "megahercios"),
        Unidad("GHz", "ghz", "gigahercio", "gigahercios"),
        Unidad("Hz", "hz", "hercio", "hercios"),
        Unidad("kV", "kv", "kilovoltio", "kilovoltios"),
        Unidad("mV", "mv", "milivoltio", "milivoltios"),
        Unidad("V", "v", "voltio", "voltios"),
        Unidad("kW", "kw", "kilovatio", "kilovatios"),
        Unidad("W", "w", "vatio", "vatios"),
        Unidad("mA", "ma", "miliamperio", "miliamperios"),
        Unidad("km/h", "km/h", "kilómetro por hora", "kilómetros por hora"),
        Unidad("m/s", "m/s", "metro por segundo", "metros por segundo"),
        Unidad("mph", "mph", "milla por hora", "millas por hora"),
        Unidad("rpm", "rpm", "revolución por minuto", "revoluciones por minuto"),
        Unidad("dB", "db", "decibel", "decibeles"),
        Unidad("lm", "lm", "lumen", "lúmenes"),
        Unidad("hp", "hp", "caballo de fuerza", "caballos de fuerza"),
        Unidad("m²", "m²", "metro cuadrado", "metros cuadrados"),
        Unidad("m2", "m2", "metro cuadrado", "metros cuadrados"),
        Unidad("cm²", "cm²", "centímetro cuadrado", "centímetros cuadrados"),
        Unidad("cm2", "cm2", "centímetro cuadrado", "centímetros cuadrados"),
        Unidad("ft²", "ft²", "pie cuadrado", "pies cuadrados"),
        Unidad("ft2", "ft2", "pie cuadrado", "pies cuadrados"),
        Unidad("m³", "m³", "metro cúbico", "metros cúbicos"),
        Unidad("m3", "m3", "metro cúbico", "metros cúbicos"),
        Unidad("cm³", "cm³", "centímetro cúbico", "centímetros cúbicos"),
        Unidad("cm3", "cm3", "centímetro cúbico", "centímetros cúbicos"),
        Unidad("cc", "cc", "centímetro cúbico", "centímetros cúbicos"),
        Unidad("mm", "mm", "milímetro", "milímetros"),
        Unidad("cm", "cm", "centímetro", "centímetros"),
        Unidad("km", "km", "kilómetro", "kilómetros"),
        Unidad("kg", "kg", "kilogramo", "kilogramos"),
        Unidad("mg", "mg", "miligramo", "miligramos"),
        Unidad("ml", "ml", "mililitro", "mililitros"),
        Unidad("gal", "gal", "galón", "galones"),
        Unidad("lbs", "lbs", "libra", "libras"),
        Unidad("lb", "lb", "libra", "libras"),
        Unidad("oz", "oz", "onza", "onzas"),
        Unidad("ft", "ft", "pie", "pies"),
        Unidad("in", "in", "pulgada", "pulgadas"),
        Unidad("pulg\\.?", "pulg", "pulgada", "pulgadas"),
        Unidad("min", "min", "minuto", "minutos"),
        Unidad("[Ll]", "l", "litro", "litros", ignorarMayusculas = false),
        Unidad("m", "m", "metro", "metros", ignorarMayusculas = false),
        Unidad("g", "g", "gramo", "gramos", ignorarMayusculas = false),
        Unidad("h", "h", "hora", "horas", ignorarMayusculas = false),
        Unidad("s", "s", "segundo", "segundos", ignorarMayusculas = false),
        Unidad("%", "%", "por ciento", "por ciento", ignorarMayusculas = false),
        Unidad("°C", "°c", "grado Celsius", "grados Celsius"),
        Unidad("°F", "°f", "grado Fahrenheit", "grados Fahrenheit"),
        Unidad("Ω", "ω", "ohmio", "ohmios"),
        Unidad("ohm", "ohm", "ohmio", "ohmios"),
    )

    private val PATRON_UNIDADES = UNIDADES
        .sortedByDescending { it.clave.length }
        .joinToString("|") { unidad ->
            if (unidad.ignorarMayusculas) "(?i:${unidad.patron})" else unidad.patron
        }

    private val UNIDAD_REGEX = Regex(
        """(?<![\p{L}\p{N}])(\d+(?:[.,]\d+)?)\s*($PATRON_UNIDADES)(?![\p{L}\p{N}])""",
    )

    private val AMPERIOS = Regex(
        """(?<![\p{L}\p{N}])(\d+(?:[.,]\d+)?)\s*A(?![\p{L}\p{N}])""",
    )

    private val PULGADAS_COMILLAS = Regex(
        """(?<![\p{L}\p{N}])(\d+(?:[.,]\d+)?)\s*["”″](?![\p{L}\p{N}])""",
    )

    private val PIES_APOSTROFO = Regex(
        """(?<![\p{L}\p{N}])(\d+(?:[.,]\d+)?)\s*['′](?![\p{L}\p{N}])""",
    )

    private val RANGO_HZ = Regex(
        """(?<![\p{L}\p{N}])(\d+(?:[.,]\d+)?)\s*/\s*(\d+(?:[.,]\d+)?)\s*(?:Hz|HZ|hz)(?![\p{L}\p{N}])""",
    )

    private val RANGO_UNIDAD = Regex(
        """(?<![\p{L}\p{N}])(\d+(?:[.,]\d+)?)\s*[-–—]\s*(\d+(?:[.,]\d+)?)\s*($PATRON_UNIDADES)(?![\p{L}\p{N}])""",
    )
}

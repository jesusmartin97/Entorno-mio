package com.cecapi.app.feature.modulo7_entorno

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Denominaciones de los billetes mexicanos en circulación y cómo se dicen en voz alta. */
enum class DenominacionBillete(val valor: Int, val nombreHablado: String) {
    D20(20, "veinte pesos"),
    D50(50, "cincuenta pesos"),
    D100(100, "cien pesos"),
    D200(200, "doscientos pesos"),
    D500(500, "quinientos pesos"),
    D1000(1000, "mil pesos"),
}

/**
 * Una palabra leída por el OCR, qué tan alta es en la foto y dónde está. Los números de la denominación son lo
 * más grande del billete, así que el tamaño sirve para distinguirlos de números chicos (serie, fecha, microtexto).
 * [alto] = 0 significa "no se sabe" y todas las palabras pesan igual.
 * [x] y [y] son el centro de la palabra en la foto; -1 significa "no se sabe". Sirven para no contar dos veces
 * un mismo número que el OCR partió en dos y, con ello, para reconocer varios billetes en la misma foto.
 */
data class PalabraOcr(val texto: String, val alto: Int = 0, val x: Int = -1, val y: Int = -1)

/**
 * @property denominacion la denominación exacta, solo cuando hay evidencia clara; si no, null.
 * @property esBilleteSeguro se tiene la certeza de que lo que hay en la foto es un billete.
 * @property incierto es un billete, pero no se pudo decir de cuánto (lejos, borroso, o dos lecturas distintas).
 * @property sugerida pista débil (por color o por una lectura dudosa). Nunca se dice como si fuera un hecho.
 * @property adicionales otras denominaciones distintas que también se leen con claridad en la misma foto
 *   (hay varios billetes). Si está vacía, hay un solo billete. Billetes iguales no se cuentan por separado.
 */
data class ResultadoBillete(
    val denominacion: DenominacionBillete?,
    val esBilleteSeguro: Boolean,
    val incierto: Boolean,
    val sugerida: DenominacionBillete? = null,
    val adicionales: List<DenominacionBillete> = emptyList(),
    val cantidades: Map<DenominacionBillete, Int> = emptyMap(),
) {
    /** Todas las denominaciones confirmadas en la foto: la principal y las adicionales. */
    val todas: List<DenominacionBillete>
        get() = listOfNotNull(denominacion) + adicionales

    /** Cuántos billetes de [d] se estima que hay en la foto (al menos 1 si [d] está confirmada). */
    fun cantidadDe(d: DenominacionBillete): Int = cantidades[d] ?: if (d in todas) 1 else 0

    /** Hay más de un billete en total (de distintas denominaciones o varios iguales). */
    val hayVarios: Boolean
        get() = todas.sumOf { cantidadDe(it) } > 1

    /** Valor total estimado en pesos. Solo es tan confiable como el conteo. */
    val totalPesos: Int
        get() = todas.sumOf { it.valor * cantidadDe(it) }
}

object BilleteMx {

    private val VALIDAS = DenominacionBillete.entries.associateBy { it.valor }

    // Palabras impresas en el billete. Se pide coincidencia casi exacta para no adivinar.
    private val PALABRAS = mapOf(
        "VEINTE" to DenominacionBillete.D20,
        "CINCUENTA" to DenominacionBillete.D50,
        "CIEN" to DenominacionBillete.D100,
        "DOSCIENTOS" to DenominacionBillete.D200,
        "QUINIENTOS" to DenominacionBillete.D500,
        "MIL" to DenominacionBillete.D1000,
    )

    // Con dos de estas palabras (o con BANXICO) se toma como billete aunque la etiqueta de imagen no lo diga.
    private val PISTAS_DE_BILLETE = setOf(
        "BANXICO", "BANCO", "MEXICO", "MEXICANOS", "ESTADOS", "UNIDOS", "PESOS", "SERIE", "BILLETE",
    )

    // Lo que el OCR suele confundir en un número: O/Q/D por 0, I/L por 1, S por 5, Z por 2.
    private val CONFUSIONES = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0',
        'I' to '1', 'L' to '1', '|' to '1',
        'S' to '5', 'Z' to '2',
    )

    /** Versión simple: solo texto, sin tamaños. */
    fun reconocer(
        textoExtraido: String,
        esBilletePorImagen: Boolean,
        usuarioPideBillete: Boolean = false,
    ): ResultadoBillete = reconocer(
        palabras = textoExtraido.split(Regex("\\s+")).filter { it.isNotBlank() }.map { PalabraOcr(it) },
        esBilletePorImagen = esBilletePorImagen,
        usuarioPideBillete = usuarioPideBillete,
    )

    /**
     * @param palabras lo que leyó el OCR, palabra por palabra.
     * @param esBilletePorImagen el etiquetador de imágenes dijo "billete".
     * @param usuarioPideBillete la persona dijo "qué billete es": se asume que sí es un billete.
     * @param colores qué porción de la foto tiene el color típico de cada denominación (0 a 1). Solo apoya.
     */
    fun reconocer(
        palabras: List<PalabraOcr>,
        esBilletePorImagen: Boolean,
        usuarioPideBillete: Boolean = false,
        colores: Map<DenominacionBillete, Float> = emptyMap(),
    ): ResultadoBillete {
        val limpias = palabras.map { PalabraOcr(normalizar(it.texto), it.alto, it.x, it.y) }
            .filter { it.texto.isNotEmpty() }

        val pistas = limpias.map { it.texto }.filter { it in PISTAS_DE_BILLETE }.toSet()
        val tienePistas = "BANXICO" in pistas || pistas.size >= 2
        val esBillete = esBilletePorImagen || usuarioPideBillete || tienePistas

        // Lecturas de número y de palabra, cada una con su tamaño.
        val lecturas = mutableListOf<Lectura>()
        for (p in limpias) {
            numeroDe(p.texto)?.let { lecturas += Lectura(it, p.alto, esNumero = true, x = p.x, y = p.y) }
            palabraDe(p.texto)?.let { lecturas += Lectura(it, p.alto, esNumero = false, x = p.x, y = p.y) }
        }

        if (lecturas.isEmpty() && !esBillete) return ResultadoBillete(null, false, false)

        // Pesos: un número grande vale mucho más que uno chico; una palabra impresa vale algo intermedio.
        val altoMax = max(1, lecturas.filter { it.esNumero }.maxOfOrNull { it.alto } ?: 1)
        val puntos = mutableMapOf<DenominacionBillete, Float>()
        for (l in lecturas) {
            val peso = if (l.esNumero) {
                if (l.alto <= 0) 1f else 1f + 3f * (l.alto.toFloat() / altoMax)
            } else {
                1.5f
            }
            puntos[l.denominacion] = (puntos[l.denominacion] ?: 0f) + peso
        }

        if (!esBillete) return ResultadoBillete(null, false, false) // hay un "100" pero nada dice que sea billete

        if (puntos.isEmpty()) {
            // Es un billete, pero el OCR no leyó la denominación. El color solo da una pista, nunca una certeza.
            return ResultadoBillete(null, true, true, sugerida = mejorPorColor(colores))
        }

        // Varios billetes: si dos o más denominaciones distintas tienen su número grande (y no es el mismo número
        // partido en dos), cada una es un billete. Esto va antes del criterio de abajo, que con dos billetes
        // distintos siempre diría "no estoy segura".
        val varios = denominacionesPresentes(lecturas, puntos, altoMax)
        if (varios.size >= 2) {
            return ResultadoBillete(
                varios.first(), true, false, adicionales = varios.drop(1),
                cantidades = varios.associateWith { contarBilletes(lecturas, it) },
            )
        }

        // El color desempata y apoya, pero por sí solo nunca cambia una lectura clara del número.
        val totales = puntos.mapValues { (d, p) -> p + 1.5f * (colores[d] ?: 0f) }
        val ordenados = totales.entries.sortedByDescending { it.value }
        val primero = ordenados[0]
        val segundo = ordenados.getOrNull(1)?.value ?: 0f

        // Hace falta ventaja clara sobre la segunda lectura; si no, es mejor decir "no estoy segura".
        return if (segundo == 0f || primero.value >= segundo * 1.6f) {
            ResultadoBillete(
                primero.key, true, false,
                cantidades = mapOf(primero.key to contarBilletes(lecturas, primero.key)),
            )
        } else {
            ResultadoBillete(null, true, true, sugerida = if (primero.value > segundo) primero.key else null)
        }
    }

    /**
     * Qué denominación sugiere el color dominante de la foto, o null si no hay uno claro.
     * Los colores salen del billete de cada denominación (azul, rosa, rojo, verde, café, morado).
     */
    fun colorDominante(pixeles: IntArray): Map<DenominacionBillete, Float> {
        val cuenta = FloatArray(6)
        var total = 0
        for (px in pixeles) {
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val delta = mx - mn
            if (mx < 0.25f || delta / mx < 0.25f) continue // oscuro, blanco o gris: no dice nada del color
            val matiz = when (mx) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }.let { if (it < 0f) it + 360f else it }
            val clase = when {
                matiz < 12f || matiz >= 345f -> 0 // rojo -> 100
                matiz < 45f -> 1 // café/naranja -> 500
                matiz in 70f..170f -> 2 // verde -> 200
                matiz in 170f..255f -> 3 // azul -> 20
                matiz in 255f..310f -> 4 // morado -> 1000
                matiz in 310f..345f -> 5 // rosa -> 50
                else -> continue // amarillo y verde-amarillo: piel, mesa, luz cálida
            }
            cuenta[clase]++
            total++
        }
        if (total < max(20, pixeles.size / 20)) return emptyMap() // casi no hay color: no se opina
        val destino = arrayOf(
            DenominacionBillete.D100, DenominacionBillete.D500, DenominacionBillete.D200,
            DenominacionBillete.D20, DenominacionBillete.D1000, DenominacionBillete.D50,
        )
        return destino.indices.associate { destino[it] to cuenta[it] / total }
    }

    private fun mejorPorColor(colores: Map<DenominacionBillete, Float>): DenominacionBillete? {
        val orden = colores.entries.sortedByDescending { it.value }
        val top = orden.firstOrNull() ?: return null
        val segundo = orden.getOrNull(1)?.value ?: 0f
        return if (top.value >= 0.55f && top.value >= segundo * 2f) top.key else null
    }

    private data class Lectura(
        val denominacion: DenominacionBillete,
        val alto: Int,
        val esNumero: Boolean,
        val x: Int = -1,
        val y: Int = -1,
    )

    // Un número cuenta como "grande" (el de la denominación, no serie ni microtexto) si mide al menos esta
    // fracción del número más grande de la foto.
    private const val FRACCION_NUMERO_GRANDE = 0.6f

    // Dos lecturas más cerca que esto (en alturas de letra) se toman como el mismo número partido por el OCR,
    // por ejemplo "500" leído como "50" + "0".
    private const val DISTANCIA_MISMO_NUMERO = 2.5f

    /**
     * Las denominaciones distintas que tienen un número grande en la foto, de la de más evidencia a la de menos.
     * Devuelve lista vacía si no hay tamaños (versión de solo texto) o si en realidad solo hay una.
     * No cuenta billetes repetidos: dos billetes de 100 salen como una sola denominación.
     */
    private fun denominacionesPresentes(
        lecturas: List<Lectura>,
        puntos: Map<DenominacionBillete, Float>,
        altoMax: Int,
    ): List<DenominacionBillete> {
        if (lecturas.none { it.esNumero && it.alto > 0 }) return emptyList()
        val umbral = FRACCION_NUMERO_GRANDE * altoMax

        // El número más grande de cada denominación, solo entre los grandes.
        val principales = lecturas
            .filter { it.esNumero && it.alto >= umbral }
            .groupBy { it.denominacion }
            .mapValues { (_, grupo) -> grupo.reduce { a, b -> if (b.alto > a.alto) b else a } }
        if (principales.size < 2) return emptyList()

        // De más a menos evidencia; una lectura pegada a otra más fuerte es el mismo número mal partido.
        val elegidas = mutableListOf<Map.Entry<DenominacionBillete, Lectura>>()
        for (candidata in principales.entries.sortedByDescending { puntos[it.key] ?: 0f }) {
            if (elegidas.none { mismoNumero(candidata.value, it.value) }) elegidas += candidata
        }
        return if (elegidas.size >= 2) elegidas.map { it.key } else emptyList()
    }

    // Todos los números de una misma denominación que estén a menos de esta distancia (en alturas del número más
    // grande) se toman como del mismo billete: el de una esquina y el de la esquina contraria están a unas 12
    // alturas. Por eso dos billetes iguales muy pegados se cuentan como uno; hace falta algo de separación.
    private const val RADIO_MISMO_BILLETE = 14f

    /**
     * Cuántos billetes de [d] hay, de forma aproximada. Cada billete trae su número en varias esquinas, así que se
     * agrupan los números grandes que están cerca entre sí; cada grupo es un billete. Sin posiciones en la foto,
     * o con una sola lectura, es 1. Es una estimación: billetes pegados, encimados o doblados se cuentan de menos.
     */
    private fun contarBilletes(lecturas: List<Lectura>, d: DenominacionBillete): Int {
        val propias = lecturas.filter { it.esNumero && it.denominacion == d && it.alto > 0 && it.x >= 0 && it.y >= 0 }
        if (propias.size < 2) return 1
        val altoMax = propias.maxOf { it.alto }
        // Solo números grandes (no serie ni microtexto) y sin contar dos veces un número que el OCR partió en dos.
        val grandes = propias.filter { it.alto >= FRACCION_NUMERO_GRANDE * altoMax }
        val unicos = mutableListOf<Lectura>()
        for (l in grandes.sortedByDescending { it.alto }) if (unicos.none { mismoNumero(l, it) }) unicos += l
        val limite = RADIO_MISMO_BILLETE * altoMax
        val grupos = mutableListOf<Lectura>() // el primer número de cada billete
        for (l in unicos.sortedWith(compareBy({ it.y }, { it.x }))) {
            val cerca = grupos.any { g ->
                val dx = (l.x - g.x).toFloat()
                val dy = (l.y - g.y).toFloat()
                dx * dx + dy * dy < limite * limite
            }
            if (!cerca) grupos += l
        }
        return max(1, grupos.size)
    }

    /** Sin posición no se puede saber, y se asume que son números distintos. */
    private fun mismoNumero(a: Lectura, b: Lectura): Boolean {
        if (a.x < 0 || a.y < 0 || b.x < 0 || b.y < 0) return false
        val dx = (a.x - b.x).toFloat()
        val dy = (a.y - b.y).toFloat()
        val limite = DISTANCIA_MISMO_NUMERO * max(a.alto, b.alto)
        return dx * dx + dy * dy < limite * limite
    }

    /** Mayúsculas, sin acentos, y sin signos de pesos o puntuación en los bordes. */
    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase()
            .trim { !it.isLetterOrDigit() && it != '|' }

    /**
     * Si la palabra es una denominación ("100", "1,000", "$50", "5O", "1OO"), cuál. Las letras que el OCR
     * confunde con números solo se corrigen dentro de palabras cortas que ya tienen algún dígito de verdad:
     * así "PESOS" nunca se convierte en "PE505".
     */
    private fun numeroDe(token: String): DenominacionBillete? {
        val t = token.replace(",", "").replace(".", "").replace("$", "")
        if (t.length !in 2..4) return null
        if (t.none { it.isDigit() }) return null
        if (!t.all { it.isDigit() || it in CONFUSIONES }) return null
        val digitos = t.map { if (it.isDigit()) it else CONFUSIONES.getValue(it) }.joinToString("")
        return VALIDAS[digitos.toIntOrNull() ?: return null]
    }

    /** Si la palabra es "VEINTE", "CIEN", "MIL"... aceptando una letra mal leída en las palabras largas. */
    private fun palabraDe(token: String): DenominacionBillete? {
        PALABRAS[token]?.let { return it }
        if (token.length < 6) return null
        return PALABRAS.entries.firstOrNull { (palabra, _) ->
            palabra.length >= 6 && abs(palabra.length - token.length) <= 1 && distancia(palabra, token) <= 1
        }?.value
    }

    private fun distancia(a: String, b: String): Int {
        val previa = IntArray(b.length + 1) { it }
        val actual = IntArray(b.length + 1)
        for (i in 1..a.length) {
            actual[0] = i
            for (j in 1..b.length) {
                val costo = if (a[i - 1] == b[j - 1]) 0 else 1
                actual[j] = min(min(actual[j - 1] + 1, previa[j] + 1), previa[j - 1] + costo)
            }
            actual.copyInto(previa)
        }
        return previa[b.length]
    }
}

package propuesta

/** Propuesta: mismas reglas que VoiceText.matches, pero el texto se parte una sola vez por frase dicha. */
class Prepared(val text: String) {
    val words: List<String> = text.split(" ").filter { it.isNotEmpty() }
    val padded = " $text "
}

private val REAL_WORDS = setOf(
    "barra", "borro", "borre", "historia", "termino", "terminal", "describo", "vibro", "vaca", "pasa",
    "chato", "coche", "olvido", "escribo", "escribir", "hacia", "estoy", "estas", "tengo",
)

fun hasAny(p: Prepared, phrases: Collection<String>): Boolean = phrases.any { matches(p, it) }

fun matches(p: Prepared, phrase: String): Boolean {
    val target = phrase.trim()
    if (target.isEmpty()) return false
    val textWords = p.words
    if (target.endsWith("*")) {
        val stem = target.removeSuffix("*")
        return stem.isNotEmpty() && textWords.any { it.startsWith(stem) }
    }
    val targetWords = target.split(" ").filter { it.isNotEmpty() }
    if (targetWords.size > textWords.size + 1) return false
    if (p.padded.contains(" $target ")) return true
    if (targetWords.size == 1 && target.length >= 6) {
        for (i in 0 until textWords.size - 1) {
            val a = textWords[i]; val b = textWords[i + 1]
            if (a.length + b.length == target.length && target.startsWith(a) && target.endsWith(b)) return true
        }
    }
    if (targetWords.size > textWords.size) return false
    for (start in 0..textWords.size - targetWords.size) {
        var ok = true
        for (i in targetWords.indices) if (!closeEnough(textWords[start + i], targetWords[i])) { ok = false; break }
        if (ok) return true
    }
    return false
}

private fun closeEnough(word: String, target: String): Boolean {
    if (word == target) return true
    if (target.length < 5 || word in REAL_WORDS) return false
    if (word.first() != target.first()) return false
    val allowed = if (target.length >= 9) 2 else 1
    return kotlin.math.abs(word.length - target.length) <= allowed && distance(word, target) <= allowed
}

private fun distance(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
        }
        val t = previous; previous = current; current = t
    }
    return previous[b.length]
}

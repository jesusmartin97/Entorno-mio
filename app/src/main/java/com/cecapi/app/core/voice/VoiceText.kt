package com.cecapi.app.core.voice

import java.text.Normalizer

/** Helpers for comparing what the user said, ignoring case, accents and punctuation. */
object VoiceText {

    /**
     * Lowercases and strips accents/punctuation one character at a time, so every index in the
     * result still lines up with the same index in [text].
     */
    fun fold(text: String): String = buildString(text.length) {
        for (ch in text) {
            if (ch.code < 128) {
                append(if (ch.isLetterOrDigit()) ch.lowercaseChar() else ' ')
                continue
            }
            val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull { it.isLetterOrDigit() }
            append(base?.lowercaseChar() ?: ' ')
        }
    }

    /**
     * Cleans text that came from outside (an AI answer, a scanned document) before it is read aloud.
     * A speech engine reads formatting literally ("asterisco asterisco importante asterisco asterisco"),
     * and emojis as their names, so both are dropped and bullets become plain sentences.
     */
    fun forSpeech(text: String): String = text
        .replace(MARKDOWN_LINK, "$1")
        .replace(LIST_BULLET, "")
        .replace(MARKDOWN_MARKS, " ")
        .replace(EMOJI, "")
        .replace(SPACES, " ")
        .trim()

    private val MARKDOWN_LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)") // [texto](enlace) -> texto
    private val LIST_BULLET = Regex("^\\s*[-•*]\\s+", RegexOption.MULTILINE)
    private val MARKDOWN_MARKS = Regex("[*_`#>~]+")
    private val EMOJI = Regex("[\\p{So}\\p{Cs}]")
    private val SPACES = Regex("\\s+")

    private val YES_WORDS = setOf(
        "si", "claro", "dale", "ok", "okey", "supuesto", "afirmativo", "correcto", "seguro", "hazlo", "confirmo",
        "confirmar", "adelante", "exacto", "listo", "simon", "sale", "orale", "andale", "va", "sip", "aja",
    )
    private val NO_WORDS = setOf("no", "nunca", "negativo", "olvidalo", "dejalo", "cancela", "cancelar", "nel", "nop", "nope")

    /** A whole-word "no" (or a synonym) in [spoken]. It wins over a "si" in the same phrase: "no, sí, mejor no". */
    fun isNo(spoken: String): Boolean = normalize(spoken).split(" ").any { it in NO_WORDS }

    /** A whole-word "sí" (or a synonym) in [spoken], and no "no" with it. */
    fun isYes(spoken: String): Boolean = !isNo(spoken) && normalize(spoken).split(" ").any { it in YES_WORDS }

    /** [fold] with repeated spaces collapsed: "¡Ingresa, el usuario!" -> "ingresa el usuario". */
    fun normalize(text: String): String = fold(text).trim().replace(SPACES, " ")

    /**
     * True when the normalized [text] contains any of the normalized [phrases]. A phrase matches only as whole
     * words ("menudo" is not "menu", "contexto" is not "texto"), and forgives what a recognizer gets wrong: a
     * letter off in a long word ("isquierda") or a word split in two ("en frente"). A phrase ending in `*` is a
     * stem that matches any word starting with it ("activ*" for "activa" and "activar"). Words that are real
     * words in their own right ([REAL_WORDS]) are never "corrected" into a command.
     */
    fun hasAny(text: String, vararg phrases: String): Boolean = phrases.any { matches(text, it) }

    fun hasAny(text: String, phrases: Collection<String>): Boolean = phrases.any { matches(text, it) }

    fun matches(text: String, phrase: String): Boolean {
        val target = phrase.trim()
        if (target.isEmpty()) return false
        val textWords = text.split(" ").filter { it.isNotEmpty() }
        if (target.endsWith("*")) {
            val stem = target.removeSuffix("*")
            return stem.isNotEmpty() && textWords.any { it.startsWith(stem) }
        }
        val targetWords = target.split(" ").filter { it.isNotEmpty() }
        if (targetWords.size > textWords.size + 1) return false
        if (" $text ".contains(" $target ")) return true
        // One long word said as two neighbouring words ("en frente" for "enfrente"), never across the whole text.
        if (targetWords.size == 1 && target.length >= 6) {
            for (i in 0 until textWords.size - 1) if (textWords[i] + textWords[i + 1] == target) return true
        }
        if (targetWords.size > textWords.size) return false
        for (start in 0..textWords.size - targetWords.size) {
            if (targetWords.indices.all { i -> closeEnough(textWords[start + i], targetWords[i]) }) return true
        }
        return false
    }

    /** Real words that are one letter away from a command. They only count when said exactly. */
    private val REAL_WORDS = setOf(
        "barra", "borro", "borre", "historia", "termino", "terminal", "describo", "vibro", "vaca", "pasa",
        "chato", "coche", "olvido", "escribo", "escribir", "hacia", "estoy", "estas", "tengo",
    )

    private fun closeEnough(word: String, target: String): Boolean {
        if (word == target) return true
        if (target.length < 5 || word in REAL_WORDS) return false
        if (word.first() != target.first()) return false
        val allowed = if (target.length >= 9) 2 else 1
        return kotlin.math.abs(word.length - target.length) <= allowed && distance(word, target) <= allowed
    }

    /** Edit distance: how many letters must change, be added or removed to turn [a] into [b]. */
    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }
}

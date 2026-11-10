import com.cecapi.app.core.voice.VoiceText
import java.io.File

fun main(args: Array<String>) {
    val phrases = File(args[0]).readLines(Charsets.UTF_8).filter { it.isNotBlank() }
    // 1) Equivalencia: mismas respuestas en todas las frases x muchas entradas
    val corpus = File(args[1]).readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { VoiceText.normalize(it) }
    var checks = 0; var diffs = 0
    for (t in corpus) { val p = propuesta.Prepared(t); for (ph in phrases) { checks++; if (VoiceText.matches(t, ph) != propuesta.matches(p, ph)) { diffs++; if (diffs < 5) println("DIF: '$t' vs '$ph'") } } }
    println("equivalencia: $checks comparaciones, $diffs diferencias")
    val base = "oye me podrias decir por favor que hay enfrente de mi porque no alcanzo a ver bien la mesa "
    val inputs = linkedMapOf(
        "corta (3 palabras)" to "isquierda por favor",
        "normal (12 palabras)" to "oye me podrias decir por favor que hay en frente de mi",
        "larga (100 caracteres)" to base.take(100),
        "muy larga (500 caracteres)" to base.repeat(6).take(500),
    )
    fun med(block: () -> Unit): Pair<Double, Double> {
        repeat(3_000) { block() }
        val s = LongArray(3_000) { val t0 = System.nanoTime(); block(); System.nanoTime() - t0 }
        s.sort(); return s[1500] / 1000.0 to s[2970] / 1000.0
    }
    println("%-28s %22s %22s".format("entrada", "actual med/p99 (us)", "propuesta med/p99 (us)"))
    for ((name, raw) in inputs) {
        val a = med { val t = VoiceText.normalize(raw); phrases.count { VoiceText.matches(t, it) } }
        val b = med { val p = propuesta.Prepared(VoiceText.normalize(raw)); phrases.count { propuesta.matches(p, it) } }
        println("%-28s %10.1f / %9.1f %10.1f / %9.1f".format(name, a.first, a.second, b.first, b.second))
    }
}

package com.cecapi.app.core.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "what is my phone doing" in words a screen-reader-free user can hear: battery, Wi-Fi or
 * mobile data and signal, time and date. Read-only; it never changes any setting.
 */
@Singleton
class PhoneStatusReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** The spoken answer if [spoken] asks about the phone's state, or null if it is about something else. */
    fun answer(spoken: String): String? {
        val text = VoiceText.normalize(spoken)
        val significant = text.split(" ").filter { it.isNotEmpty() && it !in FILLER }
        // Only a short question is about the phone. A longer sentence that merely mentions "hora" or "internet" is
        // an answer to a form or a question for the AI, and must reach the screen instead.
        if (significant.size > MAX_TOPIC_WORDS) return null
        // The date words are common inside an ordinary sentence ("el 5 de este mes", "el año pasado"), so they
        // only answer when the question is essentially bare: the trigger word and, at most, one filler-adjacent word.
        val bareDateQuestion = significant.size <= 1
        return when {
            listOf("estado del telefono", "estado del celular", "estado de la pantalla", "estado del dispositivo",
                "como esta mi telefono", "como esta mi celular").let { phrases -> VoiceText.hasAny(text, phrases) } -> fullReport()
            listOf("bateria", "pila", "cuanta carga").let { phrases -> VoiceText.hasAny(text, phrases) } -> battery()
            listOf("wifi", "wi fi", "internet", "senal", "cobertura", "datos moviles", "conexion").let { phrases -> VoiceText.hasAny(text, phrases) } -> network()
            bareDateQuestion && HOUR.containsMatchIn(text) -> time()
            bareDateQuestion && YEAR.containsMatchIn(text) -> year()
            bareDateQuestion && MONTH.containsMatchIn(text) -> month()
            listOf("que dia", "dia es", "dia de hoy").let { phrases -> VoiceText.hasAny(text, phrases) } -> today()
            bareDateQuestion && VoiceText.hasAny(text, "fecha") -> date()
            else -> null
        }
    }

    fun fullReport(): String = "${time()} ${date()} ${battery()} ${network()}"

    /** The short version for the home-screen widget: time, battery and connection. */
    fun shortReport(): String = "${time()} ${battery()} ${network()}"

    fun battery(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "No pude leer la batería."
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return "No pude leer la batería."
        val percent = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return buildString {
            append("Batería al $percent por ciento")
            append(if (charging) ", cargando." else ".")
            if (!charging && percent <= 20) append(" La batería está baja, conviene conectar el cargador.")
        }
    }

    fun network(): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return "Sin conexión. No tienes Wi-Fi ni datos móviles."
        val signal = signalWord(capabilities)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                "Conectado a Wi-Fi$signal." + if (hasInternet) "" else " Pero sin acceso a internet."
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                "Usando datos móviles$signal." + if (hasInternet) "" else " Pero sin acceso a internet."
            else -> "Conectado a una red."
        }
    }

    fun time(): String {
        val now = LocalDateTime.now()
        val hour12 = now.hour % 12
        val hour = if (hour12 == 0) 12 else hour12
        val lead = if (hour == 1) "Es la una" else "Son las $hour"
        val minutes = if (now.minute == 0) " en punto" else " con ${now.minute}"
        val period = when (now.hour) {
            in 0..5 -> "de la madrugada"
            in 6..11 -> "de la mañana"
            12 -> "del día"
            in 13..19 -> "de la tarde"
            else -> "de la noche"
        }
        return "$lead$minutes $period."
    }

    /** Full date with the year: "Hoy es viernes 25 de septiembre de 2026." */
    fun date(): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", LOCALE)
        return "Hoy es ${LocalDateTime.now().format(formatter)}."
    }

    /** "Qué día es hoy": weekday and day of the month, without the year. */
    fun today(): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", LOCALE)
        return "Hoy es ${LocalDateTime.now().format(formatter)}."
    }

    fun year(): String = "Estamos en el año ${LocalDateTime.now().year}."

    /** The month by name and by number: "Estamos en septiembre, el mes número 9." */
    fun month(): String {
        val now = LocalDateTime.now()
        val name = now.format(DateTimeFormatter.ofPattern("MMMM", LOCALE))
        return "Estamos en $name, el mes número ${now.monthValue}."
    }

    private companion object {
        val LOCALE: Locale = Locale("es", "MX")
        /**
         * Words that carry no topic. A question about the phone is short once these are set aside ("qué hora es"),
         * while "a qué hora abre el banco" or "el 5 de este mes" has more to it and belongs to the screen or the AI.
         */
        val FILLER = setOf(
            "que", "cual", "cuanto", "cuanta", "como", "esta", "estoy", "es", "el", "la", "los", "las", "de", "del", "en",
            "a", "un", "una", "y", "me", "mi", "dime", "dice", "dices", "puedes", "decir", "decirme", "por", "favor", "porfa",
            "oye", "hola", "asistente", "hoy", "ahora", "ahorita", "actual", "actualmente", "tengo", "hay", "queda", "este",
            "estamos", "tiene", "tienes", "le",
        )
        const val MAX_TOPIC_WORDS = 2

        val HOUR = Regex("\\bhora\\b")
        val YEAR = Regex("\\bano\\b")
        val MONTH = Regex("\\bmes\\b")
    }

    /** ", señal buena" when the system reports a level; empty when it does not (older Android, or no value). */
    private fun signalWord(capabilities: NetworkCapabilities): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""
        val dbm = capabilities.signalStrength
        if (dbm == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) return ""
        val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val level = if (wifi) {
            when {
                dbm >= -55 -> "excelente"
                dbm >= -67 -> "buena"
                dbm >= -78 -> "regular"
                else -> "débil"
            }
        } else {
            when {
                dbm >= -85 -> "excelente"
                dbm >= -100 -> "buena"
                dbm >= -110 -> "regular"
                else -> "débil"
            }
        }
        return ", señal $level"
    }
}

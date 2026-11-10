package com.cecapi.app.core.util

import com.cecapi.app.core.voice.VoiceText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** One structured fact found in what the person said: about whom/what, which property, and how to say it. */
data class WikidataFact(val subject: String, val property: String, val sentence: (value: String) -> String)

/**
 * A handful of specific, structured facts — when someone was born or died, a country's capital, a place's
 * population — that Wikidata answers more reliably than hunting for the number inside Wikipedia's prose
 * summary ([WikipediaLookup]). Same family of free Wikimedia APIs, no key, same User-Agent etiquette.
 * Only covers these few common questions, not a general Wikidata search — a much bigger, messier problem
 * (entity disambiguation, hundreds of possible properties) that a voice answer cannot ask the person to
 * narrow down the way a results list could.
 */
@Singleton
class WikidataLookup @Inject constructor() {

    /** Which fact (if any) [text] is asking for, and about whom/what; null if it does not match one. */
    fun matchFact(text: String): WikidataFact? {
        val folded = VoiceText.fold(text)
        for ((regex, property, sentence) in PATTERNS) {
            val match = regex.find(folded) ?: continue
            val subject = text.substring(match.groups[1]!!.range.first).trim().takeIf { it.length >= 2 } ?: continue
            return WikidataFact(subject, property, sentence)
        }
        return null
    }

    /** The spoken answer for [fact], or null if Wikidata has no matching entity or no value for that property. */
    suspend fun answer(fact: WikidataFact): String? = withContext(Dispatchers.IO) {
        try {
            val entityId = findEntity(fact.subject) ?: return@withContext null
            val claims = getClaims(entityId, fact.property) ?: return@withContext null
            val mainsnak = claims.optJSONArray(fact.property)?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?: return@withContext null
            val datavalue = mainsnak.optJSONObject("datavalue") ?: return@withContext null
            val value = formatValue(datavalue) ?: return@withContext null
            fact.sentence(value)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatValue(datavalue: JSONObject): String? = when (datavalue.optString("type")) {
        "time" -> datavalue.optJSONObject("value")?.let { formatTime(it) }
        "quantity" -> datavalue.optJSONObject("value")?.let { formatQuantity(it) }
        // A property whose value is itself another entity (e.g. a country's capital): resolve its label.
        "wikibase-entityid" -> datavalue.optJSONObject("value")?.optString("id")?.takeIf { it.isNotBlank() }?.let { getLabel(it) }
        else -> null
    }

    /** Wikidata time: "+1810-09-16T00:00:00Z" plus a precision (9 = year, 10 = month, 11 = day known). */
    private fun formatTime(value: JSONObject): String? {
        val raw = value.optString("time").removePrefix("+")
        val precision = value.optInt("precision", 11)
        val parts = raw.substringBefore("T").split("-")
        if (parts.size < 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull()?.let { MESES.getOrNull(it - 1) }
        val day = parts[2].toIntOrNull()?.takeIf { it > 0 }
        return when {
            precision < 10 || month == null -> "$year"
            precision < 11 || day == null -> "$month de $year"
            else -> "$day de $month de $year"
        }
    }

    private fun formatQuantity(value: JSONObject): String? {
        val amount = value.optString("amount").removePrefix("+").toDoubleOrNull() ?: return null
        // Agrupa por miles a la manera en español (9.209.944), sin depender del locale del teléfono.
        return amount.toLong().toString().reversed().chunked(3).joinToString(".").reversed()
    }

    private fun findEntity(query: String): String? {
        val url = "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=es&type=item&limit=1&search=" +
            URLEncoder.encode(query, "UTF-8")
        val body = get(url) ?: return null
        return JSONObject(body).optJSONArray("search")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    private fun getClaims(entityId: String, property: String): JSONObject? {
        val url = "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&entity=$entityId&property=$property"
        return get(url)?.let { JSONObject(it).optJSONObject("claims") }
    }

    private fun getLabel(entityId: String): String? {
        val url = "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&ids=$entityId&languages=es&props=labels"
        val body = get(url) ?: return null
        return JSONObject(body).optJSONObject("entities")?.optJSONObject(entityId)
            ?.optJSONObject("labels")?.optJSONObject("es")?.optString("value")?.takeIf { it.isNotBlank() }
    }

    private fun get(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "CECAPI-App/1.0 (accesibilidad; proyecto de estadía)")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val MESES = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )

        /** (patrón del pedido, propiedad de Wikidata, cómo convertir el valor ya encontrado en una frase). */
        val PATTERNS: List<Triple<Regex, String, (String) -> String>> = listOf(
            Triple(Regex("(?:cuando nacio|fecha de nacimiento de)\\s+(.+)"), "P569") { "Nació el $it." },
            Triple(
                Regex("(?:cuando murio|cuando fallecio|fecha de (?:muerte|fallecimiento) de)\\s+(.+)"),
                "P570",
            ) { "Murió el $it." },
            Triple(Regex("capital de\\s+(.+)"), "P36") { "La capital es $it." },
            Triple(
                Regex("(?:poblacion de|habitantes de|cuantos habitantes tiene)\\s+(.+)"),
                "P1082",
            ) { "Tiene aproximadamente $it habitantes." },
        )
    }
}

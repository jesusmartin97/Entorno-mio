package com.cecapi.app.core.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** A short summary of a topic and, if the person asks for more, the rest of it. */
data class WikipediaResult(val short: String, val rest: String?, val title: String)

/**
 * A second source of answers when there is internet but the AI is off (its providers do not allow minors,
 * so it stays off until Pp decides otherwise): Wikipedia in Spanish, which needs no key and no account.
 * The full-text search endpoint (`action=query&list=search`) finds the closest article for what the person
 * said, then the REST summary endpoint returns its first paragraph.
 *
 * Earlier this used `action=opensearch`, which only matches the START of a title. Verified against the real
 * API: "el día de la independencia" (how someone actually says it, with "el" in front) matched a wrong,
 * unrelated article with opensearch, and "el humano" matched nothing at all — a spoken question almost
 * never says a bare article title. Full-text search matches content, not just the title's first letters, so
 * it finds a relevant article for both instead of a wrong one or nothing.
 */
@Singleton
class WikipediaLookup @Inject constructor() {

    suspend fun search(query: String): WikipediaResult? = withContext(Dispatchers.IO) {
        try {
            // Tries each candidate title in order — not just the top one — because the best text match is
            // sometimes a disambiguation page ("¿cuál de estos?"), which fetchSummary skips. Giving up right
            // there threw away a perfectly good second or third result for no reason.
            for (title in findTitles(query)) {
                val extract = fetchSummary(title) ?: continue
                val cut = cutAtSentence(extract, SHORT_ANSWER_CHARS)
                return@withContext WikipediaResult(
                    short = cut,
                    rest = extract.substring(cut.length).trim().ifBlank { null },
                    title = title,
                )
            }
            null
        } catch (e: Exception) {
            // Not asserted on since this already returns null to the person either way; this only exists so
            // the next time every query comes back empty, logcat says why instead of leaving us to guess
            // between "no network", "Wikipedia changed its response shape" and "genuinely no article".
            Log.w(TAG, "search(\"$query\") failed", e)
            null
        }
    }

    private fun findTitles(query: String): List<String> {
        val url = "https://es.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=$CANDIDATE_TITLES&srsearch=" +
            URLEncoder.encode(query, "UTF-8")
        val body = get(url) ?: return emptyList()
        val results = JSONObject(body).optJSONObject("query")?.optJSONArray("search")
        if (results == null) {
            Log.w(TAG, "findTitles(\"$query\"): unexpected response shape: $body")
            return emptyList()
        }
        if (results.length() == 0) {
            Log.d(TAG, "findTitles(\"$query\"): Wikipedia returned zero results")
            return emptyList()
        }
        return (0 until results.length()).mapNotNull { i ->
            results.optJSONObject(i)?.optString("title")?.takeIf { it.isNotBlank() }
        }
    }

    private fun fetchSummary(title: String): String? {
        val url = "https://es.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title, "UTF-8")
        val body = get(url) ?: return null
        val json = JSONObject(body)
        if (json.optString("type") == "disambiguation") return null // "¿cuál de estos?" needs a screen, not voice
        return json.optString("extract").trim().ifBlank { null }
    }

    private fun get(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            // Wikipedia's own etiquette (meta.wikimedia.org/wiki/User-Agent_policy) asks for a real identifier.
            setRequestProperty("User-Agent", "CECAPI-App/1.0 (accesibilidad; proyecto de estadía)")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "GET $urlString -> HTTP ${connection.responseCode}")
                return null
            }
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Cuts at the sentence end at or after [minChars], so the short answer never stops mid-thought. */
    private fun cutAtSentence(text: String, minChars: Int): String {
        if (text.length <= minChars) return text
        val end = text.indexOf(". ", minChars).let { if (it == -1) text.indexOf('.', minChars) else it }
        return if (end == -1) text else text.substring(0, end + 1)
    }

    private companion object {
        const val TAG = "WikipediaLookup"
        const val SHORT_ANSWER_CHARS = 220

        /** How many search results to try, in order, before giving up (not just the top one). */
        const val CANDIDATE_TITLES = 3
    }
}

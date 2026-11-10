package com.cecapi.app.feature.modulo3_asistenteinteligente

import com.cecapi.app.BuildConfig
import com.cecapi.app.core.voice.IntentFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** What `/entender` decided: a module to open ([moduleCode], a [com.cecapi.app.core.model.ModuloCecapi.storageCode]) or an [answer]. */
data class AiUnderstanding(
    val moduleCode: String? = null,
    val answer: String? = null,
)

interface AiAssistantApi {
    suspend fun ask(pregunta: String, contexto: List<String>): Result<String>

    /** Like [ask], but the backend first checks whether the phrase names a module to open. */
    suspend fun understand(pregunta: String, contexto: List<String>): Result<AiUnderstanding>
}

/**
 * Talks to a small backend YOU control — never directly to a third-party LLM
 * provider's API from the phone. Shipping any provider's API key inside an
 * Android APK means anyone who decompiles it gets the key — there is no
 * client-side secret storage that fixes this, so the request must be
 * relayed through a server this team (Módulo 3) owns and deploys.
 *
 * Recommended provider for that backend: **Gemini API** (Google AI Studio) —
 * it has a genuinely free tier with no time limit, which matters for a
 * student project with no budget. This is a backend-only choice: swapping to
 * another provider later never touches this file or any other Android code,
 * since the app only ever talks to your own endpoint below.
 *
 * Backend contract (see backend/README.md):
 *   POST {AI_PROXY_BASE_URL}   (ends in /preguntar)
 *   body:     {"pregunta": "...", "contexto": ["turno 1", "turno 2", ...]}
 *   response: {"respuesta": "..."}
 *   POST <same server>/entender   (same body)
 *   response: {"tipo": "comando", "comando": "<storageCode>"} or {"tipo": "respuesta", "respuesta": "..."}
 */
@Singleton
class ProxyAiAssistantApi @Inject constructor(
    private val intentFallback: IntentFallback,
) : AiAssistantApi {

    override suspend fun ask(pregunta: String, contexto: List<String>): Result<String> =
        post("/preguntar", pregunta, contexto).mapCatching { it.getString("respuesta") }

    override suspend fun understand(pregunta: String, contexto: List<String>): Result<AiUnderstanding> {
        val resultado = post("/entender", pregunta, contexto)
        // A backend from before /entender existed answers 404: ask it the plain way instead of failing.
        if ((resultado.exceptionOrNull() as? HttpError)?.code == HttpURLConnection.HTTP_NOT_FOUND) {
            return ask(pregunta, contexto).map { AiUnderstanding(answer = it) }
        }
        return resultado.mapCatching { json ->
            if (json.optString("tipo") == "comando") {
                AiUnderstanding(moduleCode = json.getString("comando"))
            } else {
                AiUnderstanding(answer = json.getString("respuesta"))
            }
        }
    }

    private class HttpError(val code: Int) : IllegalStateException("El asistente no respondió (código $code).")

    private suspend fun post(path: String, pregunta: String, contexto: List<String>): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            // The person turns this on in Configuración; off by default (AI providers do not allow minors).
            // Checked here, at the one place that actually reaches the network, so no caller can bypass it.
            if (!intentFallback.enabled) {
                return@withContext Result.failure(
                    IllegalStateException("La inteligencia artificial está desactivada. Actívala en Configuración."),
                )
            }
            try {
                // The setting may be the server ("http://IP:8000") or the old full URL (".../preguntar"): both work.
                val base = BuildConfig.AI_PROXY_BASE_URL.trimEnd('/').removeSuffix("/preguntar")
                val url = URL(base + path)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5_000
                    readTimeout = 12_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                val body = JSONObject().apply {
                    put("pregunta", pregunta)
                    put("contexto", JSONArray(contexto))
                }

                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }

                if (connection.responseCode !in 200..299) {
                    return@withContext Result.failure(HttpError(connection.responseCode))
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                Result.success(JSONObject(responseText))
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
}

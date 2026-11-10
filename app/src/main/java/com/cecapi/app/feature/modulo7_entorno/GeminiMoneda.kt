package com.cecapi.app.feature.modulo7_entorno

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.cecapi.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Reconoce monedas mexicanas con la API de Gemini (visión).
 *
 * Solo se llama cuando la persona dice "qué moneda es" o "guarda la moneda". En el resto de la
 * aplicación no se manda ninguna foto a internet, para no gastar tokens ni cuota.
 *
 * La foto se reduce a 768 px y se comprime en JPEG antes de enviarla: menos píxeles es menos
 * consumo de tokens y una respuesta más rápida.
 */
class GeminiMoneda {

    suspend fun reconocer(bitmap: Bitmap): ResultadoMoneda = withContext(Dispatchers.IO) {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            Log.w(TAG, "Falta GEMINI_API_KEY en local.properties o en el entorno.")
            return@withContext ResultadoMoneda(null, 0f, MotivoMoneda.SIN_CONEXION)
        }
        // No se manda otra foto si ya hay una petición en curso o si la anterior fue hace muy poco.
        if (!LimiteGemini.intentar()) {
            Log.d(TAG, "Petición omitida para no gastar tokens (otra en curso o muy seguida).")
            return@withContext ResultadoMoneda(null, 0f, MotivoMoneda.SIN_CONEXION)
        }
        try {
            interpretar(pedirAGemini(bitmap))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar a Gemini: ${e.message}")
            ResultadoMoneda(null, 0f, MotivoMoneda.SIN_CONEXION)
        } finally {
            LimiteGemini.terminar()
        }
    }

    private fun pedirAGemini(bitmap: Bitmap): String {
        val imagen = imagenJpeg(bitmap)
        val cuerpo = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().apply {
                            put(JSONObject().put("text", PROMPT))
                            put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", Base64.encodeToString(imagen, Base64.NO_WRAP))
                                    },
                                ),
                            )
                        },
                    ),
                ),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 120)
                    put("responseMimeType", "application/json")
                    put("responseSchema", ESQUEMA)
                },
            )
        }

        val url = URL("$BASE_URL/$MODELO:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
        val conexion = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
            val codigo = conexion.responseCode
            val respuesta = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (codigo !in 200..299) {
                throw IOException("HTTP $codigo: ${respuesta.take(300)}")
            }
            return JSONObject(respuesta)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .optString("text", "")
        } finally {
            conexion.disconnect()
        }
    }

    /** Convierte la respuesta JSON de Gemini en lo que la app ya sabe decir y contar. */
    private fun interpretar(texto: String): ResultadoMoneda {
        val limpio = texto.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = JSONObject(limpio)
        val denominacion = json.optString("denominacion", "ninguna").trim().lowercase()
        val confianza = json.optDouble("confianza", 0.0).toFloat().coerceIn(0f, 1f)

        return when (denominacion) {
            "varias" -> ResultadoMoneda(
                null,
                confianza,
                if (confianza >= MonedaMx.UMBRAL_CLASE_ESPECIAL) MotivoMoneda.VARIAS else MotivoMoneda.NO_CLARO,
            )
            "ninguna", "" -> ResultadoMoneda(
                null,
                confianza,
                if (confianza >= MonedaMx.UMBRAL_CLASE_ESPECIAL) MotivoMoneda.NO_HAY_MONEDA else MotivoMoneda.NO_CLARO,
            )
            else -> {
                val valor = denominacion.filter { it.isDigit() }.toIntOrNull()
                val moneda = DenominacionMoneda.values().firstOrNull { it.valor == valor }
                when {
                    moneda == null -> ResultadoMoneda(null, confianza, MotivoMoneda.NO_CLARO)
                    confianza >= UMBRAL_SEGURA_GEMINI -> ResultadoMoneda(moneda, confianza, MotivoMoneda.SEGURA)
                    confianza >= UMBRAL_DUDOSA_GEMINI -> ResultadoMoneda(moneda, confianza, MotivoMoneda.DUDOSA)
                    else -> ResultadoMoneda(null, confianza, MotivoMoneda.NO_CLARO)
                }
            }
        }
    }

    /** Reduce y comprime la foto antes de mandarla: menos tokens y menos datos. */
    private fun imagenJpeg(bitmap: Bitmap): ByteArray {
        val escala = minOf(1f, LADO_MAXIMO.toFloat() / maxOf(bitmap.width, bitmap.height))
        val reducida = if (escala < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * escala).roundToInt().coerceAtLeast(1),
                (bitmap.height * escala).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        return try {
            ByteArrayOutputStream().use { salida ->
                reducida.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
                salida.toByteArray()
            }
        } finally {
            if (reducida !== bitmap) reducida.recycle()
        }
    }

    private companion object {
        const val TAG = "GeminiMoneda"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MODELO = "gemini-3.5-flash-lite"
        const val LADO_MAXIMO = 768
        const val CALIDAD_JPEG = 80
        // Umbrales de Gemini, como antes: su confianza no se calibró con las fotos del modelo local.
        const val UMBRAL_SEGURA_GEMINI = 0.85f
        const val UMBRAL_DUDOSA_GEMINI = 0.60f

        val ESQUEMA = JSONObject(
            """
            {
              "type": "OBJECT",
              "properties": {
                "denominacion": {
                  "type": "STRING",
                  "enum": ["1", "2", "5", "10", "20", "varias", "ninguna"]
                },
                "confianza": { "type": "NUMBER" }
              },
              "required": ["denominacion", "confianza"]
            }
            """.trimIndent(),
        )

        val PROMPT = """
            Analiza la fotografía y reconoce la moneda mexicana que aparece.
            Denominaciones válidas: 1, 2, 5, 10 o 20 pesos.
            Si hay más de una moneda, responde "varias".
            Si no hay ninguna moneda, si es de otra denominación (por ejemplo 50 centavos),
            o si no puedes identificarla con seguridad, responde "ninguna".
            No inventes: si la imagen está borrosa, oscura o no se ve la moneda, responde "ninguna".
            La confianza debe ir de 0 a 1.
            Responde únicamente con el JSON solicitado.
        """.trimIndent()
    }
}

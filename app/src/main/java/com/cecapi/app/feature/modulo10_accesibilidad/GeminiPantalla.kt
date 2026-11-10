package com.cecapi.app.feature.modulo10_accesibilidad

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.cecapi.app.BuildConfig
import com.cecapi.app.feature.modulo7_entorno.LimiteGemini
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Por qué Gemini dio o no dio una descripción de las imágenes de la pantalla. */
enum class MotivoPantallaGemini {
    /** Devolvió al menos un objeto principal. */
    OK,

    /** Respondió, pero la pantalla no mostraba imágenes claras. */
    NO_CLARO,

    /** Sin internet, sin clave o error del servicio. */
    SIN_CONEXION,
}

/**
 * @property principal la imagen más importante de la pantalla, con artículo ("una playa").
 * @property secundarios hasta dos imágenes más ("un perro", "una computadora").
 */
data class ResultadoPantallaGemini(
    val principal: String?,
    val secundarios: List<String>,
    val motivo: MotivoPantallaGemini,
) {
    val hayObjetos: Boolean get() = motivo == MotivoPantallaGemini.OK && !principal.isNullOrBlank()
}

/**
 * Describe las imágenes o fotografías visibles en una captura de pantalla con la API de Gemini.
 *
 * Es el mismo criterio que [com.cecapi.app.feature.modulo7_entorno.GeminiEntorno] usa en la cámara:
 * un objeto principal y hasta dos secundarios. Aquí el prompt le pide ignorar el texto, los botones
 * y la interfaz, para que describa solo el contenido de las fotos o imágenes de la pantalla.
 *
 * Para no gastar tokens, la captura se reduce a 768 px, se comprime en JPEG al 80 % y la respuesta
 * se limita a 120 tokens. Si no hay internet o la API falla, el servicio sigue con ML Kit.
 */
class GeminiPantalla {

    suspend fun reconocer(bitmap: Bitmap): ResultadoPantallaGemini = withContext(Dispatchers.IO) {
        if (BuildConfig.GEMINI_PANTALLA_API_KEY.isBlank()) {
            Log.w(TAG, "Falta GEMINI_PANTALLA_API_KEY en local.properties o en el entorno.")
            return@withContext ResultadoPantallaGemini(null, emptyList(), MotivoPantallaGemini.SIN_CONEXION)
        }
        // No se manda otra captura si ya hay una petición en curso o si la anterior fue hace muy poco.
        if (!LimiteGemini.intentar()) {
            Log.d(TAG, "Petición omitida para no gastar tokens (otra en curso o muy seguida).")
            return@withContext ResultadoPantallaGemini(null, emptyList(), MotivoPantallaGemini.SIN_CONEXION)
        }
        try {
            interpretar(pedirAGemini(bitmap))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar a Gemini para la pantalla: ${e.message}")
            ResultadoPantallaGemini(null, emptyList(), MotivoPantallaGemini.SIN_CONEXION)
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

        val url = URL("$BASE_URL/$MODELO:generateContent?key=${BuildConfig.GEMINI_PANTALLA_API_KEY}")
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

    /** Convierte la respuesta JSON en el objeto principal y hasta dos secundarios. */
    private fun interpretar(texto: String): ResultadoPantallaGemini {
        val limpio = texto.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        Log.d(TAG, "Respuesta de Gemini: $limpio")
        val json = JSONObject(limpio)
        val principalCrudo = json.optString("principal", "").trim()
        val secundariosCrudos = json.optJSONArray("secundarios")?.let { arreglo ->
            (0 until arreglo.length()).mapNotNull { i ->
                arreglo.optString(i, "").trim().takeIf { it.isNotEmpty() }
            }
        }.orEmpty()

        val principal = principalCrudo.takeIf { it.isNotEmpty() }
            ?: return ResultadoPantallaGemini(null, emptyList(), MotivoPantallaGemini.NO_CLARO)
        val vistos = mutableSetOf(sinArticulo(principal).lowercase())
        val secundarios = secundariosCrudos
            .filter { vistos.add(sinArticulo(it).lowercase()) }
            .take(2)
        return ResultadoPantallaGemini(principal, secundarios, MotivoPantallaGemini.OK)
    }

    /** Reduce y comprime la captura antes de mandarla: menos tokens y menos datos. */
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

    companion object {
        private const val TAG = "GeminiPantalla"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODELO = "gemini-3.5-flash-lite"
        private const val LADO_MAXIMO = 768
        private const val CALIDAD_JPEG = 80

        private val ARTICULO = Regex("""^(un|una|unos|unas)\s+""", RegexOption.IGNORE_CASE)

        /** "una computadora" -> "computadora", para comparar y quitar duplicados. */
        fun sinArticulo(texto: String): String = texto.replace(ARTICULO, "").trim()

        /** Lo que se dice en voz alta: un principal y hasta dos secundarios. */
        fun frase(resultado: ResultadoPantallaGemini): String {
            val principal = resultado.principal ?: return ""
            return when (resultado.secundarios.size) {
                0 -> "Veo $principal."
                1 -> "Veo $principal. También veo ${resultado.secundarios[0]}."
                else -> "Veo $principal. También veo ${resultado.secundarios[0]} y ${resultado.secundarios[1]}."
            }
        }

        private val ESQUEMA = JSONObject(
            """
            {
              "type": "OBJECT",
              "properties": {
                "principal": { "type": "STRING" },
                "secundarios": {
                  "type": "ARRAY",
                  "items": { "type": "STRING" },
                  "maxItems": 2
                }
              },
              "required": ["principal", "secundarios"]
            }
            """.trimIndent(),
        )

        private val PROMPT = """
            Analiza esta captura de pantalla de un teléfono, para una persona ciega.
            Dime qué imágenes o fotografías se ven en la pantalla: por ejemplo, una foto en una
            galería, una imagen en una conversación, una publicación o un video.
            Ignora el texto, los botones, los menús, los íconos y el resto de la interfaz.
            Devuelve el contenido de la imagen más importante como objeto principal y hasta dos
            imágenes secundarias.
            Escribe cada uno en español de México, con su artículo indefinido: "una playa",
            "un perro", "una computadora", "una persona", "un paisaje".
            No repitas lo mismo ni uses sinónimos.
            No describas rasgos físicos de las personas.
            No inventes: si no se ve con claridad, no lo pongas.
            Si la pantalla no muestra ninguna imagen o foto clara, devuelve "principal": "" y
            "secundarios": [].
            Responde únicamente con el JSON solicitado.
        """.trimIndent()
    }
}

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

/** Por qué Gemini dio o no dio una descripción del entorno. */
enum class MotivoEntornoGemini {
    /** Devolvió al menos un objeto principal. */
    OK,

    /** Respondió, pero no vio objetos claros. */
    NO_CLARO,

    /** Sin internet, sin clave o error del servicio. */
    SIN_CONEXION,

    /** La persona apagó la inteligencia artificial en internet. */
    IA_DESHABILITADA,
}

/**
 * @property principal el objeto más importante, ya escrito con artículo ("una computadora").
 * @property secundarios hasta dos objetos más ("un monitor", "una libreta").
 */
data class ResultadoEntornoGemini(
    val principal: String?,
    val secundarios: List<String>,
    val motivo: MotivoEntornoGemini,
) {
    val hayObjetos: Boolean get() = motivo == MotivoEntornoGemini.OK && !principal.isNullOrBlank()
}

/**
 * Describe los objetos de una foto normal con la API de Gemini (visión).
 *
 * Solo se llama cuando la persona toma o elige una foto para "qué hay enfrente". Para no gastar
 * tokens, la imagen se reduce a 768 px, se comprime en JPEG al 80 % y la respuesta se limita a un
 * objeto principal y dos secundarios (máximo 120 tokens).
 *
 * Si no hay internet o la API falla, [EnvironmentRepository] sigue con el detector local.
 */
class GeminiEntorno {

    suspend fun reconocer(bitmap: Bitmap): ResultadoEntornoGemini = withContext(Dispatchers.IO) {
        if (BuildConfig.GEMINI_ENTORNO_API_KEY.isBlank()) {
            Log.w(TAG, "Falta GEMINI_ENTORNO_API_KEY en local.properties o en el entorno.")
            return@withContext ResultadoEntornoGemini(null, emptyList(), MotivoEntornoGemini.SIN_CONEXION)
        }
        // No se manda otra foto si ya hay una petición en curso o si la anterior fue hace muy poco.
        if (!LimiteGemini.intentar()) {
            Log.d(TAG, "Petición omitida para no gastar tokens (otra en curso o muy seguida).")
            return@withContext ResultadoEntornoGemini(null, emptyList(), MotivoEntornoGemini.SIN_CONEXION)
        }
        try {
            interpretar(pedirAGemini(bitmap))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar a Gemini para el entorno: ${e.message}")
            ResultadoEntornoGemini(null, emptyList(), MotivoEntornoGemini.SIN_CONEXION)
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

        val url = URL("$BASE_URL/$MODELO:generateContent?key=${BuildConfig.GEMINI_ENTORNO_API_KEY}")
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
    private fun interpretar(texto: String): ResultadoEntornoGemini {
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

        // Se descartan personas por privacidad, aunque el modelo no debería devolverlas.
        val objetos = (listOf(principalCrudo) + secundariosCrudos)
            .filter { it.isNotEmpty() && !esPersona(it) }

        val principal = objetos.firstOrNull()
            ?: return ResultadoEntornoGemini(null, emptyList(), MotivoEntornoGemini.NO_CLARO)
        val vistos = mutableSetOf(sinArticulo(principal).lowercase())
        val secundarios = objetos.drop(1)
            .filter { vistos.add(sinArticulo(it).lowercase()) }
            .take(2)
        return ResultadoEntornoGemini(principal, secundarios, MotivoEntornoGemini.OK)
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

    companion object {
        private const val TAG = "GeminiEntorno"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODELO = "gemini-3.5-flash-lite"
        private const val LADO_MAXIMO = 768
        private const val CALIDAD_JPEG = 80

        private val ARTICULO = Regex("""^(un|una|unos|unas)\s+""", RegexOption.IGNORE_CASE)
        private val PALABRAS_PERSONA = setOf(
            "persona", "personas", "gente", "hombre", "mujer", "niño", "niña", "bebé",
            "señor", "señora", "adulto", "adulta", "joven", "chico", "chica",
        )

        /** "una computadora" -> "computadora", para guardar la etiqueta sin artículo. */
        fun sinArticulo(texto: String): String = texto.replace(ARTICULO, "").trim()

        private fun esPersona(texto: String): Boolean {
            val palabras = texto.lowercase().split(Regex("""[^a-záéíóúüñ]+"""))
            return palabras.any { it in PALABRAS_PERSONA }
        }

        /** Lo que se dice en voz alta: un principal y hasta dos secundarios. */
        fun frase(resultado: ResultadoEntornoGemini): String {
            val principal = resultado.principal
                ?: return "No logré identificar objetos claros enfrente. Intenta centrar el objeto, " +
                    "acercarte un poco más o mejorar la iluminación."
            return when (resultado.secundarios.size) {
                0 -> "Veo $principal."
                1 -> "Veo $principal. También veo ${resultado.secundarios[0]}."
                else -> "Veo $principal. También veo ${resultado.secundarios[0]} y ${resultado.secundarios[1]}."
            }
        }

        /** Etiquetas para el historial, sin artículos ni duplicados. */
        fun etiquetas(resultado: ResultadoEntornoGemini): List<String> =
            (listOfNotNull(resultado.principal) + resultado.secundarios)
                .map(::sinArticulo)
                .filter { it.isNotEmpty() }

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
            Analiza la fotografía y dime qué objetos se ven, para una persona ciega.
            Devuelve un objeto principal (el más grande o importante) y hasta dos objetos secundarios.
            Escribe cada objeto en español de México, con su artículo indefinido: "una computadora",
            "un monitor", "una libreta", "un teclado", "una silla", "una botella".
            No repitas el mismo objeto ni sus sinónimos.
            No describas personas, caras, cuerpos, ropa ni características físicas. Si hay una persona,
            no la incluyas.
            No inventes: si un objeto no se ve con claridad, no lo pongas.
            Si no hay objetos claros, devuelve "principal": "" y "secundarios": [].
            Responde únicamente con el JSON solicitado.
        """.trimIndent()
    }
}

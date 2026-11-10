package com.cecapi.app.feature.modulo7_entorno

/**
 * Filtro del texto que se alcanza a leer en la zona del pecho. Solo deja pasar algo que parece un
 * nombre de persona; si no está claro, devuelve null. No usa red ni reconocimiento facial.
 */
object Gafete {

    const val MAX_LARGO = 25
    const val MAX_PALABRAS = 3

    private val PALABRA = Regex("^[A-ZÁÉÍÓÚÜÑ][a-záéíóúüñ]+$")

    private val PROHIBIDAS = setOf(
        "cecapi", "alumno", "alumna", "alumnos", "alumnas", "estudiante", "docente",
        "profesor", "profesora", "maestro", "maestra", "visitante", "personal",
        "gafete", "utd", "escuela", "prepa", "universidad", "grupo", "grado",
        "materia", "asistencia", "nombre",
    )

    /**
     * Primer texto que parece un nombre. Revisa línea por línea porque un gafete suele traer el
     * nombre separado del resto; si no, prueba el bloque completo.
     */
    fun elegirNombre(textos: List<String>): String? {
        for (bloque in textos) {
            for (linea in bloque.lines()) {
                if (esNombre(linea)) return normalizar(linea)
            }
            if (esNombre(bloque)) return normalizar(bloque)
        }
        return null
    }

    /** Un nombre corto, con mayúscula inicial y sin números ni palabras de escuela o grupo. */
    fun esNombre(texto: String): Boolean {
        val limpio = normalizar(texto)
        if (limpio.length < 2 || limpio.length > MAX_LARGO) return false
        if (limpio.any { it.isDigit() }) return false
        val palabras = limpio.split(" ")
        if (palabras.size > MAX_PALABRAS) return false
        palabras.forEach { palabra ->
            if (palabra.lowercase() in PROHIBIDAS) return false
            if (!PALABRA.matches(palabra)) return false
        }
        return true
    }

    private fun normalizar(texto: String): String =
        texto.trim()
            .trim { it.isWhitespace() || it in ".,;:·-–—\"'“”‘’()[]{}" }
            .replace(Regex("\\s+"), " ")
}

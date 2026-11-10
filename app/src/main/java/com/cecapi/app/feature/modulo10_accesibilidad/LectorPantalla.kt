package com.cecapi.app.feature.modulo10_accesibilidad

/**
 * Un elemento de la pantalla ya sin Android: solo lo que se puede leer de él.
 * Lo llena [LectorPantallaAndroid] a partir de un `AccessibilityNodeInfo`.
 */
data class NodoPantalla(
    val texto: String,
    /** Se puede activar (botón, enlace, casilla…). */
    val esBoton: Boolean = false,
    /** Encabezado o título de la pantalla. */
    val esEncabezado: Boolean = false,
    /** Está en pantalla pero apagado. */
    val deshabilitado: Boolean = false,
    /** Es un párrafo de texto, no una etiqueta corta ni un botón. */
    val esParrafo: Boolean = false,
)

/**
 * Arma lo que se dice en voz alta a partir de los elementos de la pantalla.
 *
 * Es código puro a propósito (no depende de Android) para poder probarlo con pruebas unitarias
 * normales. Quien lee de verdad la pantalla es [LectorPantallaAndroid].
 */
object LectorPantalla {

    /** Tope de elementos que se leen de una pantalla, para no marear (una lista larga es ilegible). */
    const val MAXIMO = 40

    /** Desde esta longitud un texto cuenta como largo y se lee antes que las etiquetas cortas. */
    const val LARGO_MINIMO = 60

    /** Lo que se dice cuando la pantalla no tiene nada con texto. */
    const val SIN_CONTENIDO = "No hay nada que leer en esta pantalla."

    /** El texto limpio de un elemento, o null si no aporta nada (vacío o solo espacios). */
    fun textoDe(nodo: NodoPantalla): String? = nodo.texto.trim().takeIf { it.isNotEmpty() }

    /** Frase de un elemento para leerlo al tocarlo. Añade "botón" para que se sepa que se puede activar. */
    fun fraseDe(nodo: NodoPantalla): String? {
        val base = textoDe(nodo) ?: return null
        return buildString {
            append(base)
            if (nodo.esBoton) append(", botón")
            if (nodo.deshabilitado) append(", desactivado")
        }
    }

    /**
     * Lee la pantalla completa, en el orden en que vienen los elementos: los junta en frases y evita
     * repetir el mismo texto seguido (pasa con etiquetas y su campo). Corta en [MAXIMO].
     */
    fun lectura(nodos: List<NodoPantalla>): String =
        unir(nodos).ifEmpty { SIN_CONTENIDO }

    /** True si el nodo es un párrafo o un texto largo: lo que más interesa al leer una pantalla. */
    fun esTextoPrincipal(nodo: NodoPantalla): Boolean {
        val texto = textoDe(nodo) ?: return false
        return nodo.esParrafo || texto.length >= LARGO_MINIMO
    }

    /**
     * Lectura para el gesto de volumen: recorre los nodos de arriba hacia abajo y prioriza párrafos y
     * textos largos. Si hay párrafos, incluye también los encabezados que los acompañan y deja fuera
     * las etiquetas cortas y los botones. Si no hay párrafos ni textos largos, lee todos los textos.
     *
     * Devuelve null si no hay nada que leer, para que el servicio no hable en una pantalla vacía.
     */
    fun lecturaDeTexto(nodos: List<NodoPantalla>): String? {
        val conTexto = nodos.mapNotNull { nodo ->
            textoDe(nodo)?.let { nodo.copy(texto = it) }
        }
        if (conTexto.isEmpty()) return null

        val hayPrincipales = conTexto.any(::esTextoPrincipal)
        val elegidos = if (hayPrincipales) {
            conTexto.filter { esTextoPrincipal(it) || it.esEncabezado }
        } else {
            conTexto
        }
        return unir(elegidos).takeIf { it.isNotEmpty() }
    }

    /** Junta los nodos en frases, sin repetir el mismo texto seguido y sin pasar de [MAXIMO]. */
    private fun unir(nodos: List<NodoPantalla>): String {
        val frases = mutableListOf<String>()
        for (nodo in nodos) {
            val frase = fraseDe(nodo) ?: continue
            if (frases.lastOrNull() == frase) continue
            frases += frase
            if (frases.size >= MAXIMO) break
        }
        if (frases.isEmpty()) return ""
        return frases.joinToString(". ", postfix = ".")
    }
}

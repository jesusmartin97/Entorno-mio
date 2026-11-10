package com.cecapi.app.feature.modulo10_accesibilidad

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Convierte la pantalla real de Android en [NodoPantalla] para que [LectorPantalla] arme la frase.
 *
 * Aquí solo se lee la pantalla (exploración táctil y "leer la pantalla"); **no** se toma ninguna
 * captura ni se manda nada a internet. Para poder leer, el servicio necesita
 * `canRetrieveWindowContent="true"`, que ya está en `res/xml/accessibility_service_config.xml`.
 */
object LectorPantallaAndroid {

    /** El texto de un solo elemento, listo para leer al explorarlo con el dedo. Null si no dice nada. */
    fun fraseDeNodo(nodo: AccessibilityNodeInfo?): String? =
        nodo?.let { LectorPantalla.fraseDe(aNodo(it)) }

    /** Los elementos con texto del árbol de [raiz], en orden de lectura. */
    fun recoger(raiz: AccessibilityNodeInfo?): List<NodoPantalla> {
        if (raiz == null) return emptyList()
        val salida = ArrayList<NodoPantalla>()
        recorrer(raiz, salida)
        return salida
    }

    /** La lectura completa de una pantalla: lo que responde la acción "leer la pantalla". */
    fun leer(raiz: AccessibilityNodeInfo?): String = LectorPantalla.lectura(recoger(raiz))

    /**
     * La lectura priorizada del gesto de volumen: párrafos y textos largos primero, de arriba hacia
     * abajo. Null si la pantalla no tiene texto, para no hablar de más.
     */
    fun leerTexto(raiz: AccessibilityNodeInfo?): String? =
        LectorPantalla.lecturaDeTexto(recoger(raiz))

    private fun recorrer(nodo: AccessibilityNodeInfo, salida: MutableList<NodoPantalla>) {
        if (salida.size >= LectorPantalla.MAXIMO) return
        val texto = textoDe(nodo)
        if (texto.isNotBlank()) salida += aNodo(nodo, texto)
        for (indice in 0 until nodo.childCount) {
            if (salida.size >= LectorPantalla.MAXIMO) return
            val hijo = nodo.getChild(indice) ?: continue
            recorrer(hijo, salida)
        }
    }

    private fun aNodo(nodo: AccessibilityNodeInfo, texto: String = textoDe(nodo)): NodoPantalla =
        NodoPantalla(
            texto = texto,
            esBoton = nodo.isClickable,
            esEncabezado = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) nodo.isHeading else false,
            deshabilitado = !nodo.isEnabled,
            esParrafo = esParrafo(nodo, texto),
        )

    /**
     * Un párrafo es texto que no se toca y que es largo o tiene varias frases. Los botones y los
     * encabezados no cuentan, para no confundir una acción con el contenido de la pantalla.
     */
    private fun esParrafo(nodo: AccessibilityNodeInfo, texto: String): Boolean {
        if (nodo.isClickable) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && nodo.isHeading) return false
        val variasFrases = texto.count { it == '.' || it == '!' || it == '?' } >= 2
        return texto.length >= LectorPantalla.LARGO_MINIMO || variasFrases
    }

    /** Une el texto propio, la descripción para lectores de pantalla y la pista del campo. */
    private fun textoDe(nodo: AccessibilityNodeInfo): String {
        val partes = mutableListOf<String>()
        nodo.text?.toString()?.takeIf { it.isNotBlank() }?.let { partes += it }
        nodo.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { partes += it }
        nodo.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { partes += it }
        return partes.joinToString(" ").trim()
    }
}

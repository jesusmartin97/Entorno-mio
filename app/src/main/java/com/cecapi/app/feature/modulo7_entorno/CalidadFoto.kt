package com.cecapi.app.feature.modulo7_entorno

/**
 * Revisa si una foto se puede describir. Es código puro para probarlo sin Android: recibe una
 * matriz de grises y devuelve el problema, o null si la foto sirve.
 *
 * Los umbrales son los mismos que ya usa el Lector de Documentos (Módulo 4): luminancia media 45
 * y varianza del laplaciano 60. En esta rama no existe `LuminanciaUtil`; esta clase concentra la
 * misma idea para no depender del módulo de documentos.
 */
object CalidadFoto {

    const val LUMINANCIA_MINIMA = 45.0
    const val NITIDEZ_MINIMA = 60.0

    enum class Problema {
        MUY_OSCURA,
        MOVIDA,
    }

    fun evaluar(grises: Array<DoubleArray>): Problema? {
        if (grises.isEmpty() || grises[0].isEmpty()) return null
        if (luminanciaMedia(grises) < LUMINANCIA_MINIMA) return Problema.MUY_OSCURA
        if (nitidez(grises) < NITIDEZ_MINIMA) return Problema.MOVIDA
        return null
    }

    fun mensaje(problema: Problema): String = when (problema) {
        Problema.MUY_OSCURA -> "La foto salió muy oscura. Busca más luz e intenta de nuevo."
        Problema.MOVIDA -> "La foto salió movida. Sostén el teléfono firme e intenta de nuevo."
    }

    private fun luminanciaMedia(grises: Array<DoubleArray>): Double {
        var suma = 0.0
        var cuenta = 0
        for (fila in grises) {
            for (valor in fila) {
                suma += valor
                cuenta++
            }
        }
        return if (cuenta == 0) 0.0 else suma / cuenta
    }

    /** Laplaciano: una foto nítida tiene bordes de alto contraste; una movida queda plana. */
    private fun nitidez(grises: Array<DoubleArray>): Double {
        val alto = grises.size
        val ancho = grises[0].size
        if (alto < 3 || ancho < 3) return 0.0
        val valores = ArrayList<Double>((alto - 2) * (ancho - 2))
        for (y in 1 until alto - 1) {
            for (x in 1 until ancho - 1) {
                valores += grises[y - 1][x] + grises[y + 1][x] +
                    grises[y][x - 1] + grises[y][x + 1] - 4 * grises[y][x]
            }
        }
        if (valores.isEmpty()) return 0.0
        val media = valores.average()
        return valores.sumOf { (it - media) * (it - media) } / valores.size
    }
}

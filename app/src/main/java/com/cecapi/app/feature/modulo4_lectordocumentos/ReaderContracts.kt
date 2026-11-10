package com.cecapi.app.feature.modulo4_lectordocumentos

/**
 * Resultado de procesar una foto en el Lector de Documentos.
 * El Repository lo produce y el ViewModel decide qué decir en cada caso.
 */
sealed class OcrOutcome {

    /**
     * Se reconoció texto. [documentoId] es null cuando nadie inició sesión: se lee, pero no se guarda.
     * [borrosa] marca que la foto salió movida o desenfocada pero aun así se pudo leer algo — no se
     * rechaza la foto, solo se avisa, porque quien no ve no puede saber de antemano si quedó borrosa.
     */
    data class Exito(
        val documentoId: Long?,
        val parrafos: List<String>,
        val borrosa: Boolean = false,
    ) : OcrOutcome() {
        val textoCompleto: String get() = parrafos.joinToString("\n\n")
    }

    /** La foto está demasiado oscura para leerla. No se guarda nada. */
    object PocaLuz : OcrOutcome()

    /** La foto se ve bien, pero no contiene texto suficiente. No se guarda nada. */
    object SinTexto : OcrOutcome()

    /** Falla inesperada (archivo dañado, error de ML Kit, etc.). */
    data class Error(val causa: Throwable) : OcrOutcome()
}

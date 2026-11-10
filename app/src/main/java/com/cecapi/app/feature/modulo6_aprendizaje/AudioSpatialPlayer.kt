package com.cecapi.app.feature.modulo6_aprendizaje

import android.content.Context
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================================
// AVISO PARA EL EQUIPO: este archivo es NUEVO, no existía antes.
//
// Su trabajo es reproducir un sonido corto (campana, aplauso, etc.) pudiendo
// controlar el volumen de cada oído por separado, y pudiendo ir cambiando ese
// volumen POCO A POCO mientras el sonido suena, para simular que el sonido se
// mueve o que se acerca/aleja. Esto es lo que necesitan los ejercicios de
// "sonido espacial" del módulo (izquierda/derecha, movimiento, distancia).
// =============================================================================

@Singleton
class AudioSpatialPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Reproduce el sonido del ejercicio dado, moviendo el volumen entre oídos
     * si el ejercicio lo requiere (cuando el volumen de inicio y el de fin son
     * distintos). Si son iguales, simplemente suena fijo de ese lado.
     *
     * Se queda "esperando" (suspend) hasta que termina toda la animación de
     * volumen, así el código que la llama sabe cuándo ya se puede escuchar
     * la respuesta del usuario.
     */
    suspend fun reproducir(ejercicio: EjercicioEntity) {
        detener() // por si algo se había quedado sonando de un ejercicio anterior

        val resId = context.resources.getIdentifier(ejercicio.archivoSonido, "raw", context.packageName)
        if (resId == 0) return // no se encontró el archivo de audio, evita que truene la app

        val player = MediaPlayer.create(context, resId) ?: return
        mediaPlayer = player

        player.setVolume(ejercicio.volIzqInicio, ejercicio.volDerInicio)
        player.start()

        // Va actualizando el volumen poco a poco, cada FRAME_MS milisegundos,
        // desde el volumen inicial hasta el volumen final del ejercicio.
        val totalPasos = (ejercicio.duracionMs / FRAME_MS).toInt().coerceAtLeast(1)
        for (paso in 0..totalPasos) {
            if (mediaPlayer !== player) return // el usuario ya pasó a otro ejercicio, cancelar

            val progreso = paso / totalPasos.toFloat()
            val volumenIzquierdo = interpolar(ejercicio.volIzqInicio, ejercicio.volIzqFin, progreso)
            val volumenDerecho = interpolar(ejercicio.volDerInicio, ejercicio.volDerFin, progreso)
            player.setVolume(volumenIzquierdo, volumenDerecho)

            delay(FRAME_MS)
        }
    }

    /** Detiene y libera cualquier sonido que esté reproduciéndose en este momento. */
    fun detener() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    // Calcula el volumen "a medio camino" entre el inicio y el fin, según qué
    // tan avanzada va la reproducción (progreso: 0.0 = apenas empieza, 1.0 = ya acabó).
    private fun interpolar(inicio: Float, fin: Float, progreso: Float): Float =
        inicio + (fin - inicio) * progreso

    companion object {
        // Cada cuántos milisegundos se actualiza el volumen. 50ms = 20 veces
        // por segundo, suficientemente seguido para que el movimiento se
        // sienta suave y no "a saltos".
        private const val FRAME_MS = 50L
    }
}
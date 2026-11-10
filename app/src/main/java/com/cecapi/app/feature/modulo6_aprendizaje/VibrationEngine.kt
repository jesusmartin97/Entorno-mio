package com.cecapi.app.feature.modulo6_aprendizaje

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================================
// AVISO PARA EL EQUIPO: este archivo es NUEVO, no existia antes.
//
// Hace lo mismo que AudioSpatialPlayer.kt pero para vibracion: reproduce un
// patron de pulsos (cortos, largos o una mezcla) con intensidad ajustable, y
// se queda "esperando" (suspend) hasta que termina, para que el codigo que
// llama sepa cuando ya puede pedir la respuesta de la persona.
//
// No usa archivos de audio ni res/raw -- los patrones se generan por codigo,
// asi que este archivo no depende de que alguien grabe nada.
// =============================================================================

@Singleton
class VibrationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Reproduce un patron de vibracion y espera a que termine.
     *
     * @param patronMs lista de duraciones en milisegundos, alternando pausa y
     *   vibracion: [pausaInicial, vibra1, pausa1, vibra2, pausa2, ...] (asi
     *   funciona VibrationEffect.createWaveform de Android).
     * @param intensidad 1 (suave) a 3 (fuerte). En telefonos viejos que no
     *   soportan amplitud ajustable (antes de Android 8 / API 26) se ignora
     *   y vibra siempre al maximo.
     */
    suspend fun reproducirPatron(patronMs: List<Long>, intensidad: Int) {
        if (!vibrator.hasVibrator()) return
        detener() // por si algo se habia quedado vibrando de un intento anterior

        val timings = patronMs.toLongArray()
        val duracionTotal = patronMs.sum()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitud = amplitudDeIntensidad(intensidad)
            // Un valor de amplitud por cada paso del patron: 0 en las pausas
            // (posiciones pares, empezando en 0) y "amplitud" en las vibraciones.
            val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 0) 0 else amplitud }
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }

        delay(duracionTotal)
    }

    fun detener() {
        vibrator.cancel()
    }

    private fun amplitudDeIntensidad(intensidad: Int): Int = when (intensidad) {
        1 -> 60    // suave
        2 -> 140   // media
        else -> 255 // fuerte (maximo que permite Android)
    }

    companion object {
        // Patrones base que pide la actividad. Formato:
        // pausaInicial, vibra, pausa, vibra, pausa, ...
        val PATRON_CORTO = listOf(0L, 100L, 150L, 100L, 150L, 100L)
        val PATRON_LARGO = listOf(0L, 500L, 200L, 500L)
        val PATRON_MIXTO = listOf(0L, 100L, 150L, 500L, 150L, 100L)
    }
}

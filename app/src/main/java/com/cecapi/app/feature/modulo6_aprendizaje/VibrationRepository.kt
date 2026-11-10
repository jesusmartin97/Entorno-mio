package com.cecapi.app.feature.modulo6_aprendizaje

import javax.inject.Inject
import javax.inject.Singleton

private const val INSTRUCCION_VIBRACION =
    "Siente el patrón con atención. ¿Fue corto, largo o mixto?"

@Singleton
class VibrationRepository @Inject constructor(
    private val ejercicioVibracionDao: EjercicioVibracionDao,
    private val resultadoVibracionDao: ResultadoVibracionDao,
) {
    fun observeEjerciciosDelNivel(nivel: Int) = ejercicioVibracionDao.observeByNivel(nivel)

    suspend fun registrarResultado(usuarioId: Long, ejercicioId: Long, correcto: Boolean) {
        resultadoVibracionDao.insert(
            ResultadoVibracionEntity(
                usuarioId = usuarioId,
                ejercicioVibracionId = ejercicioId,
                fueCorrecto = correcto,
            ),
        )
    }

    suspend fun seedEjerciciosSiVacio() {
        if (ejercicioVibracionDao.count() > 0) return
        ejercicioVibracionDao.insertAll(bancoInicialDeEjercicios())
    }

    /**
     * Banco inicial: los 3 patrones que pide la actividad (corto, largo,
     * mixto) x 3 intensidades (suave, media, fuerte) = 9 ejercicios, todos en
     * nivel 1 por ahora. Si el equipo quiere niveles de dificultad como en
     * audio, lo mas facil es separar esto por "nivel" mas adelante (por
     * ejemplo nivel 1 = solo corto/largo, nivel 2 = agrega mixto).
     */
    private fun bancoInicialDeEjercicios(): List<EjercicioVibracionEntity> {
        val patrones = mapOf(
            "corto" to VibrationEngine.PATRON_CORTO,
            "largo" to VibrationEngine.PATRON_LARGO,
            "mixto" to VibrationEngine.PATRON_MIXTO,
        )
        return patrones.flatMap { (nombre, patron) ->
            (1..3).map { intensidad ->
                EjercicioVibracionEntity(
                    titulo = "Patrón $nombre (intensidad $intensidad)",
                    instruccion = INSTRUCCION_VIBRACION,
                    respuestaCorrecta = nombre,
                    nivel = 1,
                    patronMs = patron.joinToString(","),
                    intensidad = intensidad,
                )
            }
        }
    }
}

/** Convierte "0,100,150,100,150,100" de vuelta a List<Long> para pasarselo a VibrationEngine. */
fun String.aPatronMs(): List<Long> = split(",").map { it.trim().toLong() }

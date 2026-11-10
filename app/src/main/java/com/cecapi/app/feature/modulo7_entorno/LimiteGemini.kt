package com.cecapi.app.feature.modulo7_entorno

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Evita gastar tokens de Gemini por acciones repetidas o simultáneas.
 *
 * Las tres funciones que usan visión (monedas, cámara normal y doble volumen arriba) pasan por aquí:
 * - Solo se permite una petición a la vez en toda la app.
 * - Debe pasar un intervalo mínimo entre una petición y la siguiente.
 *
 * Cuando no se permite la petición, quien llama usa el modelo local. Así una pulsación doble
 * accidental, una foto repetida o dos comandos seguidos no mandan varias imágenes a internet.
 */
object LimiteGemini {

    /** Tiempo mínimo entre dos peticiones a Gemini, en milisegundos. */
    private const val INTERVALO_MINIMO_MS = 3_000L

    private val enCurso = AtomicBoolean(false)

    @Volatile
    private var ultimaPeticionMs = 0L

    /**
     * Reserva el turno para una petición. Devuelve false si ya hay otra en curso o si la anterior
     * fue hace muy poco; en ese caso no se debe llamar a la API.
     */
    fun intentar(): Boolean {
        if (enCurso.get()) return false
        val ahora = SystemClock.elapsedRealtime()
        if (ahora - ultimaPeticionMs < INTERVALO_MINIMO_MS) return false
        if (!enCurso.compareAndSet(false, true)) return false
        ultimaPeticionMs = ahora
        return true
    }

    /** Libera el turno cuando la petición termina, haya funcionado o no. */
    fun terminar() {
        enCurso.set(false)
    }
}

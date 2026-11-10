package com.cecapi.app.feature.modulo6_aprendizaje

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Instrucción genérica por nivel, para que Alonso solo grabe 3 frases en vez
// de una por cada ejercicio (los 34 ejercicios de un mismo nivel comparten la
// misma instrucción).
private const val INSTRUCCION_NIVEL_1 = "Escucha con atención. ¿De qué lado viene el sonido: izquierda, derecha o ambos lados?"
private const val INSTRUCCION_NIVEL_2 = "Escucha con atención. ¿El sonido se movió, o qué tan cerca o lejos lo sentiste?"
private const val INSTRUCCION_NIVEL_3 = "Escucha con atención. Cuando el sonido se detenga, dime de qué lado viene."

@Singleton
class LearningRepository @Inject constructor(
    private val ejercicioDao: EjercicioDao,
    private val resultadoDao: ResultadoEjercicioDao,
    private val nivelDao: NivelAprendizajeDao,
) {
    fun observeNivel(usuarioId: Long): Flow<NivelAprendizajeEntity> =
        nivelDao.observeByUser(usuarioId).map { it ?: NivelAprendizajeEntity(usuarioId = usuarioId) }

    /** The exercises of one level, whatever level the person has reached (Actividades lets them pick). */
    fun observeEjerciciosPorNivel(nivel: Int): Flow<List<EjercicioEntity>> = ejercicioDao.observeByNivel(nivel)

    fun observeEjerciciosDelNivel(usuarioId: Long): Flow<List<EjercicioEntity>> =
        observeNivel(usuarioId).flatMapLatest { nivel -> ejercicioDao.observeByNivel(nivel.nivelActual) }

    suspend fun registrarResultado(usuarioId: Long, ejercicioId: Long, correcto: Boolean) {
        resultadoDao.insert(ResultadoEjercicioEntity(usuarioId = usuarioId, ejercicioId = ejercicioId, fueCorrecto = correcto))
        if (!correcto) return

        val nivelActual = nivelDao.findByUser(usuarioId) ?: NivelAprendizajeEntity(usuarioId = usuarioId)
        val nuevosPuntos = nivelActual.puntosTotales + PUNTOS_POR_ACIERTO
        val subeNivel = nuevosPuntos >= PUNTOS_PARA_SUBIR_NIVEL && nivelActual.nivelActual < NIVEL_MAXIMO
        val actualizado = nivelActual.copy(
            puntosTotales = if (subeNivel) 0 else nuevosPuntos,
            nivelActual = if (subeNivel) nivelActual.nivelActual + 1 else nivelActual.nivelActual,
            fechaActualizacion = System.currentTimeMillis(),
        )
        nivelDao.upsert(actualizado)
    }

    // ---- Banco inicial de ejercicios --------------------------------------

    suspend fun seedEjerciciosSiVacio() {
        if (ejercicioDao.count() > 0) return
        ejercicioDao.insertAll(bancoInicialDeEjercicios())
    }

    /**
     * Banco de 34 ejercicios activos ("Lista de actividades v2"). Los 6 de
     * enfrente/atrás de Nivel 3 siguen comentados más abajo (ver
     * posicionFrenteAtras) hasta validar el prototipo con personas reales.
     *
     * Nombres de archivo esperados en app/src/main/res/raw/ (sin extensión):
     * tambor, aplauso, campana, clic, motor, pasos, tarareo, silbido,
     * tono_grave, tono_medio, tono_agudo
     */
    private fun bancoInicialDeEjercicios(): List<EjercicioEntity> = listOf(

        // =====================================================================
        // NIVEL 1 — posición estática (12): 4 sonidos x 3 posiciones
        // duracionMs corto porque son sonidos percusivos de un solo golpe.
        // =====================================================================
        posicionFija("Tambor / bombo", "tambor", "izquierda", 1),
        posicionFija("Tambor / bombo", "tambor", "derecha", 1),
        posicionFija("Tambor / bombo", "tambor", "centro", 1),
        posicionFija("Aplauso", "aplauso", "izquierda", 1),
        posicionFija("Aplauso", "aplauso", "derecha", 1),
        posicionFija("Aplauso", "aplauso", "centro", 1),
        posicionFija("Campana", "campana", "izquierda", 1),
        posicionFija("Campana", "campana", "derecha", 1),
        posicionFija("Campana", "campana", "centro", 1),
        posicionFija("Clic / chasquido", "clic", "izquierda", 1),
        posicionFija("Clic / chasquido", "clic", "derecha", 1),
        posicionFija("Clic / chasquido", "clic", "centro", 1),

        // =====================================================================
        // NIVEL 2 — movimiento y distancia (16): 4 sonidos x 4 variantes
        // duracionMs más larga porque el volumen va cambiando poco a poco.
        // =====================================================================
        movimiento("Motor de auto", "motor", deIzqADer = true, 2),
        movimiento("Motor de auto", "motor", deIzqADer = false, 2),
        distancia("Motor de auto", "motor", cerca = true, 2),
        distancia("Motor de auto", "motor", cerca = false, 2),

        movimiento("Pasos caminando", "pasos", deIzqADer = true, 2),
        movimiento("Pasos caminando", "pasos", deIzqADer = false, 2),
        distancia("Pasos caminando", "pasos", cerca = true, 2),
        distancia("Pasos caminando", "pasos", cerca = false, 2),

        movimiento("Voz / tarareo", "tarareo", deIzqADer = true, 2),
        movimiento("Voz / tarareo", "tarareo", deIzqADer = false, 2),
        distancia("Voz / tarareo", "tarareo", cerca = true, 2),
        distancia("Voz / tarareo", "tarareo", cerca = false, 2),

        movimiento("Silbido", "silbido", deIzqADer = true, 2),
        movimiento("Silbido", "silbido", deIzqADer = false, 2),
        distancia("Silbido", "silbido", cerca = true, 2),
        distancia("Silbido", "silbido", cerca = false, 2),

        // =====================================================================
        // NIVEL 3 — efecto 8D (6 de 12 activos: izquierda/derecha; enfrente/atrás pendiente para el 17 de octubre)
        // Izquierda/derecha usan UN solo archivo por tono y la app hace el
        // paneo en tiempo real con el volumen (igual que los otros niveles).
        // Enfrente/atrás usan un archivo DISTINTO por posición (ver
        // posicionFrenteAtras más abajo) porque, con solo volumen, ambos
        // suenan idénticos — ya se confirmó por análisis de audio.
        // =====================================================================
        posicionFija("Tono continuo (grave, 110 Hz)", "tono_grave", "derecha", 3),
        posicionFija("Tono continuo (grave, 110 Hz)", "tono_grave", "izquierda", 3),
        posicionFija("Tono continuo (medio, 440 Hz)", "tono_medio", "derecha", 3),
        posicionFija("Tono continuo (medio, 440 Hz)", "tono_medio", "izquierda", 3),
        posicionFija("Tono continuo (agudo, 1760 Hz)", "tono_agudo", "derecha", 3),
        posicionFija("Tono continuo (agudo, 1760 Hz)", "tono_agudo", "izquierda", 3),

        // ---- PENDIENTES: enfrente / atrás -----------------------------------
        // Existe un prototipo (click de ruido coloreado, ver posicionFrenteAtras
        // más abajo) con diferencia real de audio confirmada por análisis, pero
        // SIN validar todavía con personas reales si de verdad se siente como
        // "enfrente" o "atrás" y no solo como "dos clics distintos". Hasta no
        // hacer esa prueba, se quedan comentados — no reportar esto como
        // resuelto. Avanza para el 17 de octubre.
        // posicionFrenteAtras("Tono continuo (grave, 110 Hz)", "tono_grave", atras = false),
        // posicionFrenteAtras("Tono continuo (grave, 110 Hz)", "tono_grave", atras = true),
        // posicionFrenteAtras("Tono continuo (medio, 440 Hz)", "tono_medio", atras = false),
        // posicionFrenteAtras("Tono continuo (medio, 440 Hz)", "tono_medio", atras = true),
        // posicionFrenteAtras("Tono continuo (agudo, 1760 Hz)", "tono_agudo", atras = false),
        // posicionFrenteAtras("Tono continuo (agudo, 1760 Hz)", "tono_agudo", atras = true),
    )

    // ---- Funciones auxiliares para no repetir código 34 veces -------------

    /** Un sonido fijo en una posición: izquierda, derecha o centro. No se mueve. */
    private fun posicionFija(titulo: String, archivo: String, posicion: String, nivel: Int): EjercicioEntity {
        val (volIzq, volDer) = when (posicion) {
            "izquierda" -> 1f to 0f
            "derecha" -> 0f to 1f
            else -> 1f to 1f // centro
        }
        return EjercicioEntity(
            titulo = titulo,
            instruccion = instruccionDeNivel(nivel),
            respuestaCorrecta = posicion,
            nivel = nivel,
            archivoSonido = archivo,
            volIzqInicio = volIzq,
            volDerInicio = volDer,
            duracionMs = if (nivel == 3) 4000 else 1200,
        )
    }

    /**
     * Enfrente o atrás: a diferencia de posicionFija(), aquí el volumen va
     * SIEMPRE centrado (1f, 1f) en ambos oídos — la pista de dirección no
     * viene del volumen (eso da el mismo resultado para las dos posiciones),
     * sino de un archivo de audio distinto para cada una: el tono trae un
     * click corto de ruido al inicio, coloreado distinto según la posición
     * (agudo para "atrás", más grave para "enfrente"). Por eso el nombre de
     * archivo lleva el sufijo _enfrente o _atras.
     */
    private fun posicionFrenteAtras(titulo: String, archivoBase: String, atras: Boolean): EjercicioEntity {
        val posicion = if (atras) "atras" else "enfrente"
        return EjercicioEntity(
            titulo = titulo,
            instruccion = instruccionDeNivel(3),
            respuestaCorrecta = posicion,
            nivel = 3,
            archivoSonido = "${archivoBase}_$posicion",
            volIzqInicio = 1f,
            volDerInicio = 1f,
            duracionMs = 4000,
        )
    }

    /** Un sonido que se mueve de un lado a otro. */
    private fun movimiento(titulo: String, archivo: String, deIzqADer: Boolean, nivel: Int): EjercicioEntity {
        val volIzqInicio = if (deIzqADer) 1f else 0f
        val volDerInicio = if (deIzqADer) 0f else 1f
        return EjercicioEntity(
            titulo = titulo,
            instruccion = instruccionDeNivel(nivel),
            respuestaCorrecta = if (deIzqADer) "de izquierda a derecha" else "de derecha a izquierda",
            nivel = nivel,
            archivoSonido = archivo,
            volIzqInicio = volIzqInicio,
            volDerInicio = volDerInicio,
            volIzqFin = 1f - volIzqInicio,
            volDerFin = 1f - volDerInicio,
            duracionMs = 3000,
        )
    }

    /** Un sonido fijo (no se mueve de lado), pero fuerte (cerca) o suave (lejos). */
    private fun distancia(titulo: String, archivo: String, cerca: Boolean, nivel: Int): EjercicioEntity {
        val volumen = if (cerca) 1f else 0.15f
        return EjercicioEntity(
            titulo = titulo,
            instruccion = instruccionDeNivel(nivel),
            respuestaCorrecta = if (cerca) "cerca" else "lejos",
            nivel = nivel,
            archivoSonido = archivo,
            volIzqInicio = volumen,
            volDerInicio = volumen,
            duracionMs = 2000,
        )
    }

    private fun instruccionDeNivel(nivel: Int): String = when (nivel) {
        1 -> INSTRUCCION_NIVEL_1
        2 -> INSTRUCCION_NIVEL_2
        else -> INSTRUCCION_NIVEL_3
    }

    companion object {
        private const val PUNTOS_POR_ACIERTO = 10
        private const val PUNTOS_PARA_SUBIR_NIVEL = 50
        private const val NIVEL_MAXIMO = 3
    }
}
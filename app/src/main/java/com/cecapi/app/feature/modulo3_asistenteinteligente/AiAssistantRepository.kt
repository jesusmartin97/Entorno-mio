package com.cecapi.app.feature.modulo3_asistenteinteligente

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiAssistantRepository @Inject constructor(
    private val api: AiAssistantApi,
    private val consultaIaDao: ConsultaIaDao,
    private val respuestaIaDao: RespuestaIaDao,
    private val contextoDao: ContextoConversacionDao,
) {
    fun observeRecent(usuarioId: Long): Flow<List<ConsultaIaEntity>> = consultaIaDao.observeRecent(usuarioId)

    suspend fun ask(usuarioId: Long, pregunta: String): Result<String> {
        val contexto = contextoDao.findByUser(usuarioId)
        val historial = contexto?.resumenContexto?.let { listOf(it) } ?: emptyList()

        val resultado = api.ask(pregunta, historial)

        val consultaId = consultaIaDao.insert(ConsultaIaEntity(usuarioId = usuarioId, textoPregunta = pregunta))
        resultado.onSuccess { respuesta ->
            respuestaIaDao.insert(RespuestaIaEntity(consultaId = consultaId, textoRespuesta = respuesta))
            val nuevoResumen = "P: $pregunta / R: $respuesta"
            if (contexto == null) {
                contextoDao.insert(ContextoConversacionEntity(usuarioId = usuarioId, resumenContexto = nuevoResumen))
            } else {
                contextoDao.updateResumen(contexto.id, nuevoResumen, System.currentTimeMillis())
            }
        }
        return resultado
    }
}

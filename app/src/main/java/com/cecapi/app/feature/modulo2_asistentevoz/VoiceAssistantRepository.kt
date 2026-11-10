package com.cecapi.app.feature.modulo2_asistentevoz

import com.cecapi.app.core.model.ModuloCecapi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceAssistantRepository @Inject constructor(
    private val comandoVozDao: ComandoVozDao,
    private val historialComandoDao: HistorialComandoDao,
    private val respuestaAuditivaDao: RespuestaAuditivaDao,
) {
    fun observeRecent(usuarioId: Long): Flow<List<ComandoVozEntity>> = comandoVozDao.observeRecent(usuarioId)

    /** Logs one full voice interaction: the command heard, where it routed, and what was said back. */
    suspend fun logInteraction(
        usuarioId: Long,
        textoComando: String,
        moduloDestino: ModuloCecapi?,
        textoRespuesta: String,
    ) {
        val comandoId = comandoVozDao.insert(ComandoVozEntity(usuarioId = usuarioId, textoComando = textoComando))
        historialComandoDao.insert(
            HistorialComandoEntity(
                comandoId = comandoId,
                moduloDestino = moduloDestino?.storageCode,
                resultado = if (moduloDestino != null) "ejecutado" else "no_reconocido",
            ),
        )
        respuestaAuditivaDao.insert(RespuestaAuditivaEntity(comandoId = comandoId, textoRespuesta = textoRespuesta))
    }
}

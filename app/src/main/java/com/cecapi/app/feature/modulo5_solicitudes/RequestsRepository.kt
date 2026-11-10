package com.cecapi.app.feature.modulo5_solicitudes

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Matches `{placeholder_name}` tokens inside a plantilla body. */
private val PLACEHOLDER_REGEX = Regex("\\{(\\w+)}")

fun extractPlaceholders(cuerpoPlantilla: String): List<String> =
    PLACEHOLDER_REGEX.findAll(cuerpoPlantilla).map { it.groupValues[1] }.distinct().toList()

@Singleton
class RequestsRepository @Inject constructor(
    private val plantillaDao: PlantillaSolicitudDao,
    private val solicitudDao: SolicitudGeneradaDao,
    private val datoDao: DatoSolicitudDao,
) {
    fun observePlantillas(): Flow<List<PlantillaSolicitudEntity>> = plantillaDao.observeAll()

    fun observeSolicitudes(usuarioId: Long): Flow<List<SolicitudGeneradaEntity>> = solicitudDao.observeByUser(usuarioId)

    /**
     * Answers fill in the template either way: the document is never withheld for lack of an account.
     * With no [usuarioId] (nobody signed in) it is read out loud but not saved — there is no history to
     * add it to — instead of discarding everything the person just answered.
     */
    suspend fun generarSolicitud(
        usuarioId: Long?,
        plantilla: PlantillaSolicitudEntity,
        datos: Map<String, String>,
    ): SolicitudGeneradaEntity {
        var textoFinal = plantilla.cuerpoPlantilla
        datos.forEach { (clave, valor) -> textoFinal = textoFinal.replace("{$clave}", valor) }

        val solicitudId = usuarioId?.let { id ->
            val nuevoId = solicitudDao.insert(
                SolicitudGeneradaEntity(usuarioId = id, plantillaId = plantilla.id, textoFinal = textoFinal),
            )
            datoDao.insertAll(
                datos.map { (clave, valor) -> DatoSolicitudEntity(solicitudId = nuevoId, clave = clave, valor = valor) },
            )
            nuevoId
        }
        return SolicitudGeneradaEntity(
            id = solicitudId ?: 0,
            usuarioId = usuarioId ?: 0,
            plantillaId = plantilla.id,
            textoFinal = textoFinal,
        )
    }
}

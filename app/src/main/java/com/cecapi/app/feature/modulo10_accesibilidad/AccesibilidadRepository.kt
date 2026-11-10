package com.cecapi.app.feature.modulo10_accesibilidad

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lee de Room el perfil activo y lo convierte a [PerfilGestos] para el motor. Si la base todavía no tiene
 * perfiles, devuelve el [PerfilesPredeterminados.basico] sin escribir nada.
 */
@Singleton
class AccesibilidadRepository @Inject constructor(
    private val perfilDao: PerfilAccesibilidadDao,
    private val gestoDao: GestoAccesibilidadDao,
) {

    fun observePerfiles(): Flow<List<PerfilAccesibilidadEntity>> = perfilDao.observeTodos()

    /** El perfil activo, ya traducido; el básico si no hay ninguno guardado. */
    suspend fun perfilActivo(): PerfilGestos {
        val fila = perfilDao.activo() ?: return PerfilesPredeterminados.basico()
        return aPerfil(fila, gestoDao.gestosDePerfil(fila.id))
    }

    suspend fun activar(perfilId: Long) = perfilDao.activar(perfilId)

    /**
     * Deja el perfil básico guardado y activo la primera vez (idempotente). Devuelve el id del perfil activo.
     * No se siembra en la migración para que esta solo cree el esquema.
     */
    suspend fun asegurarPerfilPredeterminado(): Long {
        perfilDao.activo()?.let { return it.id }
        val basico = PerfilesPredeterminados.basico()
        val perfilId = perfilDao.insertar(
            PerfilAccesibilidadEntity(
                nombre = basico.nombre,
                descripcion = "Gestos recomendados para empezar: deslizar para leer y abrir CECAPI, " +
                    "y dos gestos con dos dedos para callar o detener al asistente.",
                activo = true,
            ),
        )
        basico.enlaces.forEach { (gesto, acciones) ->
            val gestoId = gestoDao.insertarGesto(GestoEntity(perfilId = perfilId, codigo = gesto.codigo))
            gestoDao.insertarAcciones(
                acciones.mapIndexed { indice, accion ->
                    AccionEntity(gestoId = gestoId, tipo = accion.tipo.codigo, parametro = accion.parametro, orden = indice)
                },
            )
        }
        return perfilId
    }

    private fun aPerfil(fila: PerfilAccesibilidadEntity, gestos: List<GestoConAcciones>): PerfilGestos {
        val enlaces = gestos.mapNotNull { filaGesto ->
            val gesto = Gesto.desdeCodigo(filaGesto.gesto.codigo) ?: return@mapNotNull null
            val acciones = filaGesto.acciones
                .sortedBy { it.orden }
                .mapNotNull { filaAccion ->
                    TipoAccion.desdeCodigo(filaAccion.tipo)?.let { Accion(it, filaAccion.parametro) }
                }
            gesto to acciones
        }.toMap()
        return PerfilGestos(id = fila.id, nombre = fila.nombre, enlaces = enlaces)
    }
}

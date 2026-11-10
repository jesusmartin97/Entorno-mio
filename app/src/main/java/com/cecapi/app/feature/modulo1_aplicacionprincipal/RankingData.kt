package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row of the individual ranking: a person and their points in Actividades. */
data class RankingFila(
    val usuarioId: Long,
    val nombreCompleto: String,
    val apodo: String?,
    val usarApodoRanking: Boolean,
    val origen: String,
    val rol: String,
    val puntosTotales: Int,
    val nivelActual: Int,
) {
    /** The real full name is never shown here. By default, just the first name; the person can
     * choose in Editar mi cuenta to show their apodo instead. */
    val nombreMostrado: String
        get() = if (usarApodoRanking) {
            apodo?.takeIf { it.isNotBlank() } ?: nombreCompleto.substringBefore(" ")
        } else {
            nombreCompleto.substringBefore(" ")
        }
}

/** One row of the institution ranking: an institution's combined points from everyone in it. */
data class RankingInstitucion(
    val origen: String,
    val puntosTotales: Int,
    val miembros: Int,
)

/**
 * Points come only from Actividades de sonidos (niveles_aprendizaje.puntos_totales) — vibración no lleva
 * puntos todavía. Cruza con usuarios para el apodo/nombre, el rol y la institución, por eso vive aparte de
 * NivelAprendizajeDao (que es de un solo módulo) y de UsuarioDao.
 */
@Dao
interface RankingDao {
    @Query(
        "SELECT u.id AS usuarioId, u.nombre_completo AS nombreCompleto, u.apodo AS apodo, " +
            "u.usar_apodo_ranking AS usarApodoRanking, u.origen AS origen, " +
            "u.rol AS rol, n.puntos_totales AS puntosTotales, n.nivel_actual AS nivelActual " +
            "FROM niveles_aprendizaje n JOIN usuarios u ON u.id = n.usuario_id " +
            "WHERE n.puntos_totales > 0 " +
            "ORDER BY n.puntos_totales DESC LIMIT 50",
    )
    fun observeIndividual(): Flow<List<RankingFila>>

    /** Solo las personas de [origen]: para que un alumno se compare con sus compañeros de institución,
     * no solo con todo el mundo en la tabla global. */
    @Query(
        "SELECT u.id AS usuarioId, u.nombre_completo AS nombreCompleto, u.apodo AS apodo, " +
            "u.usar_apodo_ranking AS usarApodoRanking, u.origen AS origen, " +
            "u.rol AS rol, n.puntos_totales AS puntosTotales, n.nivel_actual AS nivelActual " +
            "FROM niveles_aprendizaje n JOIN usuarios u ON u.id = n.usuario_id " +
            "WHERE n.puntos_totales > 0 AND u.origen = :origen COLLATE NOCASE " +
            "ORDER BY n.puntos_totales DESC LIMIT 50",
    )
    fun observeIndividualDeInstitucion(origen: String): Flow<List<RankingFila>>

    // "admin" es el origen de las cuentas de administrador (ver DatabaseModule.demoUsers), no una
    // institución real — se excluye igual que un origen vacío (usuario independiente).
    @Query(
        "SELECT u.origen AS origen, SUM(n.puntos_totales) AS puntosTotales, COUNT(DISTINCT u.id) AS miembros " +
            "FROM niveles_aprendizaje n JOIN usuarios u ON u.id = n.usuario_id " +
            "WHERE u.origen != '' AND u.origen != 'admin' AND n.puntos_totales > 0 " +
            "GROUP BY u.origen ORDER BY puntosTotales DESC LIMIT 20",
    )
    fun observeInstituciones(): Flow<List<RankingInstitucion>>
}

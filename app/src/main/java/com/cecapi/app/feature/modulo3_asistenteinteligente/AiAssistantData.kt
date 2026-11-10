package com.cecapi.app.feature.modulo3_asistenteinteligente

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.cecapi.app.feature.modulo1_aplicacionprincipal.UsuarioEntity
import kotlinx.coroutines.flow.Flow

// ---- Módulo 3: Asistente Inteligente -------------------------------------
// Tables: consultas_ia, respuestas_ia, contextos_conversacion

@Entity(
    tableName = "consultas_ia",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("usuario_id")],
)
data class ConsultaIaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "texto_pregunta")
    val textoPregunta: String,
    @ColumnInfo(name = "fecha_hora")
    val fechaHora: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "respuestas_ia",
    foreignKeys = [
        ForeignKey(
            entity = ConsultaIaEntity::class,
            parentColumns = ["id"],
            childColumns = ["consulta_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consulta_id")],
)
data class RespuestaIaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "consulta_id")
    val consultaId: Long,
    @ColumnInfo(name = "texto_respuesta")
    val textoRespuesta: String,
    @ColumnInfo(name = "fecha_hora")
    val fechaHora: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "contextos_conversacion",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("usuario_id")],
)
data class ContextoConversacionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "resumen_contexto")
    val resumenContexto: String,
    @ColumnInfo(name = "fecha_actualizacion")
    val fechaActualizacion: Long = System.currentTimeMillis(),
)

@Dao
interface ConsultaIaDao {
    @Insert
    suspend fun insert(consulta: ConsultaIaEntity): Long

    @Query("SELECT * FROM consultas_ia WHERE usuario_id = :usuarioId ORDER BY fecha_hora DESC LIMIT 50")
    fun observeRecent(usuarioId: Long): Flow<List<ConsultaIaEntity>>

    // The answers go with their question (foreign key with cascade), so nothing is left behind.
    @Query("DELETE FROM consultas_ia WHERE id = :consultaId")
    suspend fun deleteById(consultaId: Long)

    @Query("DELETE FROM consultas_ia WHERE usuario_id = :usuarioId")
    suspend fun deleteAllByUser(usuarioId: Long)
}

@Dao
interface RespuestaIaDao {
    @Insert
    suspend fun insert(respuesta: RespuestaIaEntity): Long

    @Query("SELECT * FROM respuestas_ia WHERE consulta_id = :consultaId LIMIT 1")
    suspend fun findByConsulta(consultaId: Long): RespuestaIaEntity?
}

@Dao
interface ContextoConversacionDao {
    @Query("SELECT * FROM contextos_conversacion WHERE usuario_id = :usuarioId LIMIT 1")
    suspend fun findByUser(usuarioId: Long): ContextoConversacionEntity?

    @Insert
    suspend fun insert(contexto: ContextoConversacionEntity): Long

    @Query("UPDATE contextos_conversacion SET resumen_contexto = :resumen, fecha_actualizacion = :fecha WHERE id = :id")
    suspend fun updateResumen(id: Long, resumen: String, fecha: Long)
}

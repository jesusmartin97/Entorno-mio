package com.cecapi.app.feature.modulo2_asistentevoz

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

// ---- Módulo 2: Asistente de Voz -------------------------------------------
// Tables: comandos_voz, historial_comandos, respuestas_auditivas

@Entity(
    tableName = "comandos_voz",
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
data class ComandoVozEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "texto_comando")
    val textoComando: String,
    @ColumnInfo(name = "fecha_hora")
    val fechaHora: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "historial_comandos",
    foreignKeys = [
        ForeignKey(
            entity = ComandoVozEntity::class,
            parentColumns = ["id"],
            childColumns = ["comando_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("comando_id")],
)
data class HistorialComandoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "comando_id")
    val comandoId: Long,
    @ColumnInfo(name = "modulo_destino")
    val moduloDestino: String?,
    @ColumnInfo(name = "resultado")
    val resultado: String,
)

@Entity(
    tableName = "respuestas_auditivas",
    foreignKeys = [
        ForeignKey(
            entity = ComandoVozEntity::class,
            parentColumns = ["id"],
            childColumns = ["comando_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("comando_id")],
)
data class RespuestaAuditivaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "comando_id")
    val comandoId: Long,
    @ColumnInfo(name = "texto_respuesta")
    val textoRespuesta: String,
    @ColumnInfo(name = "fecha_hora")
    val fechaHora: Long = System.currentTimeMillis(),
)

@Dao
interface ComandoVozDao {
    @Insert
    suspend fun insert(comando: ComandoVozEntity): Long

    @Query("SELECT * FROM comandos_voz WHERE usuario_id = :usuarioId ORDER BY fecha_hora DESC LIMIT 30")
    fun observeRecent(usuarioId: Long): Flow<List<ComandoVozEntity>>
}

@Dao
interface HistorialComandoDao {
    @Insert
    suspend fun insert(historial: HistorialComandoEntity): Long
}

@Dao
interface RespuestaAuditivaDao {
    @Insert
    suspend fun insert(respuesta: RespuestaAuditivaEntity): Long

    @Query("SELECT * FROM respuestas_auditivas WHERE comando_id = :comandoId LIMIT 1")
    suspend fun findByComando(comandoId: Long): RespuestaAuditivaEntity?
}

package com.cecapi.app.feature.modulo5_solicitudes

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

// ---- Módulo 5: Centro de Solicitudes ---------------------------------------
// Tables: plantillas_solicitud, solicitudes_generadas, datos_solicitud

@Entity(tableName = "plantillas_solicitud")
data class PlantillaSolicitudEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "titulo")
    val titulo: String,
    @ColumnInfo(name = "descripcion")
    val descripcion: String,
    // Placeholders use {nombre_variable} syntax, e.g. "Yo, {nombre_completo}, solicito..."
    @ColumnInfo(name = "cuerpo_plantilla")
    val cuerpoPlantilla: String,
)

@Entity(
    tableName = "solicitudes_generadas",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlantillaSolicitudEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantilla_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("usuario_id"), Index("plantilla_id")],
)
data class SolicitudGeneradaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "plantilla_id")
    val plantillaId: Long,
    @ColumnInfo(name = "texto_final")
    val textoFinal: String,
    @ColumnInfo(name = "fecha_generacion")
    val fechaGeneracion: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "datos_solicitud",
    foreignKeys = [
        ForeignKey(
            entity = SolicitudGeneradaEntity::class,
            parentColumns = ["id"],
            childColumns = ["solicitud_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("solicitud_id")],
)
data class DatoSolicitudEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "solicitud_id")
    val solicitudId: Long,
    @ColumnInfo(name = "clave")
    val clave: String,
    @ColumnInfo(name = "valor")
    val valor: String,
)

@Dao
interface PlantillaSolicitudDao {
    @Query("SELECT * FROM plantillas_solicitud ORDER BY titulo ASC")
    fun observeAll(): Flow<List<PlantillaSolicitudEntity>>

    @Insert
    suspend fun insertAll(plantillas: List<PlantillaSolicitudEntity>)

    @Query("SELECT COUNT(*) FROM plantillas_solicitud")
    suspend fun count(): Int
}

@Dao
interface SolicitudGeneradaDao {
    @Insert
    suspend fun insert(solicitud: SolicitudGeneradaEntity): Long

    @Query("SELECT * FROM solicitudes_generadas WHERE usuario_id = :usuarioId ORDER BY fecha_generacion DESC LIMIT 30")
    fun observeByUser(usuarioId: Long): Flow<List<SolicitudGeneradaEntity>>
}

@Dao
interface DatoSolicitudDao {
    @Insert
    suspend fun insertAll(datos: List<DatoSolicitudEntity>)
}

package com.cecapi.app.feature.modulo7_entorno

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

// ---- Módulo 7: Asistente del Entorno ---------------------------------------
// Tables: escaneos_entorno, objetos_detectados, descripciones_entorno

@Entity(
    tableName = "escaneos_entorno",
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
data class EscaneoEntornoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "ruta_imagen")
    val rutaImagen: String,
    @ColumnInfo(name = "fecha_escaneo")
    val fechaEscaneo: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "objetos_detectados",
    foreignKeys = [
        ForeignKey(
            entity = EscaneoEntornoEntity::class,
            parentColumns = ["id"],
            childColumns = ["escaneo_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("escaneo_id")],
)
data class ObjetoDetectadoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "escaneo_id")
    val escaneoId: Long,
    @ColumnInfo(name = "etiqueta")
    val etiqueta: String,
    @ColumnInfo(name = "confianza")
    val confianza: Float,
)

@Entity(
    tableName = "descripciones_entorno",
    foreignKeys = [
        ForeignKey(
            entity = EscaneoEntornoEntity::class,
            parentColumns = ["id"],
            childColumns = ["escaneo_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("escaneo_id", unique = true)],
)
data class DescripcionEntornoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "escaneo_id")
    val escaneoId: Long,
    @ColumnInfo(name = "texto_descripcion")
    val textoDescripcion: String,
    @ColumnInfo(name = "fecha_generacion")
    val fechaGeneracion: Long = System.currentTimeMillis(),
)

@Dao
interface EscaneoEntornoDao {
    @Insert
    suspend fun insert(escaneo: EscaneoEntornoEntity): Long

    @Query("SELECT * FROM escaneos_entorno WHERE usuario_id = :usuarioId ORDER BY fecha_escaneo DESC LIMIT 30")
    fun observeRecent(usuarioId: Long): Flow<List<EscaneoEntornoEntity>>
}

@Dao
interface ObjetoDetectadoDao {
    @Insert
    suspend fun insertAll(objetos: List<ObjetoDetectadoEntity>)
}

@Dao
interface DescripcionEntornoDao {
    @Insert
    suspend fun insert(descripcion: DescripcionEntornoEntity): Long
}

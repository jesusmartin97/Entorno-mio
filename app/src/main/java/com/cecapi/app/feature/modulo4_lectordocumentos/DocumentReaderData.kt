package com.cecapi.app.feature.modulo4_lectordocumentos

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

// ---- Módulo 4: Lector Inteligente de Documentos ---------------------------
// Tables: documentos_escaneados, textos_extraidos, historial_lecturas

@Entity(
    tableName = "documentos_escaneados",
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
data class DocumentoEscaneadoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "ruta_imagen")
    val rutaImagen: String,
    @ColumnInfo(name = "fecha_captura")
    val fechaCaptura: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "textos_extraidos",
    foreignKeys = [
        ForeignKey(
            entity = DocumentoEscaneadoEntity::class,
            parentColumns = ["id"],
            childColumns = ["documento_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documento_id", unique = true)],
)
data class TextoExtraidoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "documento_id")
    val documentoId: Long,
    @ColumnInfo(name = "texto_completo")
    val textoCompleto: String,
    @ColumnInfo(name = "fecha_extraccion")
    val fechaExtraccion: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "historial_lecturas",
    foreignKeys = [
        ForeignKey(
            entity = DocumentoEscaneadoEntity::class,
            parentColumns = ["id"],
            childColumns = ["documento_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documento_id")],
)
data class HistorialLecturaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "documento_id")
    val documentoId: Long,
    @ColumnInfo(name = "fecha_lectura")
    val fechaLectura: Long = System.currentTimeMillis(),
)

@Dao
interface DocumentoEscaneadoDao {
    @Insert
    suspend fun insert(documento: DocumentoEscaneadoEntity): Long

    @Query("SELECT * FROM documentos_escaneados WHERE usuario_id = :usuarioId ORDER BY fecha_captura DESC LIMIT 30")
    fun observeRecent(usuarioId: Long): Flow<List<DocumentoEscaneadoEntity>>
}

@Dao
interface TextoExtraidoDao {
    @Insert
    suspend fun insert(texto: TextoExtraidoEntity): Long

    @Query("SELECT * FROM textos_extraidos WHERE documento_id = :documentoId LIMIT 1")
    suspend fun findByDocumento(documentoId: Long): TextoExtraidoEntity?
}

@Dao
interface HistorialLecturaDao {
    @Insert
    suspend fun insert(historial: HistorialLecturaEntity): Long
}

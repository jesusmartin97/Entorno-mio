package com.cecapi.app.feature.modulo6_aprendizaje

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.cecapi.app.feature.modulo1_aplicacionprincipal.UsuarioEntity
import kotlinx.coroutines.flow.Flow

// ---- Módulo 6: Centro de Aprendizaje ---------------------------------------
// Tables: ejercicios, resultados_ejercicios, niveles_aprendizaje
//
// AVISO: este archivo cambió respecto a la primera versión. Los ejercicios ya
// no son "qué comando de voz dirías" — ahora son de sonido espacial (entrenar
// el oído: de qué lado viene un sonido, si se mueve, si está cerca o lejos).
// Por eso EjercicioEntity trae campos nuevos: qué archivo de audio usar y con
// qué volumen suena en cada oído (para eso sirve AudioSpatialPlayer.kt).

@Entity(tableName = "ejercicios")
data class EjercicioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "titulo")
    val titulo: String,
    @ColumnInfo(name = "instruccion")
    val instruccion: String,
    @ColumnInfo(name = "respuesta_correcta")
    val respuestaCorrecta: String,
    @ColumnInfo(name = "nivel")
    val nivel: Int,

    // ---- Campos de audio espacial (nuevos) --------------------------------
    @ColumnInfo(name = "archivo_sonido")
    val archivoSonido: String, // nombre del archivo en res/raw, sin extensión
    @ColumnInfo(name = "vol_izq_inicio")
    val volIzqInicio: Float, // 0f (silencio) a 1f (volumen máximo)
    @ColumnInfo(name = "vol_der_inicio")
    val volDerInicio: Float,
    @ColumnInfo(name = "vol_izq_fin")
    val volIzqFin: Float = volIzqInicio, // igual al de inicio = sonido fijo, no se mueve
    @ColumnInfo(name = "vol_der_fin")
    val volDerFin: Float = volDerInicio,
    @ColumnInfo(name = "duracion_ms")
    val duracionMs: Long = 1500,
)

@Entity(
    tableName = "resultados_ejercicios",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EjercicioEntity::class,
            parentColumns = ["id"],
            childColumns = ["ejercicio_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("usuario_id"), Index("ejercicio_id")],
)
data class ResultadoEjercicioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "ejercicio_id")
    val ejercicioId: Long,
    @ColumnInfo(name = "fue_correcto")
    val fueCorrecto: Boolean,
    @ColumnInfo(name = "fecha_realizado")
    val fechaRealizado: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "niveles_aprendizaje",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("usuario_id", unique = true)],
)
data class NivelAprendizajeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "nivel_actual")
    val nivelActual: Int = 1,
    @ColumnInfo(name = "puntos_totales")
    val puntosTotales: Int = 0,
    @ColumnInfo(name = "fecha_actualizacion")
    val fechaActualizacion: Long = System.currentTimeMillis(),
)

@Dao
interface EjercicioDao {
    @Query("SELECT * FROM ejercicios WHERE nivel = :nivel ORDER BY id ASC")
    fun observeByNivel(nivel: Int): Flow<List<EjercicioEntity>>

    @Insert
    suspend fun insertAll(ejercicios: List<EjercicioEntity>)

    @Query("SELECT COUNT(*) FROM ejercicios")
    suspend fun count(): Int
}

@Dao
interface ResultadoEjercicioDao {
    @Insert
    suspend fun insert(resultado: ResultadoEjercicioEntity): Long
}

@Dao
interface NivelAprendizajeDao {
    @Query("SELECT * FROM niveles_aprendizaje WHERE usuario_id = :usuarioId LIMIT 1")
    fun observeByUser(usuarioId: Long): Flow<NivelAprendizajeEntity?>

    @Query("SELECT * FROM niveles_aprendizaje WHERE usuario_id = :usuarioId LIMIT 1")
    suspend fun findByUser(usuarioId: Long): NivelAprendizajeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(nivel: NivelAprendizajeEntity)

    @Update
    suspend fun update(nivel: NivelAprendizajeEntity)
}
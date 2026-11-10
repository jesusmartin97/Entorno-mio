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
import com.cecapi.app.feature.modulo1_aplicacionprincipal.UsuarioEntity
import kotlinx.coroutines.flow.Flow

// ---- Modulo 6: actividad de vibracion --------------------------------------
// Tablas propias (separadas de "ejercicios" de audio) porque los datos que
// necesita una vibracion no son los mismos que los de un sonido: aqui no hay
// archivo ni volumen por oido, hay un patron de milisegundos y una intensidad.

@Entity(tableName = "ejercicios_vibracion")
data class EjercicioVibracionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "titulo")
    val titulo: String,
    @ColumnInfo(name = "instruccion")
    val instruccion: String,
    @ColumnInfo(name = "respuesta_correcta")
    val respuestaCorrecta: String, // "corto", "largo" o "mixto"
    @ColumnInfo(name = "nivel")
    val nivel: Int,
    @ColumnInfo(name = "patron_ms")
    val patronMs: String, // milisegundos separados por coma, ej "0,100,150,100,150,100"
    @ColumnInfo(name = "intensidad")
    val intensidad: Int, // 1 (suave) a 3 (fuerte)
)

@Entity(
    tableName = "resultados_vibracion",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EjercicioVibracionEntity::class,
            parentColumns = ["id"],
            childColumns = ["ejercicio_vibracion_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("usuario_id"), Index("ejercicio_vibracion_id")],
)
data class ResultadoVibracionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "ejercicio_vibracion_id")
    val ejercicioVibracionId: Long,
    @ColumnInfo(name = "fue_correcto")
    val fueCorrecto: Boolean,
    @ColumnInfo(name = "fecha_realizado")
    val fechaRealizado: Long = System.currentTimeMillis(),
)

@Dao
interface EjercicioVibracionDao {
    @Query("SELECT * FROM ejercicios_vibracion WHERE nivel = :nivel ORDER BY id ASC")
    fun observeByNivel(nivel: Int): Flow<List<EjercicioVibracionEntity>>

    @Insert
    suspend fun insertAll(ejercicios: List<EjercicioVibracionEntity>)

    @Query("SELECT COUNT(*) FROM ejercicios_vibracion")
    suspend fun count(): Int
}

@Dao
interface ResultadoVibracionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(resultado: ResultadoVibracionEntity): Long
}

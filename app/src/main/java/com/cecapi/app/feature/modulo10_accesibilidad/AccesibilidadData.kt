package com.cecapi.app.feature.modulo10_accesibilidad

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// ---- Módulo 10 (v1.5): Configuración Accesible Avanzada ---------------------
// Tablas de este entregable: perfiles, gestos, acciones.
//
// Un perfil agrupa qué hace cada gesto (por ejemplo "Baja visión" o "Ceguera total"). El perfil
// marcado como activo es el que usa el motor (GestorGestos). Los nombres siguen el pedido del
// responsable del entregable; el Módulo 10 del esquema (database/schema.sql) usa "perfiles_accesibilidad"
// para otra idea aún sin código, por confirmar con Pp si conviene unificar.

@Entity(tableName = "perfiles")
data class PerfilAccesibilidadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "nombre")
    val nombre: String,
    @ColumnInfo(name = "descripcion")
    val descripcion: String,
    /** Solo un perfil está activo a la vez; lo controla [PerfilAccesibilidadDao.activar]. */
    @ColumnInfo(name = "activo")
    val activo: Boolean = false,
)

@Entity(
    tableName = "gestos",
    foreignKeys = [
        ForeignKey(
            entity = PerfilAccesibilidadEntity::class,
            parentColumns = ["id"],
            childColumns = ["perfil_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("perfil_id"),
        // Un mismo gesto no puede repetirse dentro de un perfil.
        Index(value = ["perfil_id", "codigo"], unique = true),
    ],
)
data class GestoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "perfil_id")
    val perfilId: Long,
    /** Código de [Gesto] (por ejemplo "deslizar_arriba"), no el entero de Android. */
    @ColumnInfo(name = "codigo")
    val codigo: String,
)

@Entity(
    tableName = "acciones",
    foreignKeys = [
        ForeignKey(
            entity = GestoEntity::class,
            parentColumns = ["id"],
            childColumns = ["gesto_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gesto_id")],
)
data class AccionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "gesto_id")
    val gestoId: Long,
    /** Nombre de [TipoAccion] (por ejemplo "hablar"). */
    @ColumnInfo(name = "tipo")
    val tipo: String,
    /** Texto para HABLAR o COMANDO_VOZ; nulo en los demás. */
    @ColumnInfo(name = "parametro")
    val parametro: String? = null,
    /** Si un gesto dispara varias acciones, se ejecutan de menor a mayor. */
    @ColumnInfo(name = "orden")
    val orden: Int = 0,
)

/** Un gesto con sus acciones, tal como lo devuelve Room. */
data class GestoConAcciones(
    @Embedded val gesto: GestoEntity,
    @Relation(parentColumn = "id", entityColumn = "gesto_id")
    val acciones: List<AccionEntity>,
)

@Dao
interface PerfilAccesibilidadDao {
    @Query("SELECT * FROM perfiles ORDER BY nombre")
    fun observeTodos(): Flow<List<PerfilAccesibilidadEntity>>

    @Query("SELECT * FROM perfiles WHERE activo = 1 LIMIT 1")
    suspend fun activo(): PerfilAccesibilidadEntity?

    @Insert
    suspend fun insertar(perfil: PerfilAccesibilidadEntity): Long

    @Query("UPDATE perfiles SET activo = CASE WHEN id = :perfilId THEN 1 ELSE 0 END")
    suspend fun activar(perfilId: Long)

    @Query("DELETE FROM perfiles WHERE id = :perfilId")
    suspend fun borrar(perfilId: Long)
}

@Dao
interface GestoAccesibilidadDao {
    @Transaction
    @Query("SELECT * FROM gestos WHERE perfil_id = :perfilId")
    fun observeDePerfil(perfilId: Long): Flow<List<GestoConAcciones>>

    @Transaction
    @Query("SELECT * FROM gestos WHERE perfil_id = :perfilId")
    suspend fun gestosDePerfil(perfilId: Long): List<GestoConAcciones>

    @Insert
    suspend fun insertarGesto(gesto: GestoEntity): Long

    @Insert
    suspend fun insertarAcciones(acciones: List<AccionEntity>)
}

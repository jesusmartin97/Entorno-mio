package com.cecapi.app.feature.modulo1_aplicacionprincipal

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
import kotlinx.coroutines.flow.Flow

// ---- Módulo 1: Aplicación Principal Accesible -----------------------------
// Tables: usuarios, configuracion_usuario, permisos_modulos (see docs/database/schema.sql)

/** Who is signing in. Stored by [codigo] in `usuarios.rol`; anyone who self-registers is a plain [USUARIO]. */
enum class RolUsuario(val codigo: String) {
    ADMINISTRADOR("administrador"),
    DIRECTIVO("directivo"),
    EDUCADOR("educador"),
    ALUMNO("alumno"),
    USUARIO("usuario"),
    ;

    companion object {
        fun fromCodigo(codigo: String): RolUsuario = entries.find { it.codigo == codigo } ?: USUARIO
    }
}

@Entity(tableName = "usuarios")
data class UsuarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Always stored in UPPERCASE so "Jorge", "jorge" and "JORGE" are the same account. */
    @ColumnInfo(name = "nombre_usuario")
    val nombreUsuario: String,
    @ColumnInfo(name = "contrasena_hash")
    val contrasenaHash: String,
    @ColumnInfo(name = "nombre_completo")
    val nombreCompleto: String,
    @ColumnInfo(name = "fecha_registro")
    val fechaRegistro: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "rol")
    val rol: String = RolUsuario.USUARIO.codigo,
    /** Institution or place the person comes from (e.g. "CECAPI"); empty when they signed up on their own. */
    @ColumnInfo(name = "origen")
    val origen: String = "",
    /** For an alumno: the id of their assigned educador (another row in this same table). Null until assigned,
     * and always null for every other role — an educador, directivo or usuario has no "own teacher". */
    @ColumnInfo(name = "educador_id")
    val educadorId: Long? = null,
    /** Shown on the ranking instead of the real name — chosen by the person, null until they set one. */
    @ColumnInfo(name = "apodo")
    val apodo: String? = null,
    /**
     * Does NOT block logging in or using the app — "por default se les dejará ingresar". It only flags
     * the account for whoever in the chain above them (administrador > directivo > educador > alumno, or
     * administrador > usuario) to review in Gestión. New self-registered accounts start false; a demo
     * account or an account that already existed before this column was added starts true.
     */
    @ColumnInfo(name = "validado")
    val validado: Boolean = false,
    /** En Clasificación, por default se muestra el nombre; esto deja mostrar el apodo en su lugar. */
    @ColumnInfo(name = "usar_apodo_ranking")
    val usarApodoRanking: Boolean = false,
    /** Epoch millis. Nula en cuentas de antes de este campo. Es lo único que dice si la cuenta es de un menor. */
    @ColumnInfo(name = "fecha_nacimiento")
    val fechaNacimiento: Long? = null,
    /** Opcional: solo sirve para recuperar la contraseña. Nunca se muestra a otras personas. */
    @ColumnInfo(name = "email")
    val email: String? = null,
    @ColumnInfo(name = "solicita_validacion")
    val solicitaValidacion: Boolean = false,
    @ColumnInfo(name = "solicita_baja")
    val solicitaBaja: Boolean = false,
    /** Un aviso que se le dice a la persona al abrir la app, por ejemplo un cambio de contraseña hecho por un administrador. */
    @ColumnInfo(name = "aviso_pendiente")
    val avisoPendiente: String? = null,
    /** "nueva" hasta que un administrador la confirma; las cuentas de antes ya están confirmadas. */
    @ColumnInfo(name = "revision_cuenta")
    val revisionCuenta: String = "confirmada",
    /** Institución a la que pidió unirse, mientras la institución no la valida. Vacía cuando ya es parte. */
    @ColumnInfo(name = "institucion_solicitada")
    val institucionSolicitada: String? = null,
    @ColumnInfo(name = "solicita_cambio_educador")
    val solicitaCambioEducador: Boolean = false,
)

@Entity(tableName = "cambios_cuenta", indices = [Index("objetivo_id")])
data class CambioCuentaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "objetivo_id")
    val objetivoId: Long,
    @ColumnInfo(name = "autor_id")
    val autorId: Long?,
    @ColumnInfo(name = "tipo")
    val tipo: String,
    @ColumnInfo(name = "detalle")
    val detalle: String?,
    @ColumnInfo(name = "fecha")
    val fecha: Long = System.currentTimeMillis(),
)

@Dao
interface CambioCuentaDao {
    @Insert
    suspend fun insert(cambio: CambioCuentaEntity)

    @Query("SELECT * FROM cambios_cuenta WHERE objetivo_id = :objetivoId ORDER BY fecha DESC")
    suspend fun deUsuario(objetivoId: Long): List<CambioCuentaEntity>
}

@Entity(
    tableName = "configuracion_usuario",
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
data class ConfiguracionUsuarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "velocidad_voz")
    val velocidadVoz: Float = 1.0f,
    @ColumnInfo(name = "volumen")
    val volumen: Float = 1.0f,
    @ColumnInfo(name = "modo_simple")
    val modoSimple: Boolean = true,
)

@Entity(
    tableName = "permisos_modulos",
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
data class PermisosModuloEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "modulo_codigo")
    val moduloCodigo: String,
    @ColumnInfo(name = "habilitado")
    val habilitado: Boolean = true,
)

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios WHERE nombre_usuario = :nombreUsuario COLLATE NOCASE LIMIT 1")
    suspend fun findByUsername(nombreUsuario: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UsuarioEntity?

    /** For an educador: the alumnos assigned to them, nobody else's. */
    @Query("SELECT * FROM usuarios WHERE educador_id = :educadorId ORDER BY nombre_completo")
    fun observeAlumnosDeEducador(educadorId: Long): Flow<List<UsuarioEntity>>

    /** For a directivo: everyone in their institution, every role. An educador's panel narrows this itself. */
    @Query(
        "SELECT * FROM usuarios WHERE origen = :origen COLLATE NOCASE " +
            "OR (institucion_solicitada = :origen COLLATE NOCASE AND solicita_validacion = 1) " +
            "ORDER BY rol, nombre_completo",
    )
    fun observeUsuariosDeInstitucion(origen: String): Flow<List<UsuarioEntity>>

    @Query(
        "SELECT * FROM usuarios WHERE institucion_solicitada = :institucion COLLATE NOCASE " +
            "AND solicita_validacion = 1 ORDER BY nombre_completo",
    )
    fun observeSolicitudesDe(institucion: String): Flow<List<UsuarioEntity>>

    /** La institución valida a la persona: su institución pedida pasa a ser la institución validada. */
    @Query(
        "UPDATE usuarios SET origen = institucion_solicitada, validado = 1, solicita_validacion = 0, " +
            "institucion_solicitada = NULL WHERE id = :usuarioId AND institucion_solicitada IS NOT NULL",
    )
    suspend fun confirmarInstitucion(usuarioId: Long)

    @Query("UPDATE usuarios SET institucion_solicitada = :institucion, solicita_validacion = 1 WHERE id = :usuarioId")
    suspend fun solicitarInstitucion(usuarioId: Long, institucion: String)

    /** La institución no valida la solicitud: la persona sigue como usuario y puede volver a pedirlo. */
    @Query("UPDATE usuarios SET institucion_solicitada = NULL, solicita_validacion = 0 WHERE id = :usuarioId")
    suspend fun rechazarSolicitud(usuarioId: Long)

    /** For an administrador: literally everyone — every institution and every independent usuario. */
    @Query("SELECT * FROM usuarios ORDER BY origen, rol, nombre_completo")
    fun observeTodos(): Flow<List<UsuarioEntity>>

    @Query("UPDATE usuarios SET educador_id = :educadorId WHERE id = :alumnoId")
    suspend fun asignarEducador(alumnoId: Long, educadorId: Long?)

    @Query("UPDATE usuarios SET rol = :rol WHERE id = :usuarioId")
    suspend fun cambiarRol(usuarioId: Long, rol: String)

    @Query("UPDATE usuarios SET apodo = :apodo WHERE id = :usuarioId")
    suspend fun actualizarApodo(usuarioId: Long, apodo: String?)

    @Query("UPDATE usuarios SET nombre_completo = :nombre WHERE id = :usuarioId")
    suspend fun actualizarNombre(usuarioId: Long, nombre: String)

    @Query("UPDATE usuarios SET contrasena_hash = :hash WHERE id = :usuarioId")
    suspend fun actualizarContrasena(usuarioId: Long, hash: String)

    @Query("UPDATE usuarios SET validado = 1 WHERE id = :usuarioId")
    suspend fun validar(usuarioId: Long)

    @Query("SELECT COUNT(*) FROM usuarios WHERE rol = 'administrador'")
    suspend fun contarAdministradores(): Int

    @Query("SELECT * FROM usuarios WHERE revision_cuenta = 'nueva' ORDER BY id DESC")
    fun observeNuevas(): kotlinx.coroutines.flow.Flow<List<UsuarioEntity>>

    @Query("UPDATE usuarios SET revision_cuenta = :estado WHERE id = :usuarioId")
    suspend fun actualizarRevision(usuarioId: Long, estado: String)

    @Query(
        "UPDATE usuarios SET origen = '', validado = 0, solicita_validacion = 0, institucion_solicitada = NULL, " +
            "rol = 'usuario', educador_id = NULL, solicita_cambio_educador = 0 WHERE id = :usuarioId",
    )
    suspend fun quitarDeInstitucion(usuarioId: Long)

    @Query("UPDATE usuarios SET solicita_cambio_educador = :solicita WHERE id = :usuarioId")
    suspend fun actualizarSolicitaCambioEducador(usuarioId: Long, solicita: Boolean)

    @Query("UPDATE usuarios SET fecha_nacimiento = :fecha WHERE id = :usuarioId")
    suspend fun actualizarFechaNacimiento(usuarioId: Long, fecha: Long?)

    @Query("UPDATE usuarios SET email = :email WHERE id = :usuarioId")
    suspend fun actualizarEmail(usuarioId: Long, email: String?)

    @Query("UPDATE usuarios SET solicita_validacion = :solicita WHERE id = :usuarioId")
    suspend fun actualizarSolicitaValidacion(usuarioId: Long, solicita: Boolean)

    @Query("UPDATE usuarios SET solicita_baja = :solicita WHERE id = :usuarioId")
    suspend fun actualizarSolicitaBaja(usuarioId: Long, solicita: Boolean)

    @Query("UPDATE usuarios SET aviso_pendiente = :aviso WHERE id = :usuarioId")
    suspend fun actualizarAviso(usuarioId: Long, aviso: String?)

    @Query("UPDATE usuarios SET usar_apodo_ranking = :usar WHERE id = :usuarioId")
    suspend fun actualizarUsarApodoRanking(usuarioId: Long, usar: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(usuario: UsuarioEntity): Long
}

@Dao
interface ConfiguracionUsuarioDao {
    @Query("SELECT * FROM configuracion_usuario WHERE usuario_id = :usuarioId LIMIT 1")
    fun observeByUser(usuarioId: Long): Flow<ConfiguracionUsuarioEntity?>

    @Query("SELECT * FROM configuracion_usuario WHERE usuario_id = :usuarioId LIMIT 1")
    suspend fun findByUser(usuarioId: Long): ConfiguracionUsuarioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ConfiguracionUsuarioEntity)

    @Update
    suspend fun update(config: ConfiguracionUsuarioEntity)
}

@Dao
interface PermisosModuloDao {
    @Query("SELECT * FROM permisos_modulos WHERE usuario_id = :usuarioId")
    fun observeByUser(usuarioId: Long): Flow<List<PermisosModuloEntity>>

    @Query("SELECT COUNT(*) FROM permisos_modulos WHERE usuario_id = :usuarioId")
    suspend fun countByUser(usuarioId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(permisos: List<PermisosModuloEntity>)
}

/**
 * A problem reported with the app or with someone from the institution (alumno), or with no institution
 * at all (usuario independiente). [destinoOrigen] is where it should be read: an institution's name, or
 * null when the reporter has none — then it goes straight to the administradores instead of being lost,
 * per [com.cecapi.app.feature.modulo1_aplicacionprincipal.RolePermissions.destinoDeIncidencia].
 */
@Entity(
    tableName = "incidencias",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportante_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reportante_id"), Index("destino_origen")],
)
data class IncidenciaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "reportante_id")
    val reportanteId: Long,
    @ColumnInfo(name = "destino_origen")
    val destinoOrigen: String?,
    @ColumnInfo(name = "mensaje")
    val mensaje: String,
    @ColumnInfo(name = "fecha_reporte")
    val fechaReporte: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "resuelta")
    val resuelta: Boolean = false,
    /** Rol al que se le manda (EDUCADOR, DIRECTIVO, ADMINISTRADOR). Nulo en los reportes de antes. */
    @ColumnInfo(name = "destinatario_rol")
    val destinatarioRol: String? = null,
    @ColumnInfo(name = "respuesta")
    val respuesta: String? = null,
    /** Si es anónimo, la pantalla no muestra quién lo envió. */
    @ColumnInfo(name = "anonimo")
    val anonimo: Boolean = false,
)

@Entity(
    tableName = "contactos_emergencia",
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
data class ContactoEmergenciaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "nombre")
    val nombre: String,
    @ColumnInfo(name = "telefono")
    val telefono: String,
    @ColumnInfo(name = "relacion")
    val relacion: String? = null,
)

@Dao
interface ContactoEmergenciaDao {
    @Query("SELECT * FROM contactos_emergencia WHERE usuario_id = :usuarioId ORDER BY nombre")
    fun observeDeUsuario(usuarioId: Long): Flow<List<ContactoEmergenciaEntity>>

    @Insert
    suspend fun insert(contacto: ContactoEmergenciaEntity): Long

    @Query("DELETE FROM contactos_emergencia WHERE id = :id AND usuario_id = :usuarioId")
    suspend fun borrar(id: Long, usuarioId: Long)
}

@Dao
interface IncidenciaDao {
    @Insert
    suspend fun insert(incidencia: IncidenciaEntity): Long

    @Query("SELECT * FROM incidencias WHERE reportante_id = :usuarioId ORDER BY fecha_reporte DESC")
    fun observeDeReportante(usuarioId: Long): Flow<List<IncidenciaEntity>>

    /** Reportes dirigidos a un rol de la institución. Los de antes (sin destinatario) llegan a directivos, como antes. */
    @Query(
        "SELECT * FROM incidencias WHERE destino_origen = :origen COLLATE NOCASE " +
            "AND (destinatario_rol = :rol OR (destinatario_rol IS NULL AND :rol = 'directivo')) " +
            "ORDER BY fecha_reporte DESC",
    )
    fun observeParaRol(origen: String, rol: String): Flow<List<IncidenciaEntity>>

    /** For a directivo/educador's institution. */
    @Query("SELECT * FROM incidencias WHERE destino_origen = :origen COLLATE NOCASE ORDER BY fecha_reporte DESC")
    fun observeDeInstitucion(origen: String): Flow<List<IncidenciaEntity>>

    /** For an administrador: everyone's — both the institutions' and the independent usuarios' with no origen. */
    @Query("SELECT * FROM incidencias WHERE destinatario_rol = 'soporte' ORDER BY fecha_reporte DESC")
    fun observeSoporte(): Flow<List<IncidenciaEntity>>

    @Query("SELECT * FROM incidencias ORDER BY fecha_reporte DESC")
    fun observeTodas(): Flow<List<IncidenciaEntity>>

    @Query("UPDATE incidencias SET respuesta = :respuesta WHERE id = :id")
    suspend fun responder(id: Long, respuesta: String)

    @Query("DELETE FROM incidencias WHERE id = :id")
    suspend fun borrar(id: Long)

    @Query("UPDATE incidencias SET resuelta = 1 WHERE id = :id")
    suspend fun marcarResuelta(id: Long)
}

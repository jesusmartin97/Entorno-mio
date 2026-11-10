package com.cecapi.app.core.di

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cecapi.app.core.data.AppDatabase
import com.cecapi.app.core.data.MIGRATION_1_2
import com.cecapi.app.core.data.MIGRATION_2_3
import com.cecapi.app.core.data.MIGRATION_3_4
import com.cecapi.app.core.data.MIGRATION_4_5
import com.cecapi.app.core.data.MIGRATION_5_6
import com.cecapi.app.core.data.MIGRATION_6_7
import com.cecapi.app.core.data.MIGRATION_7_8
import com.cecapi.app.core.data.MIGRATION_8_9
import com.cecapi.app.core.data.MIGRATION_9_10
import com.cecapi.app.core.data.MIGRATION_10_11
import com.cecapi.app.core.data.MIGRATION_11_12
import com.cecapi.app.core.data.MIGRATION_12_13
import com.cecapi.app.core.data.MIGRATION_13_14
import com.cecapi.app.core.data.MIGRATION_14_15
import com.cecapi.app.core.data.MIGRATION_15_16
import com.cecapi.app.core.data.MIGRATION_16_17
import com.cecapi.app.core.util.PasswordHasher
import com.cecapi.app.feature.modulo3_asistenteinteligente.ConsultaIaDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.ContextoConversacionDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.RespuestaIaDao
import com.cecapi.app.feature.modulo4_lectordocumentos.DocumentoEscaneadoDao
import com.cecapi.app.feature.modulo4_lectordocumentos.HistorialLecturaDao
import com.cecapi.app.feature.modulo4_lectordocumentos.TextoExtraidoDao
import com.cecapi.app.feature.modulo7_entorno.DescripcionEntornoDao
import com.cecapi.app.feature.modulo7_entorno.EscaneoEntornoDao
import com.cecapi.app.feature.modulo7_entorno.ObjetoDetectadoDao
import com.cecapi.app.feature.modulo10_accesibilidad.GestoAccesibilidadDao
import com.cecapi.app.feature.modulo10_accesibilidad.PerfilAccesibilidadDao
import com.cecapi.app.feature.modulo6_aprendizaje.EjercicioDao
import com.cecapi.app.feature.modulo6_aprendizaje.EjercicioVibracionDao
import com.cecapi.app.feature.modulo6_aprendizaje.ResultadoVibracionDao
import com.cecapi.app.feature.modulo6_aprendizaje.NivelAprendizajeDao
import com.cecapi.app.feature.modulo6_aprendizaje.ResultadoEjercicioDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ConfiguracionUsuarioDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CambioCuentaDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ContactoEmergenciaDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.IncidenciaDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.RankingDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.PermisosModuloDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.RolUsuario
import com.cecapi.app.feature.modulo1_aplicacionprincipal.UsuarioDao
import com.cecapi.app.feature.modulo5_solicitudes.DatoSolicitudDao
import com.cecapi.app.feature.modulo5_solicitudes.PlantillaSolicitudDao
import com.cecapi.app.feature.modulo5_solicitudes.PlantillaSolicitudEntity
import com.cecapi.app.feature.modulo5_solicitudes.SolicitudGeneradaDao
import com.cecapi.app.feature.modulo2_asistentevoz.ComandoVozDao
import com.cecapi.app.feature.modulo2_asistentevoz.HistorialComandoDao
import com.cecapi.app.feature.modulo2_asistentevoz.RespuestaAuditivaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseProvider: Provider<AppDatabase>,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "cecapi.db")
        .addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17,
        )
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    seedDemoData(databaseProvider.get())
                }
            }

            // Runs on every open (fresh install and upgrade alike); it only adds the missing accounts.
            // It is synchronous on purpose: the accounts must exist before the first login attempt,
            // otherwise the very first "Ingresar" on a fresh install can fail for no visible reason.
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                ensureDemoUsers(db)
                ensureDemoPendingUser(db)
                ensureDemoRankingPoints(db)
                resetAllPasswordsOnce(context, db)
            }
        })
        .build()

    /**
     * One-time testing convenience, asked for directly: every account's password becomes "1234", once.
     * Guarded by a SharedPreferences flag so it runs exactly once and never again — otherwise it would
     * silently undo anyone's real password change forever, every time the app opens.
     */
    private fun resetAllPasswordsOnce(context: Context, db: SupportSQLiteDatabase) {
        if (db.isReadOnly) return
        val prefs = context.getSharedPreferences("mantenimiento_bd", Context.MODE_PRIVATE)
        if (prefs.getBoolean("contrasenas_reiniciadas_1234", false)) return
        db.execSQL("UPDATE usuarios SET contrasena_hash = ?", arrayOf<Any?>(PasswordHasher.hash("1234")))
        prefs.edit().putBoolean("contrasenas_reiniciadas_1234", true).apply()
    }

    private class DemoUser(
        val nombreUsuario: String,
        val contrasena: String,
        val nombreCompleto: String,
        val rol: RolUsuario,
        val origen: String,
        /** For a demo alumno: the username of the educador demo account that teaches them. */
        val educadorUsername: String? = null,
    )

    /**
     * Demo accounts, one per role. Temporary: real accounts will come from the institution.
     * CECAPI is the shared test account so testers do not start with a personal one.
     * The administrator is PEPE (not "PP") because voice recognition writes it that way.
     */
    private val demoUsers = listOf(
        DemoUser("CECAPI", "1234", "Usuario CECAPI", RolUsuario.ADMINISTRADOR, "admin"),
        DemoUser("PEPE", "1234", "Pepe", RolUsuario.ADMINISTRADOR, "admin"),
        DemoUser("JORGE", "4321", "Jorge", RolUsuario.DIRECTIVO, "CECAPI"),
        DemoUser("MIRIAM", "1234", "Miriam", RolUsuario.EDUCADOR, "CECAPI"),
        DemoUser("JUAN", "1234", "Juan", RolUsuario.ALUMNO, "CECAPI", educadorUsername = "MIRIAM"),
    )

    private fun ensureDemoUsers(db: SupportSQLiteDatabase) {
        if (db.isReadOnly) return
        demoUsers.forEach { demo ->
            val exists = db.query(
                "SELECT 1 FROM usuarios WHERE nombre_usuario = ? COLLATE NOCASE LIMIT 1",
                arrayOf<Any?>(demo.nombreUsuario),
            ).use { it.moveToFirst() }
            if (!exists) {
                db.insert(
                    "usuarios",
                    SQLiteDatabase.CONFLICT_IGNORE,
                    ContentValues().apply {
                        put("nombre_usuario", demo.nombreUsuario)
                        put("contrasena_hash", PasswordHasher.hash(demo.contrasena))
                        put("nombre_completo", demo.nombreCompleto)
                        put("fecha_registro", System.currentTimeMillis())
                        put("rol", demo.rol.codigo)
                        put("origen", demo.origen)
                        put("validado", 1) // cuentas de prueba: ya confiables, no quedan pendientes de validar
                    },
                )
            }
            // Runs every time too (not just on insert), so an account made before "educador" existed
            // still ends up linked once its teacher's demo account is created.
            if (demo.educadorUsername != null) {
                db.execSQL(
                    "UPDATE usuarios SET educador_id = " +
                        "(SELECT id FROM usuarios WHERE nombre_usuario = ? COLLATE NOCASE LIMIT 1) " +
                        "WHERE nombre_usuario = ? COLLATE NOCASE",
                    arrayOf<Any?>(demo.educadorUsername, demo.nombreUsuario),
                )
            }
        }
    }

    /**
     * One demo account left unvalidated on purpose, so Gestión always has something to validate
     * when testing on a real phone (otherwise the "pendientes" list looks empty forever).
     */
    private fun ensureDemoPendingUser(db: SupportSQLiteDatabase) {
        if (db.isReadOnly) return
        val username = "LUPITA"
        val exists = db.query(
            "SELECT 1 FROM usuarios WHERE nombre_usuario = ? COLLATE NOCASE LIMIT 1",
            arrayOf<Any?>(username),
        ).use { it.moveToFirst() }
        if (exists) return
        db.insert(
            "usuarios",
            SQLiteDatabase.CONFLICT_IGNORE,
            ContentValues().apply {
                put("nombre_usuario", username)
                put("contrasena_hash", PasswordHasher.hash("1234"))
                put("nombre_completo", "Lupita (pendiente de validar)")
                put("fecha_registro", System.currentTimeMillis())
                put("rol", RolUsuario.USUARIO.codigo)
                put("origen", "CECAPI")
                put("validado", 0)
            },
        )
    }

    /**
     * Ranking points for two demo accounts, so Clasificación shows real rows (individual and por
     * institución) instead of the empty state on a fresh install.
     */
    private fun ensureDemoRankingPoints(db: SupportSQLiteDatabase) {
        if (db.isReadOnly) return
        val puntosPorUsuario = mapOf("JUAN" to (3 to 450), "MIRIAM" to (5 to 820))
        puntosPorUsuario.forEach { (username, nivelYPuntos) ->
            val (nivel, puntos) = nivelYPuntos
            db.execSQL(
                "INSERT INTO niveles_aprendizaje (usuario_id, nivel_actual, puntos_totales, fecha_actualizacion) " +
                    "SELECT id, ?, ?, ? FROM usuarios WHERE nombre_usuario = ? COLLATE NOCASE " +
                    "AND NOT EXISTS (SELECT 1 FROM niveles_aprendizaje WHERE usuario_id = usuarios.id)",
                arrayOf<Any?>(nivel, puntos, System.currentTimeMillis(), username),
            )
        }
    }

    private suspend fun seedDemoData(database: AppDatabase) {
        database.plantillaSolicitudDao().insertAll(
            listOf(
                PlantillaSolicitudEntity(
                    titulo = "Constancia de estudios",
                    descripcion = "Para trámites que requieren comprobar que estudias en CECAPI.",
                    cuerpoPlantilla = "Solicito una constancia de estudios a nombre de {nombre_completo}, " +
                        "con fecha {fecha}, para el siguiente motivo: {motivo}.",
                ),
                PlantillaSolicitudEntity(
                    titulo = "Justificación de inasistencia",
                    descripcion = "Para justificar una falta ante CECAPI.",
                    cuerpoPlantilla = "Yo, {nombre_completo}, justifico mi inasistencia del día {fecha} " +
                        "por el siguiente motivo: {motivo}.",
                ),
                PlantillaSolicitudEntity(
                    titulo = "Solicitud de apoyo",
                    descripcion = "Para pedir apoyo o material a CECAPI.",
                    cuerpoPlantilla = "Yo, {nombre_completo}, solicito apoyo con: {motivo}. Fecha: {fecha}.",
                ),
            ),
        )
    }

    @Provides
    fun provideUsuarioDao(db: AppDatabase): UsuarioDao = db.usuarioDao()

    @Provides
    fun provideConfiguracionUsuarioDao(db: AppDatabase): ConfiguracionUsuarioDao = db.configuracionUsuarioDao()

    @Provides
    fun providePermisosModuloDao(db: AppDatabase): PermisosModuloDao = db.permisosModuloDao()

    @Provides
    fun provideIncidenciaDao(db: AppDatabase): IncidenciaDao = db.incidenciaDao()

    @Provides
    fun provideCambioCuentaDao(db: AppDatabase): CambioCuentaDao = db.cambioCuentaDao()

    @Provides
    fun provideContactoEmergenciaDao(db: AppDatabase): ContactoEmergenciaDao = db.contactoEmergenciaDao()

    @Provides
    fun provideRankingDao(db: AppDatabase): RankingDao = db.rankingDao()

    @Provides
    fun provideComandoVozDao(db: AppDatabase): ComandoVozDao = db.comandoVozDao()

    @Provides
    fun provideHistorialComandoDao(db: AppDatabase): HistorialComandoDao = db.historialComandoDao()

    @Provides
    fun provideRespuestaAuditivaDao(db: AppDatabase): RespuestaAuditivaDao = db.respuestaAuditivaDao()

    @Provides
    fun provideConsultaIaDao(db: AppDatabase): ConsultaIaDao = db.consultaIaDao()

    @Provides
    fun provideRespuestaIaDao(db: AppDatabase): RespuestaIaDao = db.respuestaIaDao()

    @Provides
    fun provideContextoConversacionDao(db: AppDatabase): ContextoConversacionDao = db.contextoConversacionDao()

    @Provides
    fun provideDocumentoEscaneadoDao(db: AppDatabase): DocumentoEscaneadoDao = db.documentoEscaneadoDao()

    @Provides
    fun provideTextoExtraidoDao(db: AppDatabase): TextoExtraidoDao = db.textoExtraidoDao()

    @Provides
    fun provideHistorialLecturaDao(db: AppDatabase): HistorialLecturaDao = db.historialLecturaDao()

    @Provides
    fun providePlantillaSolicitudDao(db: AppDatabase): PlantillaSolicitudDao = db.plantillaSolicitudDao()

    @Provides
    fun provideSolicitudGeneradaDao(db: AppDatabase): SolicitudGeneradaDao = db.solicitudGeneradaDao()

    @Provides
    fun provideDatoSolicitudDao(db: AppDatabase): DatoSolicitudDao = db.datoSolicitudDao()

    @Provides
    fun provideEjercicioDao(db: AppDatabase): EjercicioDao = db.ejercicioDao()

    @Provides
    fun provideResultadoEjercicioDao(db: AppDatabase): ResultadoEjercicioDao = db.resultadoEjercicioDao()

    @Provides
    fun provideNivelAprendizajeDao(db: AppDatabase): NivelAprendizajeDao = db.nivelAprendizajeDao()

    @Provides
    fun provideEjercicioVibracionDao(db: AppDatabase): EjercicioVibracionDao = db.ejercicioVibracionDao()

    @Provides
    fun provideResultadoVibracionDao(db: AppDatabase): ResultadoVibracionDao = db.resultadoVibracionDao()

    @Provides
    fun provideEscaneoEntornoDao(db: AppDatabase): EscaneoEntornoDao = db.escaneoEntornoDao()

    @Provides
    fun provideObjetoDetectadoDao(db: AppDatabase): ObjetoDetectadoDao = db.objetoDetectadoDao()

    @Provides
    fun provideDescripcionEntornoDao(db: AppDatabase): DescripcionEntornoDao = db.descripcionEntornoDao()

    @Provides
    fun providePerfilAccesibilidadDao(db: AppDatabase): PerfilAccesibilidadDao = db.perfilAccesibilidadDao()

    @Provides
    fun provideGestoAccesibilidadDao(db: AppDatabase): GestoAccesibilidadDao = db.gestoAccesibilidadDao()
}

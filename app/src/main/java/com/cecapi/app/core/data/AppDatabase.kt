package com.cecapi.app.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cecapi.app.feature.modulo3_asistenteinteligente.ConsultaIaDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.ConsultaIaEntity
import com.cecapi.app.feature.modulo3_asistenteinteligente.ContextoConversacionDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.ContextoConversacionEntity
import com.cecapi.app.feature.modulo3_asistenteinteligente.RespuestaIaDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.RespuestaIaEntity
import com.cecapi.app.feature.modulo4_lectordocumentos.DocumentoEscaneadoDao
import com.cecapi.app.feature.modulo4_lectordocumentos.DocumentoEscaneadoEntity
import com.cecapi.app.feature.modulo4_lectordocumentos.HistorialLecturaDao
import com.cecapi.app.feature.modulo4_lectordocumentos.HistorialLecturaEntity
import com.cecapi.app.feature.modulo4_lectordocumentos.TextoExtraidoDao
import com.cecapi.app.feature.modulo4_lectordocumentos.TextoExtraidoEntity
import com.cecapi.app.feature.modulo7_entorno.DescripcionEntornoDao
import com.cecapi.app.feature.modulo7_entorno.DescripcionEntornoEntity
import com.cecapi.app.feature.modulo7_entorno.EscaneoEntornoDao
import com.cecapi.app.feature.modulo7_entorno.EscaneoEntornoEntity
import com.cecapi.app.feature.modulo7_entorno.ObjetoDetectadoDao
import com.cecapi.app.feature.modulo7_entorno.ObjetoDetectadoEntity
import com.cecapi.app.feature.modulo10_accesibilidad.AccionEntity
import com.cecapi.app.feature.modulo10_accesibilidad.GestoAccesibilidadDao
import com.cecapi.app.feature.modulo10_accesibilidad.GestoEntity
import com.cecapi.app.feature.modulo10_accesibilidad.PerfilAccesibilidadDao
import com.cecapi.app.feature.modulo10_accesibilidad.PerfilAccesibilidadEntity
import com.cecapi.app.feature.modulo6_aprendizaje.EjercicioDao
import com.cecapi.app.feature.modulo6_aprendizaje.EjercicioEntity
import com.cecapi.app.feature.modulo6_aprendizaje.EjercicioVibracionDao
import com.cecapi.app.feature.modulo6_aprendizaje.EjercicioVibracionEntity
import com.cecapi.app.feature.modulo6_aprendizaje.ResultadoVibracionDao
import com.cecapi.app.feature.modulo6_aprendizaje.ResultadoVibracionEntity
import com.cecapi.app.feature.modulo6_aprendizaje.NivelAprendizajeDao
import com.cecapi.app.feature.modulo6_aprendizaje.NivelAprendizajeEntity
import com.cecapi.app.feature.modulo6_aprendizaje.ResultadoEjercicioDao
import com.cecapi.app.feature.modulo6_aprendizaje.ResultadoEjercicioEntity
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ConfiguracionUsuarioDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ConfiguracionUsuarioEntity
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CambioCuentaDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CambioCuentaEntity
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ContactoEmergenciaDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ContactoEmergenciaEntity
import com.cecapi.app.feature.modulo1_aplicacionprincipal.IncidenciaDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.IncidenciaEntity
import com.cecapi.app.feature.modulo1_aplicacionprincipal.PermisosModuloDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.RankingDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.PermisosModuloEntity
import com.cecapi.app.feature.modulo1_aplicacionprincipal.UsuarioDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.UsuarioEntity
import com.cecapi.app.feature.modulo5_solicitudes.DatoSolicitudDao
import com.cecapi.app.feature.modulo5_solicitudes.DatoSolicitudEntity
import com.cecapi.app.feature.modulo5_solicitudes.PlantillaSolicitudDao
import com.cecapi.app.feature.modulo5_solicitudes.PlantillaSolicitudEntity
import com.cecapi.app.feature.modulo5_solicitudes.SolicitudGeneradaDao
import com.cecapi.app.feature.modulo5_solicitudes.SolicitudGeneradaEntity
import com.cecapi.app.feature.modulo2_asistentevoz.ComandoVozDao
import com.cecapi.app.feature.modulo2_asistentevoz.ComandoVozEntity
import com.cecapi.app.feature.modulo2_asistentevoz.HistorialComandoDao
import com.cecapi.app.feature.modulo2_asistentevoz.HistorialComandoEntity
import com.cecapi.app.feature.modulo2_asistentevoz.RespuestaAuditivaDao
import com.cecapi.app.feature.modulo2_asistentevoz.RespuestaAuditivaEntity

/** Single Room database for the whole app; each module owns its own tables. */
@Database(
    entities = [
        // Módulo 1 — Aplicación Principal Accesible
        UsuarioEntity::class,
        ConfiguracionUsuarioEntity::class,
        PermisosModuloEntity::class,
        IncidenciaEntity::class,
        CambioCuentaEntity::class,
        ContactoEmergenciaEntity::class,
        // Módulo 2 — Asistente de Voz
        ComandoVozEntity::class,
        HistorialComandoEntity::class,
        RespuestaAuditivaEntity::class,
        // Módulo 3 — Asistente Inteligente
        ConsultaIaEntity::class,
        RespuestaIaEntity::class,
        ContextoConversacionEntity::class,
        // Módulo 4 — Lector Inteligente de Documentos
        DocumentoEscaneadoEntity::class,
        TextoExtraidoEntity::class,
        HistorialLecturaEntity::class,
        // Módulo 5 — Centro de Solicitudes
        PlantillaSolicitudEntity::class,
        SolicitudGeneradaEntity::class,
        DatoSolicitudEntity::class,
        // Módulo 6 — Centro de Aprendizaje
        EjercicioEntity::class,
        ResultadoEjercicioEntity::class,
        NivelAprendizajeEntity::class,
        EjercicioVibracionEntity::class,
        ResultadoVibracionEntity::class,
        // Módulo 7 — Asistente del Entorno
        EscaneoEntornoEntity::class,
        ObjetoDetectadoEntity::class,
        DescripcionEntornoEntity::class,
        // Módulo 10 (v1.5) — Accesibilidad: perfiles, gestos y acciones (Equipo 5)
        PerfilAccesibilidadEntity::class,
        GestoEntity::class,
        AccionEntity::class,
    ],
    version = 17,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao
    abstract fun configuracionUsuarioDao(): ConfiguracionUsuarioDao
    abstract fun permisosModuloDao(): PermisosModuloDao
    abstract fun incidenciaDao(): IncidenciaDao
    abstract fun cambioCuentaDao(): CambioCuentaDao
    abstract fun contactoEmergenciaDao(): ContactoEmergenciaDao
    abstract fun rankingDao(): RankingDao

    abstract fun comandoVozDao(): ComandoVozDao
    abstract fun historialComandoDao(): HistorialComandoDao
    abstract fun respuestaAuditivaDao(): RespuestaAuditivaDao

    abstract fun consultaIaDao(): ConsultaIaDao
    abstract fun respuestaIaDao(): RespuestaIaDao
    abstract fun contextoConversacionDao(): ContextoConversacionDao

    abstract fun documentoEscaneadoDao(): DocumentoEscaneadoDao
    abstract fun textoExtraidoDao(): TextoExtraidoDao
    abstract fun historialLecturaDao(): HistorialLecturaDao

    abstract fun plantillaSolicitudDao(): PlantillaSolicitudDao
    abstract fun solicitudGeneradaDao(): SolicitudGeneradaDao
    abstract fun datoSolicitudDao(): DatoSolicitudDao

    abstract fun ejercicioDao(): EjercicioDao
    abstract fun resultadoEjercicioDao(): ResultadoEjercicioDao
    abstract fun nivelAprendizajeDao(): NivelAprendizajeDao
    abstract fun ejercicioVibracionDao(): EjercicioVibracionDao
    abstract fun resultadoVibracionDao(): ResultadoVibracionDao

    abstract fun escaneoEntornoDao(): EscaneoEntornoDao
    abstract fun objetoDetectadoDao(): ObjetoDetectadoDao
    abstract fun descripcionEntornoDao(): DescripcionEntornoDao

    abstract fun perfilAccesibilidadDao(): PerfilAccesibilidadDao
    abstract fun gestoAccesibilidadDao(): GestoAccesibilidadDao
}

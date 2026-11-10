-- =============================================================================
-- CECAPI - Sistema de Asistencia Auditiva
-- Script de base de datos completo (dialecto SQLite / compatible con Room)
--
-- Estrategia: "mixta" (sección 9 de la propuesta v2): cada equipo documenta y
-- defiende sus propias 3 tablas, pero todas viven en una sola base de datos
-- central para evitar duplicar usuarios, historial y configuraciones.
--
-- Los módulos 1-7 (versión 1.0) están implementados como entidades Room reales
-- en app/src/main/java/com/cecapi/app/feature/**/*.kt — estas definiciones son
-- funcionalmente equivalentes al esquema autogenerado por Room en tiempo de
-- ejecución (mismos nombres de tabla y columna en snake_case).
--
-- Los módulos 8-14 (versión 1.5) están documentados aquí para que los equipos
-- que se integren después tengan su esquema listo desde el día uno, tal como
-- pide la sección 6 de la propuesta ("permitir que nuevos equipos se
-- incorporen ... sin afectar el núcleo principal").
-- =============================================================================

PRAGMA foreign_keys = ON;

-- =============================================================================
-- MÓDULO 1: Aplicación Principal Accesible (v1.0)
-- =============================================================================

CREATE TABLE usuarios (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre_usuario      TEXT NOT NULL UNIQUE,
    contrasena_hash     TEXT NOT NULL,
    nombre_completo     TEXT NOT NULL,
    fecha_registro      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

CREATE TABLE configuracion_usuario (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL UNIQUE,
    velocidad_voz       REAL NOT NULL DEFAULT 1.0,
    volumen             REAL NOT NULL DEFAULT 1.0,
    modo_simple         INTEGER NOT NULL DEFAULT 1, -- boolean: 0/1
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE permisos_modulos (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    modulo_codigo       TEXT NOT NULL, -- VOICE_ASSISTANT, AI_ASSISTANT, DOCUMENT_READER, REQUESTS, LEARNING, ENVIRONMENT
    habilitado          INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_permisos_modulos_usuario ON permisos_modulos(usuario_id);

-- =============================================================================
-- MÓDULO 2: Asistente de Voz (v1.0)
-- =============================================================================

CREATE TABLE comandos_voz (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    texto_comando       TEXT NOT NULL,
    fecha_hora          INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_comandos_voz_usuario ON comandos_voz(usuario_id);

CREATE TABLE historial_comandos (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    comando_id          INTEGER NOT NULL,
    modulo_destino      TEXT,          -- NULL si el comando no se reconoció
    resultado           TEXT NOT NULL, -- 'ejecutado' | 'no_reconocido'
    FOREIGN KEY (comando_id) REFERENCES comandos_voz(id) ON DELETE CASCADE
);
CREATE INDEX idx_historial_comandos_comando ON historial_comandos(comando_id);

CREATE TABLE respuestas_auditivas (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    comando_id          INTEGER NOT NULL,
    texto_respuesta     TEXT NOT NULL,
    fecha_hora          INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (comando_id) REFERENCES comandos_voz(id) ON DELETE CASCADE
);
CREATE INDEX idx_respuestas_auditivas_comando ON respuestas_auditivas(comando_id);

-- =============================================================================
-- MÓDULO 3: Asistente Inteligente (v1.0)
-- =============================================================================

CREATE TABLE consultas_ia (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    texto_pregunta      TEXT NOT NULL,
    fecha_hora          INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_consultas_ia_usuario ON consultas_ia(usuario_id);

CREATE TABLE respuestas_ia (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    consulta_id         INTEGER NOT NULL,
    texto_respuesta     TEXT NOT NULL,
    fecha_hora          INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (consulta_id) REFERENCES consultas_ia(id) ON DELETE CASCADE
);
CREATE INDEX idx_respuestas_ia_consulta ON respuestas_ia(consulta_id);

CREATE TABLE contextos_conversacion (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    resumen_contexto    TEXT NOT NULL,
    fecha_actualizacion INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_contextos_conversacion_usuario ON contextos_conversacion(usuario_id);

-- =============================================================================
-- MÓDULO 4: Lector Inteligente de Documentos (v1.0)
-- =============================================================================

CREATE TABLE documentos_escaneados (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    ruta_imagen         TEXT NOT NULL,
    fecha_captura       INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_documentos_escaneados_usuario ON documentos_escaneados(usuario_id);

CREATE TABLE textos_extraidos (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    documento_id        INTEGER NOT NULL UNIQUE,
    texto_completo      TEXT NOT NULL,
    fecha_extraccion    INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (documento_id) REFERENCES documentos_escaneados(id) ON DELETE CASCADE
);

CREATE TABLE historial_lecturas (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    documento_id        INTEGER NOT NULL,
    fecha_lectura       INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (documento_id) REFERENCES documentos_escaneados(id) ON DELETE CASCADE
);
CREATE INDEX idx_historial_lecturas_documento ON historial_lecturas(documento_id);

-- =============================================================================
-- MÓDULO 5: Centro de Solicitudes (v1.0)
-- =============================================================================

CREATE TABLE plantillas_solicitud (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    titulo              TEXT NOT NULL,
    descripcion         TEXT NOT NULL,
    cuerpo_plantilla    TEXT NOT NULL -- usa marcadores {nombre_variable}
);

CREATE TABLE solicitudes_generadas (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    plantilla_id        INTEGER NOT NULL,
    texto_final         TEXT NOT NULL,
    fecha_generacion    INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (plantilla_id) REFERENCES plantillas_solicitud(id) ON DELETE CASCADE
);
CREATE INDEX idx_solicitudes_generadas_usuario ON solicitudes_generadas(usuario_id);
CREATE INDEX idx_solicitudes_generadas_plantilla ON solicitudes_generadas(plantilla_id);

CREATE TABLE datos_solicitud (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    solicitud_id        INTEGER NOT NULL,
    clave               TEXT NOT NULL,
    valor               TEXT NOT NULL,
    FOREIGN KEY (solicitud_id) REFERENCES solicitudes_generadas(id) ON DELETE CASCADE
);
CREATE INDEX idx_datos_solicitud_solicitud ON datos_solicitud(solicitud_id);

-- =============================================================================
-- MÓDULO 6: Centro de Aprendizaje (v1.0)
-- =============================================================================

CREATE TABLE ejercicios (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    titulo              TEXT NOT NULL,
    instruccion         TEXT NOT NULL,
    respuesta_correcta  TEXT NOT NULL,
    nivel               INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE resultados_ejercicios (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    ejercicio_id        INTEGER NOT NULL,
    fue_correcto        INTEGER NOT NULL, -- boolean: 0/1
    fecha_realizado      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (ejercicio_id) REFERENCES ejercicios(id) ON DELETE CASCADE
);
CREATE INDEX idx_resultados_ejercicios_usuario ON resultados_ejercicios(usuario_id);
CREATE INDEX idx_resultados_ejercicios_ejercicio ON resultados_ejercicios(ejercicio_id);

CREATE TABLE niveles_aprendizaje (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL UNIQUE,
    nivel_actual        INTEGER NOT NULL DEFAULT 1,
    puntos_totales      INTEGER NOT NULL DEFAULT 0,
    fecha_actualizacion INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- =============================================================================
-- MÓDULO 7: Asistente del Entorno (v1.0)
-- =============================================================================

CREATE TABLE escaneos_entorno (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    ruta_imagen         TEXT NOT NULL,
    fecha_escaneo       INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_escaneos_entorno_usuario ON escaneos_entorno(usuario_id);

CREATE TABLE objetos_detectados (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    escaneo_id          INTEGER NOT NULL,
    etiqueta            TEXT NOT NULL,
    confianza           REAL NOT NULL,
    FOREIGN KEY (escaneo_id) REFERENCES escaneos_entorno(id) ON DELETE CASCADE
);
CREATE INDEX idx_objetos_detectados_escaneo ON objetos_detectados(escaneo_id);

CREATE TABLE descripciones_entorno (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    escaneo_id          INTEGER NOT NULL UNIQUE,
    texto_descripcion   TEXT NOT NULL,
    fecha_generacion    INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (escaneo_id) REFERENCES escaneos_entorno(id) ON DELETE CASCADE
);

-- =============================================================================
-- MÓDULO 8 (v1.5): Historial y Actividad del Usuario
-- Aún no implementado en la app; esquema listo para el equipo que lo tome.
-- =============================================================================

CREATE TABLE tipos_actividad (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo              TEXT NOT NULL UNIQUE, -- 'LECTURA', 'SOLICITUD', 'COMANDO_VOZ', 'EJERCICIO'
    descripcion         TEXT NOT NULL
);

CREATE TABLE actividades_usuario (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    tipo_actividad_id   INTEGER NOT NULL,
    referencia_id       INTEGER, -- id de la tabla origen (documento, solicitud, comando, ejercicio)
    fecha_hora          INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (tipo_actividad_id) REFERENCES tipos_actividad(id) ON DELETE CASCADE
);
CREATE INDEX idx_actividades_usuario_usuario ON actividades_usuario(usuario_id);

CREATE TABLE resumen_uso (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL UNIQUE,
    total_actividades   INTEGER NOT NULL DEFAULT 0,
    ultima_actividad    INTEGER,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- =============================================================================
-- MÓDULO 9 (v1.5): Tutoriales Auditivos
-- =============================================================================

CREATE TABLE tutoriales (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    titulo              TEXT NOT NULL,
    descripcion         TEXT NOT NULL,
    modulo_relacionado  TEXT -- código de ModuloCecapi que enseña, o NULL si es general
);

CREATE TABLE pasos_tutorial (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    tutorial_id         INTEGER NOT NULL,
    orden               INTEGER NOT NULL,
    texto_instruccion   TEXT NOT NULL,
    FOREIGN KEY (tutorial_id) REFERENCES tutoriales(id) ON DELETE CASCADE
);
CREATE INDEX idx_pasos_tutorial_tutorial ON pasos_tutorial(tutorial_id);

CREATE TABLE progreso_tutorial (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    tutorial_id         INTEGER NOT NULL,
    paso_actual         INTEGER NOT NULL DEFAULT 0,
    completado          INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (tutorial_id) REFERENCES tutoriales(id) ON DELETE CASCADE
);
CREATE INDEX idx_progreso_tutorial_usuario ON progreso_tutorial(usuario_id);

-- =============================================================================
-- MÓDULO 10 (v1.5): Configuración Accesible Avanzada
-- =============================================================================

CREATE TABLE perfiles_accesibilidad (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre              TEXT NOT NULL, -- 'Baja visión', 'Ceguera total', 'Adulto mayor', ...
    descripcion         TEXT NOT NULL
);

CREATE TABLE preferencias_accesibilidad (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL UNIQUE,
    perfil_id           INTEGER,
    tamano_texto        REAL NOT NULL DEFAULT 1.0,
    contraste_alto      INTEGER NOT NULL DEFAULT 0,
    vibracion_activa    INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (perfil_id) REFERENCES perfiles_accesibilidad(id) ON DELETE SET NULL
);

CREATE TABLE ajustes_audio (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL UNIQUE,
    velocidad_voz       REAL NOT NULL DEFAULT 1.0,
    volumen             REAL NOT NULL DEFAULT 1.0,
    tono_voz            TEXT NOT NULL DEFAULT 'neutro',
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- =============================================================================
-- MÓDULO 11 (v1.5): Biblioteca de Recursos CECAPI
-- =============================================================================

CREATE TABLE categorias_recursos (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre              TEXT NOT NULL UNIQUE
);

CREATE TABLE recursos (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    categoria_id        INTEGER NOT NULL,
    titulo              TEXT NOT NULL,
    tipo                TEXT NOT NULL, -- 'audio', 'documento', 'guia'
    ruta_archivo        TEXT NOT NULL,
    FOREIGN KEY (categoria_id) REFERENCES categorias_recursos(id) ON DELETE CASCADE
);
CREATE INDEX idx_recursos_categoria ON recursos(categoria_id);

CREATE TABLE favoritos_usuario (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    recurso_id          INTEGER NOT NULL,
    fecha_agregado      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (recurso_id) REFERENCES recursos(id) ON DELETE CASCADE
);
CREATE INDEX idx_favoritos_usuario_usuario ON favoritos_usuario(usuario_id);

-- =============================================================================
-- MÓDULO 12 (v1.5): Recordatorios Accesibles
-- =============================================================================

CREATE TABLE tipos_recordatorio (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo              TEXT NOT NULL UNIQUE, -- 'CITA', 'MEDICAMENTO', 'DOCUMENTO', 'VISITA'
    descripcion         TEXT NOT NULL
);

CREATE TABLE recordatorios (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    tipo_recordatorio_id INTEGER NOT NULL,
    titulo              TEXT NOT NULL,
    fecha_hora_objetivo INTEGER NOT NULL,
    completado          INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (tipo_recordatorio_id) REFERENCES tipos_recordatorio(id) ON DELETE CASCADE
);
CREATE INDEX idx_recordatorios_usuario ON recordatorios(usuario_id);

CREATE TABLE notificaciones_recordatorio (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    recordatorio_id     INTEGER NOT NULL,
    fecha_envio         INTEGER NOT NULL,
    fue_leida           INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (recordatorio_id) REFERENCES recordatorios(id) ON DELETE CASCADE
);
CREATE INDEX idx_notificaciones_recordatorio_recordatorio ON notificaciones_recordatorio(recordatorio_id);

-- =============================================================================
-- MÓDULO 13 (v1.5): Modo Administrador Institucional
-- =============================================================================

CREATE TABLE usuarios_institucion (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL UNIQUE,
    puesto              TEXT NOT NULL, -- 'docente', 'psicólogo', 'coordinador', ...
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE administradores (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_institucion_id INTEGER NOT NULL UNIQUE,
    nivel_acceso        TEXT NOT NULL DEFAULT 'basico', -- 'basico' | 'completo'
    FOREIGN KEY (usuario_institucion_id) REFERENCES usuarios_institucion(id) ON DELETE CASCADE
);

CREATE TABLE acciones_administrador (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    administrador_id    INTEGER NOT NULL,
    accion              TEXT NOT NULL, -- 'crear_plantilla', 'deshabilitar_modulo', 'crear_usuario', ...
    detalle             TEXT,
    fecha_hora          INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (administrador_id) REFERENCES administradores(id) ON DELETE CASCADE
);
CREATE INDEX idx_acciones_administrador_administrador ON acciones_administrador(administrador_id);

-- =============================================================================
-- MÓDULO 14 (v1.5): Reportes Simples de Uso
-- =============================================================================

CREATE TABLE reportes (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    titulo              TEXT NOT NULL,
    fecha_generacion    INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    periodo_inicio      INTEGER NOT NULL,
    periodo_fin         INTEGER NOT NULL
);

CREATE TABLE metricas_uso (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    reporte_id          INTEGER NOT NULL,
    nombre_metrica      TEXT NOT NULL, -- 'usuarios_activos', 'solicitudes_generadas', ...
    valor               REAL NOT NULL,
    FOREIGN KEY (reporte_id) REFERENCES reportes(id) ON DELETE CASCADE
);
CREATE INDEX idx_metricas_uso_reporte ON metricas_uso(reporte_id);

CREATE TABLE modulos_consultados (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    reporte_id          INTEGER NOT NULL,
    modulo_codigo       TEXT NOT NULL,
    veces_usado         INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (reporte_id) REFERENCES reportes(id) ON DELETE CASCADE
);
CREATE INDEX idx_modulos_consultados_reporte ON modulos_consultados(reporte_id);

-- =============================================================================
-- Datos de demostración (coinciden con el seed de DatabaseModule.kt)
-- =============================================================================

INSERT INTO usuarios (nombre_usuario, contrasena_hash, nombre_completo) VALUES
    ('cecapi', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f', 'Usuario CECAPI');
    -- El hash de arriba es SHA-256("1234"), igual que PasswordHasher.hash("1234") en la app.

package com.cecapi.app.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2: users get a role and an origin/institution, and usernames become case-insensitive (UPPERCASE). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN rol TEXT NOT NULL DEFAULT 'usuario'")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN origen TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE usuarios SET nombre_usuario = UPPER(nombre_usuario)")
        // The shared test account that already existed becomes an administrator.
        db.execSQL("UPDATE usuarios SET rol = 'administrador', origen = 'admin' WHERE nombre_usuario = 'CECAPI'")
    }
}

/**
 * v3: the audio exercises of Módulo 6 now carry a sound file and the volume of each ear, and the vibration
 * exercises get their own tables. The old voice-command exercises are dropped (their results go with them,
 * by cascade); the new bank is created the first time Actividades opens.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM resultados_ejercicios")
        db.execSQL("DELETE FROM ejercicios")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN archivo_sonido TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_izq_inicio REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_der_inicio REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_izq_fin REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_der_fin REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN duracion_ms INTEGER NOT NULL DEFAULT 1500")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ejercicios_vibracion (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "titulo TEXT NOT NULL, instruccion TEXT NOT NULL, respuesta_correcta TEXT NOT NULL, " +
                "nivel INTEGER NOT NULL, patron_ms TEXT NOT NULL, intensidad INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS resultados_vibracion (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "usuario_id INTEGER NOT NULL, ejercicio_vibracion_id INTEGER NOT NULL, " +
                "fue_correcto INTEGER NOT NULL, fecha_realizado INTEGER NOT NULL, " +
                "FOREIGN KEY(usuario_id) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(ejercicio_vibracion_id) REFERENCES ejercicios_vibracion(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_resultados_vibracion_usuario_id ON resultados_vibracion (usuario_id)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_resultados_vibracion_ejercicio_vibracion_id " +
                "ON resultados_vibracion (ejercicio_vibracion_id)",
        )
    }
}

/** v4: roles get educador, and an alumno can be linked to the educador who teaches them. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN educador_id INTEGER")
    }
}

/** v5: a simple way for an alumno or an independent usuario to report a problem. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS incidencias (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "reportante_id INTEGER NOT NULL, destino_origen TEXT, mensaje TEXT NOT NULL, " +
                "fecha_reporte INTEGER NOT NULL, resuelta INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(reportante_id) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incidencias_reportante_id ON incidencias (reportante_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incidencias_destino_origen ON incidencias (destino_origen)")
    }
}

/** v6: an optional nickname, shown on the ranking instead of the real name (there are minors). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN apodo TEXT")
    }
}

/**
 * v7: who in the chain above an account (administrador > directivo > educador > alumno, or
 * administrador > usuario) still needs to review it. Defaults new column to true so every account that
 * already existed before this migration is not retroactively flagged pending; only accounts created from
 * here on default to false (SessionRepository.register sets it explicitly either way).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN validado INTEGER NOT NULL DEFAULT 1")
    }
}

/** v8: en Clasificación, por default se muestra el nombre; la persona puede elegir mostrar su apodo. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN usar_apodo_ranking INTEGER NOT NULL DEFAULT 0")
    }
}

/** v9: fecha de nacimiento, para saber si la cuenta es de un menor de edad. Nula en cuentas que ya existían. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN fecha_nacimiento INTEGER")
    }
}

/** v10: el banco de ejercicios del Módulo 6 cambió (un sonido por oído, sin los de enfrente y atrás). Se borra para que se vuelva a cargar. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM resultados_ejercicios")
        db.execSQL("DELETE FROM ejercicios")
        db.execSQL("DELETE FROM resultados_vibracion")
        db.execSQL("DELETE FROM ejercicios_vibracion")
    }
}

/** v11: correo opcional de contacto, solicitud de validación institucional, baja pedida por menores y bitácora de cambios de cuenta. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN email TEXT")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN solicita_validacion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN solicita_baja INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN aviso_pendiente TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cambios_cuenta (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "objetivo_id INTEGER NOT NULL, autor_id INTEGER, tipo TEXT NOT NULL, detalle TEXT, " +
                "fecha INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cambios_cuenta_objetivo_id ON cambios_cuenta (objetivo_id)")
    }
}

/** v12: cada cuenta nueva queda "nueva" hasta que un administrador la confirma; las de antes ya se consideran confirmadas. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN revision_cuenta TEXT NOT NULL DEFAULT 'confirmada'")
    }
}

/** v13: la institución a la que la persona pidió unirse, separada de la institución ya validada (origen). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN institucion_solicitada TEXT")
    }
}

/** v14: contactos de emergencia por persona, y reportes con destinatario y opción de anonimato. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS contactos_emergencia (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, usuario_id INTEGER NOT NULL, " +
                "nombre TEXT NOT NULL, telefono TEXT NOT NULL, relacion TEXT, " +
                "FOREIGN KEY(usuario_id) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contactos_emergencia_usuario_id ON contactos_emergencia (usuario_id)")
        db.execSQL("ALTER TABLE incidencias ADD COLUMN destinatario_rol TEXT")
        db.execSQL("ALTER TABLE incidencias ADD COLUMN anonimo INTEGER NOT NULL DEFAULT 0")
    }
}

/** v15: un alumno puede pedir cambiar de educador; queda marcado hasta que lo atienda quien corresponde. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN solicita_cambio_educador INTEGER NOT NULL DEFAULT 0")
    }
}

/** v16: el administrador puede responder un mensaje de Ayuda y soporte; la respuesta la ve quien lo escribió. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE incidencias ADD COLUMN respuesta TEXT")
    }
}

/**
 * v17: Accesibilidad (Equipo 5, entregable 1). Crea `perfiles`, `gestos` y `acciones`, las tres tablas
 * que usa el servicio de accesibilidad para traducir un gesto a una acción según el perfil activo.
 *
 * Solo crea el esquema: el perfil básico se guarda la primera vez desde
 * `AccesibilidadRepository.asegurarPerfilPredeterminado()`, no aquí.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `perfiles` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nombre` TEXT NOT NULL, " +
                "`descripcion` TEXT NOT NULL, " +
                "`activo` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gestos` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`perfil_id` INTEGER NOT NULL, " +
                "`codigo` TEXT NOT NULL, " +
                "FOREIGN KEY(`perfil_id`) REFERENCES `perfiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gestos_perfil_id` ON `gestos` (`perfil_id`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_gestos_perfil_id_codigo` " +
                "ON `gestos` (`perfil_id`, `codigo`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `acciones` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`gesto_id` INTEGER NOT NULL, " +
                "`tipo` TEXT NOT NULL, " +
                "`parametro` TEXT, " +
                "`orden` INTEGER NOT NULL, " +
                "FOREIGN KEY(`gesto_id`) REFERENCES `gestos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_acciones_gesto_id` ON `acciones` (`gesto_id`)")
    }
}

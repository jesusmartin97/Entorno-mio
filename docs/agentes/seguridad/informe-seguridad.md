# Informe de seguridad — CECAPI

**Base:** commit `8019dd1` · **Revisión:** 28 sep 2026 · **Agente 3 (Seguridad)** · Solo lectura.

Severidades: **Crítico** (fuga/pérdida de datos o riesgo legal inmediato, explotable hoy) · **Alto** · **Medio** · **Bajo** · **Info** (verificado correcto o sin exposición explotable).

Contexto que baja las severidades hoy: solo hay cuentas de demostración, sin datos reales, y la IA está apagada y sin conectar. Varias severidades **subirán** en cuanto haya datos reales o se conecte la IA; se indica cuándo.

---

## Tabla resumen

| ID | Severidad | Dónde | Qué pasa | Estado |
|----|-----------|-------|----------|--------|
| S-01 | Alto | `core/util/PasswordHasher.kt:13` | Contraseñas con SHA-256 sin sal | Confirmado |
| S-02 | Medio | `core/di/DatabaseModule.kt:91-119` | Cuentas demo fijas con contraseñas débiles, recreadas en cada apertura | Confirmado |
| S-03 | Alto | `AndroidManifest.xml:23` | `allowBackup=true` sin reglas: la DB (hashes) y las fotos privadas salen en copias de seguridad | Confirmado (parche adjunto) |
| S-04 | Medio | Room `cecapi.db` + `filesDir` | Base de datos y fotos sin cifrar en reposo | Confirmado |
| S-05 | Alto (privacidad) | `core/voice/VoiceEngine.kt:499-503` | La contraseña se dice en voz alta y el reconocedor del SO (Google) envía el audio a la nube | Confirmado |
| S-06 | Bajo hoy / Medio al conectar | `feature/modulo3.../AiAssistantApi.kt` | Llamada al backend sin token ni fijado de certificado | Confirmado |
| S-07 | Medio | `app/build.gradle.kts:26` | Release con `isMinifyEnabled = false`: sin ofuscar ni reducir | Confirmado |
| S-08 | Bajo / Info | `AndroidManifest.xml:29-68` | Tres componentes `exported="true"`, todos legítimos | Confirmado (sin exposición) |
| S-09 | Medio | `core/di/DatabaseModule.kt:71-74` vs `core/data/AccountEraser.kt` | "Borra mi cuenta" no es permanente para cuentas demo: reaparece al reabrir | Nuevo |
| S-10 | Bajo | `core/data/AccountEraser.kt` | El borrado no limpia DataStore (`preferred_name`, intentos de login); `preferred_name` puede ser un dato personal que sobrevive | Nuevo |
| S-11 | Info | `core/ui/CameraComponents.kt:240-246` | Selector de fotos: usa Photo Picker, NO pide permiso de galería y copia a almacenamiento privado | Verificado correcto |
| S-12 | Bajo | `feature/modulo1.../LoginAttemptsStore.kt:27,36` | El bloqueo por intentos usa la hora del teléfono: se puede saltar adelantando el reloj | Confirmado |
| S-13 | Info | `core/voice/IntentFallback.kt:29-39` | Interruptor de IA: apaga de verdad el envío; nada sale con la IA apagada | Verificado correcto |
| S-14 | Info | `feature/modulo1.../LoginViewModel.kt:297,320` | El log de login ya no escribe el usuario | Verificado correcto |
| S-15 | Bajo | `feature/modulo1.../SessionRepository.kt:48-58` | Auto-registro sin aprobación y sin índice único de usuario (posible carrera) | Confirmado |
| S-16 | Info | Historial de git (ramas locales) | Sin secretos ni claves encontrados | Verificado |

---

## Detalle de cada hallazgo

### S-01 — Contraseñas con SHA-256 sin sal
- **Severidad:** Alto (sube a Crítico con contraseñas reales).
- **Dónde:** `app/src/main/java/com/cecapi/app/core/util/PasswordHasher.kt:13-17`.
- **Qué pasa:** el hash es un SHA-256 simple, rápido y sin sal. Es vulnerable a tablas precomputadas (rainbow tables) y a fuerza bruta masiva; dos personas con la misma contraseña tienen el mismo hash. El propio comentario del archivo ya lo reconoce como pendiente.
- **Cómo reproducirlo:** leer el archivo; hashear "1234" con cualquier calculadora SHA-256 y compararlo con `usuarios.contrasena_hash` de una cuenta demo: coinciden.
- **Sugerencia:** migrar a un algoritmo para contraseñas (Argon2id preferido; bcrypt o PBKDF2 como alternativa) con sal por usuario y coste ajustable. Requiere columna de sal/parámetros y una migración. **No aplicar aún** (afecta el login y la DB): coordinar con Pp y con el Agente 1.

### S-02 — Cuentas demo fijas y débiles
- **Severidad:** Medio.
- **Dónde:** `core/di/DatabaseModule.kt:91-119` (`demoUsers`, `ensureDemoUsers`).
- **Qué pasa:** cuentas CECAPI/PEPE/JUAN con `1234` y JORGE con `4321`, administradores incluidos, recreadas en cada apertura de la base (`onOpen`). Contraseñas triviales y públicas (están en el código).
- **Cómo reproducirlo:** leer el archivo; entrar con `PEPE`/`1234` como administrador.
- **Sugerencia:** aceptable para demo, pero antes de datos reales: quitar el sembrado automático de administradores, o exigir cambio de contraseña en el primer inicio, o cargar cuentas solo en compilaciones `debug`. Documentar que estas credenciales son públicas.

### S-03 — `allowBackup=true` sin reglas de respaldo
- **Severidad:** Alto.
- **Dónde:** `app/src/main/AndroidManifest.xml:23`. No existen `dataExtractionRules` ni `fullBackupContent`.
- **Qué pasa:** con el respaldo activado y sin exclusiones, la base `cecapi.db` (con los hashes de contraseña y el historial) y las fotos privadas de documentos/entorno en `filesDir` entran en las copias de seguridad de Android (nube y `adb backup`). Es la vía de fuga de datos más directa hoy.
- **Cómo reproducirlo:** en una compilación de depuración, `adb backup -f cecapi.ab com.cecapi.app`, extraer el `.ab` y encontrar `cecapi.db` y las fotos. (Prueba a ejecutar por el Agente 1 en dispositivo, con Pp presente.)
- **Sugerencia:** poner `android:allowBackup="false"`, o mantener el respaldo pero excluir la base y las fotos con `dataExtractionRules`/`fullBackupContent`. **Parche mínimo (una línea) en `parches/S-03-allowBackup.patch`, sin aplicar.**

### S-04 — Datos sin cifrar en reposo
- **Severidad:** Medio.
- **Dónde:** Room (`cecapi.db`) y fotos en `context.filesDir` (`core/ui/CameraComponents.kt:218,241`).
- **Qué pasa:** `filesDir` es privado del app, pero el contenido es legible con acceso físico + respaldo (S-03), en un teléfono con root, o si el almacenamiento del app se vuelca. Incluye fotos de documentos (posibles datos personales) e historial.
- **Cómo reproducirlo:** tras S-03, abrir la copia; o en un emulador con root, leer `/data/data/com.cecapi.app/`.
- **Sugerencia:** arreglar primero S-03. Para reposo real: evaluar SQLCipher para la base y cifrado de las fotos (con la clave en Android Keystore). Medir el impacto en rendimiento con el Agente 4. Decisión de Pp por el coste.

### S-05 — La contraseña dictada por voz sale a la nube
- **Severidad:** Alto (privacidad).
- **Dónde:** `core/voice/VoiceEngine.kt:499-503` (no se usa `EXTRA_PREFER_OFFLINE`); flujo de login por voz en `LoginViewModel`.
- **Qué pasa:** el login se puede hacer por voz; la persona dice su contraseña en voz alta y el `SpeechRecognizer` del sistema (normalmente Google) transcribe el audio, y por defecto lo envía a servidores de Google. Esto ocurre **independientemente del interruptor de IA** (es el reconocedor del SO, no la IA de CECAPI). La contraseña viaja a un tercero.
- **Cómo reproducirlo:** leer el código; en dispositivo, iniciar sesión por voz sin motor offline instalado y observar que requiere red.
- **Sugerencia:** para el paso de contraseña, ofrecer entrada por teclado como alternativa; intentar reconocimiento offline (`EXTRA_PREFER_OFFLINE`) y avisar si no está; y declararlo en el aviso de privacidad (pasar el dato al Agente 5). Confirmar en dispositivo qué reconocedor está activo.

### S-06 — Backend de IA sin token ni fijado de certificado
- **Severidad:** Bajo hoy (no hay resolver registrado y la IA está apagada); Medio al conectarse.
- **Dónde:** `feature/modulo3_asistenteinteligente/AiAssistantApi.kt`; URL placeholder en `app/build.gradle.kts:21`.
- **Qué pasa:** la llamada `POST` no envía `X-CECAPI-Token` ni fija certificado. Hoy la URL es de ejemplo (`https://TU-BACKEND.example.com/...`) y nada la invoca. Cuando se conecte, cualquiera podría llamar al backend o interceptar el tráfico si el TLS no está bien configurado.
- **Cómo reproducirlo:** revisión de código.
- **Sugerencia:** al conectar el backend, añadir cabecera de token por dispositivo, límite de peticiones en el servidor y evaluar certificate pinning. Es tarea conjunta con la revisión del servidor (pendiente).

### S-07 — Release sin reducir ni ofuscar
- **Severidad:** Medio.
- **Dónde:** `app/build.gradle.kts:26` (`isMinifyEnabled = false`).
- **Qué pasa:** la versión de publicación no aplica R8/ProGuard, así que el código es fácil de decompilar y leer, y el APK pesa más. Facilita el análisis del atacante.
- **Cómo reproducirlo:** revisión de código; decompilar un APK de release.
- **Sugerencia:** activar `isMinifyEnabled = true` con reglas ProGuard probadas. Coordinar con el Agente 4 (ya existe `res/raw/keep.xml`) y probar que Room/Hilt/ML Kit no se rompen. No aplicar sin pruebas.

### S-08 — Componentes exportados
- **Severidad:** Bajo / Info (sin exposición explotable).
- **Dónde:** `AndroidManifest.xml`. Exportados: `MainActivity` (launcher, normal), `CecapiNotificationListener` (protegido por `BIND_NOTIFICATION_LISTENER_SERVICE`, requerido por el SO) y `AssistantWidgetProvider` (widget; `APPWIDGET_UPDATE` es un broadcast del sistema).
- **Qué pasa:** los tres tienen razón para estar exportados y no procesan datos externos peligrosos. `MainActivity` es `singleTop` sin deep links ni extras. `WidgetActionReceiver` (el que ejecuta acciones) ya está `exported="false"`.
- **Cómo reproducirlo:** revisión del manifiesto y de los `onReceive`.
- **Sugerencia:** ninguna urgente. Verificar que `MainActivity` no procese `Intent` externos en el futuro. Confirmado como benigno.

### S-09 — "Borra mi cuenta" no es permanente para cuentas demo
- **Severidad:** Medio · **Estado:** Nuevo.
- **Dónde:** `core/di/DatabaseModule.kt:71-74` (`onOpen` → `ensureDemoUsers`) frente a `core/data/AccountEraser.kt` y `SessionRepository.deleteCurrentAccount`.
- **Qué pasa:** `AccountEraser` borra los datos y las fotos de la persona y cierra la sesión — eso funciona. Pero `ensureDemoUsers` recrea las cuentas demo en cada apertura de la base. Si la persona borra una cuenta demo (las únicas que existen hoy), al reiniciar la app la **cuenta reaparece** (vacía y con la contraseña por defecto). Los datos personales (chats, fotos) sí se borran y no vuelven; lo que reaparece es la cuenta.
- **Cómo reproducirlo:** entrar como JUAN, decir "borra mi cuenta" y confirmar; cerrar y reabrir la app; JUAN vuelve a existir con `1234`.
- **Sugerencia:** que `ensureDemoUsers` no recree una cuenta que fue borrada a propósito (marca de "borrada"), o restringir el sembrado a la primera creación (`onCreate`) / a compilaciones `debug`. Aclarar el mensaje al usuario mientras tanto.

### S-10 — El borrado no limpia DataStore
- **Severidad:** Bajo · **Estado:** Nuevo.
- **Dónde:** `core/data/AccountEraser.kt` (solo toca la base y las fotos).
- **Qué pasa:** `erase()` no limpia los DataStore: `device_settings` (incluye `preferred_name`, cómo quiere que le llamen — posible dato personal) ni `login_attempts`. Como son por dispositivo, sobreviven al borrado de "mi cuenta".
- **Cómo reproducirlo:** poner un nombre preferido, borrar la cuenta, reabrir: el nombre sigue.
- **Sugerencia:** al borrar la cuenta, limpiar también `preferred_name` (y decidir si `device_settings` completo). Documentar que estos ajustes son del dispositivo, no de la persona. Confirmar el alcance esperado del borrado con Pp/Agente 5.

### S-11 — Selector de fotos (verificado correcto)
- **Severidad:** Info.
- **Dónde:** `feature/.../DocumentReaderScreen.kt:102`, `EnvironmentScreen.kt:101`, `core/ui/CameraComponents.kt:240-246`.
- **Qué pasa:** usa `PickVisualMedia` (Android Photo Picker), que **no** requiere `READ_MEDIA_IMAGES`/galería, y copia la imagen elegida a `filesDir` privado. Comportamiento correcto y de mínimo privilegio.
- **Sugerencia:** ninguna. Recordatorio: esas fotos quedan bajo S-03/S-04.

### S-12 — Bloqueo por intentos basado en la hora del teléfono
- **Severidad:** Bajo.
- **Dónde:** `feature/modulo1.../LoginAttemptsStore.kt:27,36` (`System.currentTimeMillis()`).
- **Qué pasa:** el bloqueo de 15 min tras 10 fallos se calcula con la hora del sistema; quien tenga el teléfono puede adelantar el reloj y saltarse la espera. Además el contador es por dispositivo, no por cuenta, y se reinicia al borrar datos del app.
- **Cómo reproducirlo:** fallar 10 veces, adelantar el reloj del teléfono 20 min, reintentar: desbloqueado.
- **Sugerencia:** es una defensa local de bajo valor; documentarla como best-effort. Si se quiere endurecer, considerar `SystemClock.elapsedRealtime()` (con su límite: se reinicia al apagar). La defensa real vendrá del backend/servidor cuando exista.

### S-13 — Interruptor de IA (verificado correcto)
- **Severidad:** Info.
- **Dónde:** `core/voice/IntentFallback.kt:29-39`, `DeviceSettings.kt:75,91,120`.
- **Qué pasa:** `resolver` devuelve `null` mientras `enabled` sea `false`; `enabled` arranca en `false` y solo se activa al encender el interruptor (que persiste en DataStore, por defecto apagado). Además no hay ningún resolver registrado. Con la IA apagada, nada de lo que diga la persona se envía al servidor de CECAPI.
- **Nota:** esto **no** cubre el reconocedor de voz del SO (ver S-05), que es un canal distinto.
- **Sugerencia:** ninguna. Comportamiento correcto.

### S-14 — Log de login sin usuario (verificado correcto)
- **Severidad:** Info.
- **Dónde:** `feature/modulo1.../LoginViewModel.kt:297,320`.
- **Qué pasa:** los logs registran solo `success=<bool>` y el número de intento; no el usuario ni la contraseña. `RegisterViewModel.kt:174` registra "register crashed" con la excepción (genérica). El reconocedor tampoco registra el texto dictado.
- **Sugerencia:** verificar que ningún `Log` futuro incluya la frase reconocida (que puede contener la contraseña). Correcto hoy.

### S-15 — Auto-registro sin aprobación ni índice único
- **Severidad:** Bajo.
- **Dónde:** `feature/modulo1.../SessionRepository.kt:48-58`.
- **Qué pasa:** cualquiera que abra la app puede crear una cuenta (rol ALUMNO) sin aprobación. La tabla `usuarios` no tiene índice único en `nombre_usuario`; el duplicado se comprueba con una consulta previa, lo que deja una pequeña ventana de carrera para crear dos usuarios con el mismo nombre.
- **Cómo reproducirlo:** revisión de código.
- **Sugerencia:** añadir índice único en `nombre_usuario` (migración) para que la base garantice la unicidad; y decidir si el auto-registro debe existir en producción o requerir aprobación institucional. Coordinar con Pp (ya anotado como flujo futuro en el código).

### S-16 — Sin secretos en el historial (verificado)
- **Severidad:** Info.
- **Qué se hizo:** búsqueda en el árbol y en el historial local de git (`AIza…`, `sk-…`, `ghp_…`, `-----BEGIN`, `xox…`, pickaxe sobre `apiKey`). Sin resultados.
- **Pendiente:** el servidor de IA (`modulo03/backend-asistente`, remoto) no se pudo revisar; debe revisarse por separado, incluyendo su historial y sus claves.

---

## Nota sobre dependencias

Versiones (de `gradle/libs.versions.toml`, ~octubre 2024): AGP 8.5.2, Kotlin 2.0.21, Room 2.6.1, Hilt 2.51.1, CameraX 1.3.4, ML Kit (text 16.0.1, object 17.0.2, labeling 17.0.9), Coroutines 1.9.0, DataStore 1.1.1, Compose BOM 2024.10.00.

No se afirma ninguna CVE concreta sin fuente con fecha. **Recomendación:** correr un escaneo automatizado (p. ej. `./gradlew dependencyCheckAnalyze` con OWASP Dependency-Check, o `dependencyUpdates`) y anotar hallazgos con su fuente. Las versiones de Gradle/AGP/Kotlin/KSP están congeladas por decisión de Pp, así que cualquier actualización de dependencia se le consulta.

## Orden de arreglo sugerido (primero lo que más reduce riesgo)

1. **S-03** `allowBackup` — Alto, arreglo de una línea, parche listo.
2. **S-05** contraseña por voz → nube — Alto (privacidad); necesita diseño + aviso (Agente 5).
3. **S-01** hashing de contraseñas — Alto; **antes** de cuentas reales.
4. **S-09** borrado permanente de cuenta demo — Medio.
5. **S-07** activar minify en release — Medio (con Agente 4).
6. **S-04** cifrado en reposo — Medio (decisión de coste de Pp).
7. **S-06** token/pinning del backend — al conectar la IA (con revisión del servidor).
8. **S-02 / S-10 / S-12 / S-15** — Medio/Bajo, según convenga.

Los hallazgos "Info" (S-08, S-11, S-13, S-14, S-16) están verificados como correctos: no requieren acción, solo vigilancia.

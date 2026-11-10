# Agente 4 · Optimización — análisis sin teléfono (parte 2)

- **Commit revisado:** 8019dd1, compilado desde una copia limpia (`git archive`) fuera del proyecto.
- **Fecha:** 29 de septiembre de 2026
- **Límites respetados:** no se tocó app/src/main, Gradle ni módulos de equipos. Los experimentos de compilación se hicieron en una copia aparte, en la carpeta temporal.
- **Estado:** todo es revisión de código o medición en computadora. Lo que depende del teléfono está marcado "por medir".

## Resumen

| ID | Severidad | Tema | Estado |
|---|---|---|---|
| O-04 | Alto | Casi el 70 % del APK son librerías de ML Kit repetidas para 4 tipos de procesador | Medido |
| O-05 | Medio | Librería completa de íconos extendidos para 19 íconos | Medido de forma indirecta |
| O-06 | Medio | El reconocedor de "hola" se recrea cada 300 ms tras un error, sin espera creciente | Leído en el código; batería por medir |
| O-07 | Bajo | Ninguna tabla tiene índices; el borrado en cascada recorre tablas completas | Leído en el código |
| O-08 | Bajo | Historial de comandos sin límite de crecimiento | Leído en el código |
| O-09 | — | Cobertura de minSdk 26 | Dato externo, con fuente |

---

## O-04 · Peso del APK: librerías nativas de ML Kit para 4 procesadores

| Campo | Contenido |
|---|---|
| **ID** | O-04 |
| **Severidad** | Alto para la meta de publicar en la web; bajo si se publica en Google Play como .aab |
| **Dónde** | `app/build.gradle.kts` (sin `abiFilters`) y dependencias `mlkit.text.recognition` y `mlkit.object.detection` |
| **Qué pasa** | Ver la medición abajo. |
| **Cómo reproducirlo** | `./gradlew assembleDebug` y abrir el APK como zip: carpeta `lib/`. |
| **Sugerencia** | Ver abajo. Son cambios de Gradle: los decide Pp y los aplica el constructor. |
| **Estado** | Medido (ahorro real en "Experimentos de peso": −47.8 MB solo con el filtro) |

### Medición (verificado)

Composición del APK de depuración de 8019dd1 (121.7 MB):

| Parte | MB |
|---|---|
| `lib/x86` | 24.1 |
| `lib/x86_64` | 23.8 |
| `lib/arm64-v8a` | 22.1 |
| `lib/armeabi-v7a` | 13.7 |
| Código (dex, 17 archivos) | 20.9 |
| Audios .wav | 9.8 |
| Modelos ML Kit en assets | 6.3 |
| Resto | 1.0 |

Casi todo `lib/` son dos archivos: `libmlkitcommonpipeline.so` y `libmlkit_google_ocr_pipeline.so`, de 11 a 12.5 MB cada uno por procesador.

- **x86 y x86_64 (48 MB):** solo los usan emuladores y algunas Chromebook, no teléfonos.
- **El Oppo CPH2387 es arm64** (supuesto, por su procesador; confirmar con `adb shell getprop ro.product.cpu.abi`).

### Opciones, de menor a mayor riesgo

1. **Si se publica en Google Play (.aab):** Play entrega a cada teléfono solo lo de su procesador. No hay que tocar código.
2. **Si se reparte el APK por la web:** `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }` solo en `release`, para que el emulador del equipo siga funcionando en depuración. El ahorro medido está en la sección de experimentos.
3. **APK separados por procesador** (`splits { abi { ... } }`): el más ligero, pero hay que explicar a la persona cuál bajar. No se recomienda para este público.

---

## O-05 · `material-icons-extended` completa por 19 íconos

| Campo | Contenido |
|---|---|
| **ID** | O-05 |
| **Severidad** | Medio |
| **Dónde** | `gradle/libs.versions.toml`, línea 31 (`compose-material-icons-extended`) |
| **Qué pasa** | El código usa 19 íconos distintos. Sin reducción de código (`isMinifyEnabled = false`), la librería entra completa: el código del APK release ocupa 50.4 MB sin comprimir. La librería de íconos extendidos es conocida por ser la parte más grande de ese código; *falta medir su parte exacta*. |
| **Cómo reproducirlo** | `grep -rhoE "Icons\.(Filled\|Outlined\|Rounded\|AutoMirrored\.Filled\|Default)\.\w+" app/src/main/java \| sort -u` da 19 resultados. |
| **Sugerencia** | La reducción de código (R8) quita lo que no se usa sin cambiar nada del código; ver los experimentos. Otra opción, sin R8: usar `material-icons-core` y copiar como archivos los íconos que falten. Eso toca pantallas de varios equipos, así que no se recomienda ahora. |
| **Estado** | Nuevo |

---

## O-06 · El reconocedor de "hola" se recrea cada 300 ms tras un error

| Campo | Contenido |
|---|---|
| **ID** | O-06 |
| **Severidad** | Medio (batería); puede subir cuando se mida |
| **Dónde** | `core/voice/VoiceEngine.kt`, `listenForWakeWord()`, en `onError` |
| **Qué pasa** | Ver abajo. |
| **Cómo reproducirlo** | *Por medir en el teléfono, con Pp:* 1. Modo avión. 2. Abrir la app con la escucha de "hola" activa. 3. `adb logcat` y contar cuántas veces se crea el reconocedor por minuto. 4. Medir batería con `adb shell dumpsys batterystats --reset`, 15 minutos, y `dumpsys batterystats`. |
| **Sugerencia** | Ver abajo. |
| **Estado** | Nuevo, leído en el código |

**Qué pasa (verificado en el código):**
- En cada error que no sea de permisos (5 s) ni de reconocedor ocupado (1.5 s), se destruye el reconocedor y se crea otro a los 300 ms.
- El silencio normal termina en error de "sin coincidencia" tras unos segundos, así que el ciclo es lo esperado.
- El riesgo está en los errores **inmediatos**: sin internet (`ERROR_NETWORK`, `ERROR_NETWORK_TIMEOUT`), `ERROR_SERVER` o `ERROR_CLIENT`. Ahí el ciclo podría repetirse unas 3 veces por segundo, crear y destruir el servicio de reconocimiento de Google sin parar, y gastar CPU y batería mientras la app está abierta.
- También aplica a la escucha fuera de la app, si está activada.

**Sugerencia:**
- **Espera creciente para errores repetidos:** 300 ms, 1 s, 3 s, hasta 10 s; volver a 300 ms al primer resultado bueno.
- **Opcional:** reutilizar el mismo `SpeechRecognizer` en vez de crear uno nuevo cada vez, y probar `EXTRA_PREFER_OFFLINE` para que funcione sin internet donde el teléfono tenga el paquete de español.

---

## O-07 · Sin índices en las tablas

| Campo | Contenido |
|---|---|
| **ID** | O-07 |
| **Severidad** | Bajo hoy, porque hay pocas filas |
| **Dónde** | Todas las `@Entity` con llaves foráneas: módulos 1, 2, 3, 4, 5, 6 y 7 |
| **Qué pasa** | Ninguna entidad declara `indices`. Hoy las consultas por `usuario_id` recorren la tabla completa. Además, `AccountEraser` borra el usuario y la base borra en cascada; sin índice en la columna hija, SQLite recorre la tabla hija completa por cada fila borrada. La documentación de SQLite recomienda indexar las columnas hijas de una llave foránea ([sqlite.org/foreignkeys.html](https://www.sqlite.org/foreignkeys.html), sección 3). |
| **Cómo reproducirlo** | `grep -rn "indices" app/src/main/java` no da resultados. |
| **Sugerencia** | Agregar `indices = [Index("usuario_id")]` y `Index("comando_id")`, `Index("documento_id")` y `Index("consulta_id")` donde haya llave foránea. **Requiere una migración (versión 4)**; conviene juntarlo con la migración que ya se planea con Alonso (P-12), no hacerlo aparte. |
| **Estado** | Nuevo |

---

## O-08 · Historial sin límite de crecimiento

| Campo | Contenido |
|---|---|
| **ID** | O-08 |
| **Severidad** | Bajo |
| **Dónde** | `VoiceAssistantRepository.logInteraction` (3 filas por comando), historial de lectura, resultados de ejercicios |
| **Qué pasa** | Las lecturas usan `LIMIT 30` o `LIMIT 50`, pero nada borra filas viejas. Solo se borra con "borrar mi cuenta". Crece despacio (texto corto), pero sin tope. Las fotos sí tienen limpieza (30 días) desde Almacenamiento. |
| **Cómo reproducirlo** | `grep -rn "DELETE FROM" app/src/main`: solo el borrado de cuenta, las migraciones y las consultas de IA. |
| **Sugerencia** | Recortar al abrir la app: conservar, por ejemplo, los últimos 500 registros por persona o los de 90 días. Esto también ayuda a la política de conservación de datos del Agente 5. |
| **Estado** | Nuevo |

---

## O-09 · Cobertura de minSdk 26 (Android 8)

- **Dato (fuente externa, no oficial de Google):** con minSdk 26 la app se puede instalar en el **96.1 %** de los Android en uso. Bajar a 24 (Android 7) sumaría solo 0.5 % (96.6 %). Subir a 28 dejaría fuera 2.6 %. Fuente: [apilevels.com](https://apilevels.com/), con datos de StatCounter GlobalStats de abril de 2026 (actualizado el 28 de mayo de 2026).
- **Recomendación:** mantener 26. Bajar a 24 costaría rehacer la vibración (`VibrationEffect`), los canales de notificación y los íconos adaptables, para ganar medio punto.
- **Por confirmar:** Google publica su propia distribución solo dentro de Android Studio (asistente de nuevo proyecto). Conviene que Pp tome una captura de ahí para tener la cifra oficial.

---

## Experimentos de peso (en una copia aparte, sin tocar el proyecto)

Tres APK release de 8019dd1, compilados en una copia aparte: el cambio de Gradle se hizo solo en la copia, nunca en el proyecto.

| Versión | Cambio | Tamaño | Ahorro | Código (dex) sin comprimir |
|---|---|---|---|---|
| Base | Como está hoy | 114.5 MB | — | 50.4 MB |
| A | + `abiFilters` arm64-v8a y armeabi-v7a | 66.7 MB | −47.8 MB (−42 %) | 50.4 MB |
| B | A + `isMinifyEnabled = true` y `isShrinkResources = true` | 54.8 MB | −59.7 MB (−52 %) | 5.3 MB |

**Lo verificado:**
- Las tres compilan (BUILD SUCCESSFUL).
- En B, R8 bajó el código de 50.4 a 5.3 MB. Eso confirma O-05: casi todo era código sin usar, sobre todo los íconos.
- En B siguen los **17 audios**: el `res/raw/keep.xml` que agregó el constructor funciona.
- Pasar los .wav a .ogg ahorraría como máximo unos 8 de los 9.8 MB de audio (estimado, no medido). Es mucho menos que A o B.

**Lo NO verificado (importante):**
- B compila, pero **no se ha abierto en un teléfono**.
- R8 puede romper en ejecución lo que usa reflexión: Hilt, Room, ML Kit y la carga de audios por nombre. Antes de adoptarlo hay que:
  1. Instalar B firmado con la clave de depuración.
  2. Recorrer la lista de 30 minutos del Agente 1.
  3. Revisar `adb logcat` en busca de `ClassNotFoundException` o `NoSuchMethodException`.

**Propuesta (por escrito, sin aplicar):**
1. **Primero A**, cuando Pp decida publicar el APK por la web. Riesgo bajo: solo quita las librerías de emulador y no cambia código.
2. **B después**, solo tras pasar la lista del Agente 1 en el Oppo con Pp presente.
3. Dejar .ogg y .aab para cuando se decida el canal de publicación.

---

## Lo que necesito de otros

| A quién | Qué | Para qué |
|---|---|---|
| **Pp** | ¿Dónde se va a publicar primero: Play (.aab) o APK por la web? | Decide si O-04 necesita `abiFilters` o si Play lo resuelve solo |
| **Pp** | ¿El equipo prueba en emulador? ¿De qué tipo (x86_64)? | Para que los filtros de procesador no rompan sus pruebas |
| **Pp** | Qué otros teléfonos hay, sobre todo uno de gama baja con Android 8 a 10 | Medición de arranque, memoria y O-01 |
| **Agente 1** | Avisar cuando cierre la ronda 2; incluir en su lista un caso "modo avión con la escucha activa por 5 minutos" | Reproducir O-06 |
| **Agente 3** | ¿Ve algún problema de seguridad en activar R8 (reglas `-keep`) o en `EXTRA_PREFER_OFFLINE`? | Antes de proponer O-05 y O-06 |
| **Agente 5** | ¿Qué plazo de conservación del historial conviene escribir en la política? | Para fijar el tope de O-08 |
| **Constructor** | En la próxima migración (v4), ¿se pueden sumar los índices de O-07? | Evita una migración extra |

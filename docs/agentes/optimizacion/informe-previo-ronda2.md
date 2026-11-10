# Agente 4 · Optimización — informe previo a la medición en el teléfono

- **Commit revisado:** 8019dd1 (rama integracion/actividades, sin subir)
- **Fecha:** 29 de septiembre de 2026
- **Estado:** revisión del código y medición en computadora. **Falta medir en el teléfono**, con Pp presente y después de la ronda 2 del Agente 1.
- **Límites respetados:** no se tocó app/src/main, Gradle ni módulos de otros equipos. Sin commit ni push. Todo está en esta carpeta.

## Resumen

| ID | Severidad | Tema | Estado |
|---|---|---|---|
| O-01 | Medio | `VoiceText.matches` vuelve a partir el texto con cada frase de comando | Nuevo, medido en computadora |
| O-02 | Bajo | El MediaPlayer de Actividades queda abierto después de sonar | Nuevo, leído en el código |
| O-03 | — | El lector y Actividades al salir de la app | Verificado en el código: se detienen bien |

No hay nada crítico, así que no se entrega parche.

---

## O-01 · Costo de `VoiceText.matches` por frase

| Campo | Contenido |
|---|---|
| **ID** | O-01 |
| **Severidad** | Medio: hoy no traba la app, pero el costo crece con el largo de la frase y con el número de comandos |
| **Dónde** | `core/voice/VoiceText.kt`, funciones `matches` y `hasAny` |
| **Qué pasa** | `hasAny` llama a `matches` una vez por cada frase de comando. Cada llamada vuelve a partir el texto dicho (`split` y `filter`), arma un texto nuevo con espacios y, en la búsqueda de "en frente", une palabras en textos nuevos. Con muchas frases de comando, ese trabajo se repite cientos de veces por cada frase dicha. |
| **Cómo reproducirlo** | Ver "Cómo repetir la medición", al final. |
| **Sugerencia** | Partir el texto una sola vez por frase dicha y reutilizarlo en todas las comparaciones. Comparar "en frente" sin armar textos nuevos (largo, inicio y fin). Reutilizar los dos arreglos de la distancia de edición. La propuesta completa está en `medicion-voicetext/Fast.kt`, con las mismas reglas. |
| **Estado** | Nuevo |

### Medición (verificado, en computadora)

**Condiciones:**
- JVM 17 en Windows, no en el teléfono.
- Mismo `VoiceText.kt` del commit 8019dd1, compilado aparte con Kotlin 2.0.21.
- Se comparan **244 frases de comando**: todas las que aparecen en llamadas a `hasAny` o `has` en toda la app. Es el **peor caso**, una frase que ninguna pantalla entiende y recorre todo. En una pantalla real se comparan muchas menos.
- 3,000 repeticiones tras calentar; los tiempos son de una frase dicha, contra las 244.

| Frase dicha | Actual, mediana / p99 | Propuesta, mediana / p99 |
|---|---|---|
| Corta (3 palabras) | 141 µs / 436 µs | 58 µs / 315 µs |
| Normal (12 palabras) | 383 µs / 1,194 µs | 215 µs / 1,327 µs |
| Larga (100 caracteres) | 584 µs / 1,716 µs | 171 µs / 432 µs |
| Muy larga (500 caracteres) | 2,809 µs / 7,641 µs | 685 µs / 2,243 µs |

Una segunda corrida dio cifras parecidas: la propuesta sale entre 2 y 4 veces más rápida, más en frases largas.

**Equivalencia (verificado):** 329,644 comparaciones, con 0 diferencias entre la versión actual y la propuesta.
- Se usaron 1,353 entradas.
- Incluyen las 244 frases, cada una con una letra cambiada, pegada a palabras al azar y con un espacio menos.
- También incluyen "isquierda", "en frente", "menudo", "contexto", "barra" y "borra", y 300 frases al azar.

### Qué significa (supuesto, falta confirmarlo en el teléfono)

- **Hilo principal:** la comparación corre ahí. Las pantallas reciben la frase en `viewModelScope`; hay 14 lugares que escuchan `recognizedSpeech`.
- **Teléfonos modestos:** suelen ser varias veces más lentos que una computadora. En el peor caso, una frase de 500 caracteres podría tardar decenas de milisegundos: se notaría como un tirón, pero está muy lejos de un cierre por ANR (5 segundos).
- **Frases normales:** debería quedar muy por debajo de un cuadro de pantalla (16 ms).
- **Resultados parciales:** están apagados (`EXTRA_PARTIAL_RESULTS = false`), así que la comparación corre una vez por frase, no por cada palabra reconocida.
- **Qué medir en el Oppo:** el tiempo de `onSpeech` en Configuración y Personalización, que tienen más comandos, con una frase normal y una de 500 caracteres.

---

## O-02 · MediaPlayer de Actividades abierto después de sonar

| Campo | Contenido |
|---|---|
| **ID** | O-02 |
| **Severidad** | Bajo: es un solo reproductor a la vez; no se acumulan |
| **Dónde** | `feature/modulo6_aprendizaje/AudioSpatialPlayer.kt`, `reproducir()` y `detener()` (módulo del Equipo 4) |
| **Qué pasa** | Cuando termina el sonido, el reproductor no se libera: se queda abierto mientras la persona contesta, hasta el siguiente ejercicio, "repetir", salir de la app o cerrar la pantalla. Mientras tanto ocupa un decodificador de audio y memoria nativa. |
| **Cómo reproducirlo** | 1. Abrir Actividades y dejar sonar un ejercicio. 2. Sin contestar, revisar con `adb shell dumpsys media.player` que sigue un reproductor de `com.cecapi.app`. *Supuesto: no se ha corrido en el teléfono.* |
| **Sugerencia** | Liberarlo al terminar: al final de `reproducir()`, si `mediaPlayer === player`, llamar a `detener()`; o usar `setOnCompletionListener`. Es un módulo de otro equipo: el cambio lo decide Pp con el Equipo 4. |
| **Estado** | Nuevo |

**Qué sí está bien (verificado en el código):**
- `detener()` se llama antes de cada sonido nuevo, en `onCleared()` y en `onAppStopped()`.
- Nunca hay dos reproductores a la vez.
- No hay fuga que crezca con el uso.

---

## O-03 · El lector y Actividades al salir de la app

| Campo | Contenido |
|---|---|
| **ID** | O-03 |
| **Severidad** | — (sin fallo) |
| **Dónde** | `MainActivity.onStop`, `LearningScreen.kt` (observador de `ON_STOP`) y `DocumentReaderViewModel` (observador de párrafos) |
| **Qué pasa** | Verificado en el código: al salir de la app, Actividades cancela el ejercicio, detiene el sonido y la vibración, y el lector deja de leer. No queda trabajo corriendo en segundo plano. Los detalles están abajo. |
| **Cómo reproducirlo** | En el teléfono: 1. Empezar a leer un documento, o a sonar un ejercicio. 2. Presionar Inicio. 3. Comprobar que se calla y no vibra. |
| **Sugerencia** | Ninguna en el código. Falta comprobarlo en el Oppo, porque ColorOS a veces no entrega `onStop` como se espera. |
| **Estado** | Verificado en el código, por confirmar en el teléfono |

**Detalle de lo que se leyó en el código:**
- **Actividades:** con `ON_STOP` se llama a `onAppStopped()`, que cancela el ejercicio y detiene el sonido y la vibración.
- **Lector:** `onStop` primero marca `appVisible = false` y luego llama a `stopSpeaking()`. El observador ve el cambio de `Speaking` a `Idle` con la app oculta y marca `estaLeyendo = false`, así que no sigue al siguiente párrafo.
- **Otros detalles:** el reconocedor también se detiene en `onStop`. `onCleared()` de las dos pantallas libera lo que queda.

---

## Lo que sigue (cuando el Agente 1 cierre la ronda 2)

Con Pp presente y en el Oppo CPH2387:

1. **Arranque:** en frío y en caliente, con `adb shell am start -W`.
2. **Memoria y CPU:** en el menú, la cámara, Actividades y el lector.
3. **O-01:** tiempo por frase en Configuración y Personalización.
4. **O-02:** `dumpsys media.player` durante un ejercicio.
5. **O-03:** salir de la app mientras lee o suena.
6. **Batería:** con la escucha activa, fuera de la app y con brillo mínimo.

Las ideas de peso (.ogg, reducción de código y .aab) no se aplican. Si se proponen, será por escrito y con su medición, como pidió Pp.

## Cómo repetir la medición de O-01

Los archivos están en `medicion-voicetext/`:
- `Fast.kt`: la propuesta.
- `Bench2.kt`: la prueba de equivalencia y de tiempos.
- `phrases.txt`: las 244 frases.
- `corpus.txt`: las 1,353 entradas.

Se compila junto con una copia de `app/src/main/java/com/cecapi/app/core/voice/VoiceText.kt`, usando el compilador de Kotlin 2.0.21 que ya está en la caché de Gradle (`kotlin-compiler-embeddable-2.0.21.jar` más `kotlin-stdlib-2.0.21.jar`). En Windows:

```
java -cp "<kotlin-compiler-embeddable, stdlib, script-runtime, daemon-embeddable, trove4j, coroutines-core-jvm y annotations-13.0>" ^
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "<kotlin-stdlib-2.0.21.jar>" ^
  -d out VoiceText.kt Fast.kt Bench2.kt
java -cp "out;<kotlin-stdlib-2.0.21.jar>" Bench2Kt phrases.txt corpus.txt
```

No cambia ningún archivo del proyecto.

# Agente 4 · Optimización — ronda sobre d5ffd5e

- **Commit revisado:** d5ffd5e (integracion/actividades), compilado desde una copia limpia (`git archive`) fuera del proyecto.
- **Fecha:** 29 de septiembre de 2026
- **Límites respetados:** no se tocó app/src/main, Gradle ni módulos de equipos. No se usó el Oppo: todo lo que depende de él está marcado "por medir con Pp".
- **Enfoque de esta ronda:** buscar por qué falla, cuándo y a quién daña, no solo si funciona.

## Resumen

| ID | Severidad | Tema | Estado |
|---|---|---|---|
| O-10 | **Alto** | Una foto de galería de 105 MP o más cierra la app al mostrarse | Verificado con la fuente de Android 12 y cálculo; no reproducido |
| O-11 | Medio | La vista previa usa hasta 8 veces más memoria de la necesaria en el Oppo | Calculado; por medir |
| O-12 | Medio | Entorno decodifica la foto completa cuando no detecta objetos; `catch (Exception)` no atrapa la falta de memoria | Leído en el código |
| O-13 | Medio | Wikipedia: hasta unos 50 s de silencio con mala señal, y "no encontré nada" cuando en realidad falló la red | Leído en el código |
| O-14 | — | Peso del APK repetido sobre d5ffd5e | Ver la sección "Peso" |

Pedido de esta ronda: "cuánta memoria usa mantener el bitmap decodificado mientras se lee un párrafo largo". La respuesta corta está en O-11. O-10 y O-12 salieron al revisar el mismo código.

---

## O-10 · Una foto grande de la galería cierra la app

| Campo | Contenido |
|---|---|
| **ID** | O-10 |
| **Severidad** | **Alto**: cierre de la app. Para una persona ciega, el asistente desaparece sin aviso a mitad de una tarea. |
| **Dónde** | `core/ui/CameraComponents.kt`, `CapturedPhotoPreview`: `BitmapFactory.decodeFile(path, inSampleSize = 2)`. Afecta al lector y a Entorno. |
| **Qué pasa** | Ver abajo. |
| **Cómo reproducirlo** | *Por reproducir con Pp:* 1. Pasar al Oppo una foto de 108 MP (12000×9000), por ejemplo tomada con un teléfono Samsung o Xiaomi de 108 MP y enviada como archivo, no por WhatsApp, que la comprime. 2. En el lector, "elige una foto" y escogerla. 3. Esperado según el código: cierre con `RuntimeException: Canvas: trying to draw too large(108000000bytes) bitmap` en `adb logcat`. |
| **Sugerencia** | Ver abajo. |
| **Estado** | Nuevo. Verificado en la fuente y con cálculo; **no reproducido** en el teléfono. |

**Qué pasa:**
- La vista previa siempre reduce la foto a la mitad de cada lado, sin mirar su tamaño.
- Android 12 no deja dibujar un bitmap de más de 100 MiB (104,857,600 bytes): la app se cierra con `RuntimeException: Canvas: trying to draw too large(... bytes) bitmap`.
- Con la reducción a la mitad, eso pasa con cualquier foto de más de unos **104.9 megapíxeles**.
- Hay teléfonos comunes de gama media con cámara de 108 MP y 200 MP. Sus fotos, pasadas a la galería del Oppo por cable, Bluetooth o Drive, entran por "elige una foto".

**Fuente (verificada):** AOSP, `RecordingCanvas.java`, rama `android12-release`, línea 43: `final int DefaultSize = 100 * 1024 * 1024; // 100 MB;`. Mensaje en la línea 267: `"Canvas: trying to draw too large(" + bitmapSize + "bytes) bitmap."`. En versiones nuevas de Android el límite sube a 150 MB, pero el Oppo tiene Android 12.

| Foto de la galería | Bitmap de la vista previa (÷2) | Resultado esperado en Android 12 |
|---|---|---|
| 13 MP (cámara del Oppo) | 13.0 MB | Se muestra |
| 50 MP | 49.9 MB | Se muestra, con mucha memoria |
| 108 MP | 108.0 MB | **Cierre** al dibujar |
| 200 MP | 199.8 MB | Falta de memoria al decodificar, o cierre al dibujar |

Con 200 MP, si la falta de memoria ocurre al decodificar, `runCatching` la atrapa y el círculo de "cargando" gira para siempre. Es el mismo síntoma que el constructor ya anotó para una imagen dañada.

**Sugerencia:**
- Leer primero solo el tamaño de la foto (`inJustDecodeBounds = true`) y calcular la reducción según el espacio en pantalla. Es el método de la guía oficial de Android "Loading large bitmaps efficiently" (developer.android.com/topic/performance/graphics/load-bitmap).
- En el Oppo (pantalla de 720×1612), la reducción adecuada es ÷4 o más para cualquier foto.
- Poner un techo, por ejemplo que ningún lado pase de 2048 px, antes de dibujar.
- Se puede corregir junto con la orientación EXIF que el constructor ya tiene pendiente: los dos cambios tocan la misma función.

---

## O-11 · Memoria de la foto mientras se lee

| Campo | Contenido |
|---|---|
| **ID** | O-11 |
| **Severidad** | Medio en el Oppo, que es de gama baja (3 o 4 GB de RAM, Helio G35) |
| **Dónde** | `CapturedPhotoPreview`: el bitmap vive en `remember(path)` todo el tiempo que la pantalla muestra esa foto, incluida la lectura de todos los párrafos |
| **Qué pasa** | Ver abajo. |
| **Cómo reproducirlo** | *Por medir con Pp:* 1. `adb shell dumpsys meminfo com.cecapi.app` antes de tomar la foto. 2. Repetirlo durante la lectura de un párrafo largo. 3. Repetirlo después de salir del lector. Comparar las filas "Native Heap" y "Graphics". |
| **Sugerencia** | La misma de O-10: reducción calculada. Deja la vista previa en unos 3 MB sin que se note en pantalla. |
| **Estado** | Nuevo, calculado; **por medir** |

**Qué pasa:**
- Una foto de la cámara del Oppo es de 13 MP (4160×3120; tamaño supuesto por la ficha, confirmar con una foto real).
- Reducida a la mitad queda en 2080×1560 = **13.0 MB** en memoria nativa (4 bytes por píxel, `ARGB_8888`), retenidos mientras se lee.
- La caja donde se muestra mide como mucho 720 px de ancho: con ÷4 bastan **3.2 MB**. Sobran unos 10 MB en cada lectura.
- **El pico real es mayor mientras se procesa:**
  - Mientras se muestra la vista previa, el lector decodifica otra copia a ÷4 (3.2 MB) para revisar luz y enfoque.
  - Luego ML Kit abre el archivo con `InputImage.fromFilePath`. La documentación oficial de `InputImage` no dice si reduce la imagen: **por medir**. Si la abre completa, suma 51.9 MB.
- Leer un párrafo largo no agrega memoria de imagen: la voz solo usa texto.

Fuentes de la ficha del Oppo: [devicespecifications.com](https://www.devicespecifications.com/en/model/0e2f59d6) y [gsmchoice.com](https://www.gsmchoice.com/en/catalogue/oppo/a574g/).

---

## O-12 · Entorno: foto completa en memoria y errores de memoria sin atrapar

| Campo | Contenido |
|---|---|
| **ID** | O-12 |
| **Severidad** | Medio (posible cierre con fotos grandes de la galería) |
| **Dónde** | `feature/modulo7_entorno/EnvironmentRepository.kt`: `BitmapFactory.decodeFile(rutaImagen)` sin reducción, en el camino "no detectó objetos". También `catch (e: Exception)` ahí y en `DocumentReaderRepository.processCapturedPhoto`. Módulo del Equipo 5. |
| **Qué pasa** | Ver abajo. |
| **Cómo reproducirlo** | *Por reproducir con Pp:* en Entorno, "elige una foto" con una imagen grande de una pared lisa (sin objetos), y revisar `adb logcat` en busca de `OutOfMemoryError`. |
| **Sugerencia** | Ver abajo. Es de otro equipo: lo decide Pp con el Equipo 5. |
| **Estado** | Nuevo, leído en el código; **no reproducido** |

**Qué pasa:**
- Cuando el detector no encuentra objetos, se etiqueta la foto completa sin reducirla: 51.9 MB con una foto del Oppo, **432 MB** con una de 108 MP.
- En un teléfono de 3 GB eso puede lanzar `OutOfMemoryError`.
- `OutOfMemoryError` es un `Error`, no una `Exception`, así que el `catch` no lo atrapa y la app se cierra.
- El etiquetador de ML Kit trabaja internamente con imágenes pequeñas, así que decodificar la foto completa no mejora el resultado. *Supuesto razonable, no verificado en su documentación.*

**Sugerencia:**
- Decodificar con una reducción calculada (lado máximo de unos 1024 px) en ese camino.
- Atrapar también `OutOfMemoryError` alrededor de la decodificación, para decir en voz "la foto es demasiado grande" en vez de cerrarse.

**Aviso para el Agente 1 (supuesto, por comprobar):**
- `recortarRegion` recorta con `BitmapRegionDecoder`, que lee los píxeles tal como están guardados en el archivo.
- Las cajas de ML Kit vienen de `InputImage.fromFilePath`. Si esa función aplica la rotación EXIF, en fotos giradas se recortaría la zona equivocada y la descripción saldría mal.
- Está ligado a la orientación EXIF que el constructor ya anotó.

---

## O-13 · Wikipedia: silencio largo y mensaje engañoso

| Campo | Contenido |
|---|---|
| **ID** | O-13 |
| **Severidad** | Medio (para una persona ciega, un silencio largo parece que la app se trabó) |
| **Dónde** | `core/util/WikipediaLookup.kt` (`connectTimeout = 10_000`, `readTimeout = 15_000`, dos consultas seguidas); `HomeViewModel.askWikipedia` y `DashboardViewModel` |
| **Qué pasa** | Ver abajo. |
| **Cómo reproducirlo** | *Por reproducir con Pp:* 1. Datos móviles con una sola raya, o limitar la red con el modo desarrollador. 2. Decir "qué es un volcán". 3. Cronometrar desde "Buscando en Wikipedia" hasta la respuesta. 4. Durante la espera, abrir Actividades y ver si la respuesta interrumpe el ejercicio. |
| **Sugerencia** | Ver abajo. |
| **Estado** | Nuevo, leído en el código |

**Qué pasa (verificado en el código):**
- La consulta corre fuera del hilo principal, así que no causa ANR.
- Son dos pedidos seguidos (buscar el título y luego el resumen), cada uno con hasta 10 s para conectar y 15 s para leer.
- Con mala señal, tras "Buscando en Wikipedia" puede haber **hasta unos 50 segundos de silencio**, sin sonido de espera.
- Si falla la red o se agota el tiempo, `search` devuelve `null` y la app dice "No encontré nada en Wikipedia sobre eso". La persona entiende que el tema no existe, cuando en realidad falló la conexión.
- Una búsqueda nueva no cancela la anterior, y la respuesta se dice aunque la persona ya esté en otra pantalla, encima de lo que esté sonando.

**Sugerencia:**
- Un límite total de unos 8 s, con `withTimeoutOrNull` sobre toda la búsqueda.
- Un sonido de "sigo buscando" cada 3 s.
- Separar "no hay conexión o tardó demasiado" de "no encontré ese tema".
- Cancelar la búsqueda anterior al pedir otra y al salir de la pantalla.

---

## O-14 · Peso del APK sobre d5ffd5e

*(Se completa al terminar las compilaciones.)*

---

## Lo que necesito

| A quién | Qué |
|---|---|
| **Pp** | Una sesión con el Oppo conectado por `adb` para medir O-11 (`dumpsys meminfo`), reproducir O-10 y O-12 con una foto grande y cronometrar O-13. Necesito una foto de 50 MP o más y otra de 108 MP; si nadie del equipo tiene, se puede buscar una muestra pública con licencia libre. |
| **Constructor** | Si corrige la orientación EXIF, que calcule en la misma función la reducción de la foto (O-10 y O-11): es el mismo lugar del código. |
| **Agente 1** | Sumar a su lista la foto de 108 MP (O-10), la foto lisa en Entorno (O-12), el recorte en una foto girada (O-12) y Wikipedia con mala señal (O-13). |
| **Agente 5** | O-13 afecta a lo que pide de Wikipedia: si se filtra contenido, el límite de tiempo y la cancelación también evitan que llegue una respuesta vieja fuera de contexto. |

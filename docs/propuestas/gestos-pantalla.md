# Propuesta: gestos de pantalla de CECAPI

> Propuesta · Dueño: Equipo 5 (Jesús y Edgar) · Escrito el 19 oct 2026 contra
> `integracion/combinado` en `f5823cd` · **Actualizado el 27 oct 2026**:
> la fase 1 usa captura temporal + OCR para leer texto y captura + ML Kit en el teléfono para
> describir imágenes; los gestos de un dedo son de Android. **Dos dedos hacia abajo sigue en fase 2**.

Este documento especifica las tres acciones de gestos de pantalla del Módulo 10. Marca con claridad
qué entra en la fase 1 y qué se queda para la fase 2, para no mezclar lo que ya funciona con lo que
todavía no se ha decidido.

## 1. Las tres acciones

| Acción | Gesto | Fase | Estado |
| --- | --- | --- | --- |
| Leer el texto de la pantalla | **Volumen abajo dos veces seguidas** (≤ 500 ms) | **1** | Implementada con captura + OCR |
| Describir imágenes | **Volumen arriba dos veces seguidas** (≤ 500 ms) | **1** | Implementada con captura + ML Kit, solo imágenes |
| Abrir CECAPI | **Dos dedos hacia abajo** | 2 | Solo documentada |

Los botones de volumen son el canal principal porque no dependen de la exploración táctil ni del
modo de TalkBack. Los gestos de un dedo vuelven a ser los de Android: CECAPI no pide exploración
táctil ni los intercepta, y el perfil de gestos queda inactivo hasta que se decida reactivarlo.

## 2. Fase 1: volumen abajo dos veces

### 2.1 Detección

- Clase pura `DetectorPulsacionesVolumen`, sin dependencias de Android.
- Ventana de **500 ms** entre las dos pulsaciones de la **misma** tecla.
- Una pulsación de otra tecla reinicia la cuenta. Arriba y abajo mezclados no cuentan como doble.
- Los eventos repetidos (dedo mantenido) se ignoran; solo cuenta la primera pulsación de cada tecla.
- El detector se prueba con pruebas unitarias: una pulsación, dos dentro de la ventana, dos fuera y
  teclas mezcladas.

### 2.2 Servicio

- El servicio de accesibilidad pide `flagRequestFilterKeyEvents` y declara
  `canRequestFilterKeyEvents="true"`.
- `onKeyEvent` observa `KEYCODE_VOLUME_DOWN` y `KEYCODE_VOLUME_UP`.
- **Cada tecla se devuelve al sistema** (`return false`) para que el volumen siga cambiando normal.
- En fase 1, el doble volumen abajo lee texto y el doble volumen arriba describe imágenes. Cada
  tecla sigue contando por separado para no confundir las parejas arriba/abajo.

### 2.3 Lectura

- Se toma una **captura temporal en memoria** de la ventana activa. En Android 14 o superior se usa
  `takeScreenshotOfWindow` para que el panel de volumen no tape el texto; en versiones anteriores se
  captura la pantalla completa.
- El OCR corre en el teléfono con **ML Kit** (`TextRecognition`), el mismo modelo que ya usa el
  lector de documentos. La imagen se amplía al doble cuando la captura es pequeña.
- Se leen **todos los bloques de texto visibles** en orden de arriba hacia abajo. Los bloques de la
  misma línea se unen con un espacio y las palabras cortadas con guion se vuelven a unir.
- Antes de hablar se normalizan mayúsculas sostenidas y confusiones frecuentes del OCR (`V` por
  `Y`, `OUE` por `QUE`, `0` por `O`, palabras unidas como `PONEA`). Así el motor de voz no deletrea
  palabras desconocidas.
- También se convierten monedas y unidades a palabras: `$` se lee como pesos en México, y `28"`,
  `V`, `W`, `Hz`, `Wh`, `mAh`, etc. se leen como pulgadas, voltios, vatios, hercios, etc. La
  lectura de pantalla usa una velocidad 10% menor que la voz normal de la app.
- Si no hay texto legible, **no se habla**. No se dice "no hay nada que leer" en este gesto.
- La captura no se guarda en disco, no se muestra y no se envía por red.

### 2.4 TalkBack

- La pantalla de Accesibilidad revisa si TalkBack está activo entre los servicios de accesibilidad
  con `FEEDBACK_SPOKEN`. Se reconocen `com.google.android.marvin.talkback` y el TalkBack de Samsung
  (`com.samsung.android.accessibility.talkback`).
- La revisión se repite al volver a la pantalla (`ON_RESUME`).
- Si está activo, se muestra un aviso grande y con contraste, y un botón para abrir los ajustes de
  accesibilidad de Android.
- La app **no puede apagar TalkBack** por sí sola; el usuario debe desactivarlo en esos ajustes.
- Si el Intent de ajustes no resuelve, se muestra un mensaje en pantalla en lugar de fallar.

## 3. Fase 1: volumen arriba dos veces

### 3.1 Describir solo imágenes

- Mismo detector de doble pulsación (≤ 500 ms) con la tecla de subir volumen.
- Reutiliza la **misma captura temporal en memoria** que el volumen abajo. No se guarda en disco,
  no se muestra y no se envía a ningún servidor.
- El OCR de la captura se usa **solo para localizar las zonas con texto**. Esas zonas se tapan
  antes de etiquetar, así el etiquetador no ve letras. El texto no se describe; para eso está el
  volumen abajo.
- El etiquetado corre **en el teléfono** con ML Kit (`ImageLabeling`, el modelo que ya incluye la
  app). No usa internet, ni IA, ni el backend del Módulo 3.
- La lógica de quitar zonas de texto, filtrar etiquetas por confianza y armar la frase vive en una
  clase pura (`DescriptorImagenes`), sin dependencias de Android, con pruebas unitarias.
- Si no queda ninguna etiqueta con confianza suficiente, **no se habla**. No se dice "no hay
  imágenes" en este gesto.
- La frase no cuenta objetos exactos en esta fase ("Veo un lago, un árbol y un pájaro"): el modelo
  del teléfono no da conteos confiables. Una descripción más rica con IA queda pendiente de
  revisión con Pp (decisión 0002).
- En Android 11 o superior se usa la captura de accesibilidad; en versiones anteriores este gesto
  no hace nada, porque el servicio no puede tomar la pantalla.

### 3.2 Dos dedos hacia abajo: abrir CECAPI

- Depende del modo de exploración táctil del servicio.
- Puede chocar con TalkBack y con otros lectores de pantalla.
- Se implementará después de probar en el A55 con TalkBack encendido y apagado.

## 4. Lo que no cambia

- Los gestos de un dedo son los de Android; CECAPI no los intercepta.
- El perfil de gestos queda inactivo hasta que se decida reactivar la exploración táctil.
- Las acciones de "silencio" y "detener" no cambian.
- No se piden permisos nuevos.
- No se toca `ModuloCecapi.kt`.

## 5. Pruebas

- Unitarias: detector de pulsaciones, detección de TalkBack, composición del texto del OCR y
  descripción de imágenes (solo texto, solo imagen, baja confianza y texto + imagen).
- Compilación: `:app:assembleDebug --offline`.
- Pruebas: `:app:testDebugUnitTest --offline`.
- En el A55 (pendiente):
  - Volumen abajo dos veces lee el texto y el volumen sigue cambiando.
  - Volumen arriba dos veces describe solo las imágenes y el volumen sigue cambiando.
  - Una pantalla llena de texto no se describe con volumen arriba.
  - Una imagen con texto encima se describe sin leer ese texto.
  - Una sola pulsación no lee ni describe.
  - Dos pulsaciones separadas más de 500 ms no leen.
  - Volumen arriba y abajo mezclados no leen.
  - Con TalkBack activo aparece el aviso; al apagarlo y volver, desaparece.
  - El botón abre los ajustes de accesibilidad correctos.

## 6. Pendientes

- Probar en el A55 (no se puede hacer desde el entorno de compilación): volumen arriba con una
  foto, con una pantalla de solo texto, con texto encima de una foto y con poca luz.
- Revisar con Pp una descripción con IA para contar objetos y relaciones (decisión 0002).
- Confirmar con Pp el alcance de "dos dedos hacia abajo".
- Decidir si el aviso de TalkBack debe bloquear el uso de los gestos o solo advertir.

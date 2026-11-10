# 0008. Gestos de pantalla: el volumen como canal principal

> Vigente · Dueño: Pp · Actualizado el 27 oct 2026 contra `integracion/combinado`

## Contexto

El Módulo 10 tiene un servicio de accesibilidad y un perfil de gestos. En las pruebas del A55 se
confirmó que la exploración táctil de CECAPI estorbaba: los deslizamientos de un dedo no se sentían
como los de Android y, además, "dos dedos hacia abajo" podía chocar con TalkBack. También se probó
que el doble volumen abajo era más predecible como canal principal de lectura.

## Decisión

1. **Los botones de volumen son el canal principal** para las acciones de lectura:
   - **Volumen abajo dos veces seguidas (≤ 500 ms)**: tomar una captura temporal en memoria,
     reconocer con OCR el texto visible y leerlo en voz alta. Es la **fase 1**.
   - **Volumen arriba dos veces seguidas (≤ 500 ms)**: describir **solo las imágenes** que se ven
     en pantalla. Es parte de la **fase 1**: reutiliza la misma captura temporal, descarta las zonas
     con texto y etiqueta lo que queda con ML Kit **en el teléfono**. No cuenta objetos exactos y no
     usa IA ni internet. Las zonas con texto siguen siendo responsabilidad del volumen abajo.
2. **Dos dedos hacia abajo**: abrir CECAPI. Es la **fase 2**; todavía no se implementa.
3. **Los gestos de un dedo son los de Android**: el servicio no pide exploración táctil y no
   intercepta deslizamientos ni toques. El perfil de gestos queda inactivo hasta que se decida
   reactivar la exploración táctil.
4. La captura de la lectura y la de describir imágenes se queda **en memoria**: no se muestra al
   usuario, no se guarda en disco y no se envía por red. Una descripción enriquecida con IA queda
   como mejora pendiente de revisión con Pp, conforme a la decisión 0002 (IA apagada por defecto).
5. Cada pulsación de volumen se **devuelve al sistema** (`onKeyEvent` → `false`) para que el volumen
   siga cambiando normal.
6. Si **TalkBack está activo**, la pantalla de Accesibilidad lo avisa y ofrece abrir los ajustes de
   accesibilidad. La app no puede apagarlo por sí sola. Se reconocen el TalkBack de Google y el de
   Samsung.

## Consecuencias

- El detector de pulsaciones es código puro y se prueba sin Android.
- El servicio necesita `canRequestFilterKeyEvents` y `canTakeScreenshot`.
- La lectura del doble volumen usa la captura de accesibilidad y ML Kit en el teléfono. En Android
  14 o superior captura la ventana activa para que el panel de volumen no tape el texto; en
  versiones anteriores usa la pantalla completa.
- Se leen todos los bloques de texto visibles en orden, no solo los párrafos largos. Si no hay texto
  legible, no se habla.
- Antes de hablar se normalizan mayúsculas sostenidas y confusiones frecuentes del OCR para que el
  motor de voz no deletree palabras. También se convierten pesos/dólares, pulgadas, voltios, vatios,
  hercios, Wh y otras unidades a palabras, y la lectura va un 10% más lenta que la voz normal.
  No se guarda el texto reconocido.
- Mientras se lee, se cancela el anuncio del nivel de volumen para que no corte la lectura.
- El doble volumen arriba también cancela el anuncio del nivel. Usa la misma captura y la misma
  ventana de 500 ms; en Android 11 o superior toma la captura y en versiones anteriores no hace
  nada, porque el sistema no ofrece captura de pantalla al servicio.
- Para describir imágenes, el OCR solo se usa para localizar las zonas con texto; esas zonas se
  tapan antes de etiquetar, así el etiquetador no ve letras. Si no queda ninguna etiqueta con
  confianza suficiente, no se habla. La frase no cuenta objetos exactos
  ("Veo un lago y un árbol"), porque el modelo del teléfono no da conteos confiables.
- Queda pendiente validar el aviso de TalkBack de Samsung y el botón de ajustes con TalkBack activo.

## Fase 2 (pendiente)

- Dos dedos hacia abajo: abrir CECAPI.
- Descripción enriquecida con IA (contar objetos, relaciones, contexto) revisada con Pp y la
  decisión 0002; mientras no se autorice, todo corre en el teléfono y sin conexión.

# 0009. Cámara de personas y gafetes: local, sin caras y sin guardar

> Vigente · Dueño: Equipo 5 · Actualizado el 27 oct 2026 contra `integracion/combinado` en `bc88e20`

## Contexto

El Asistente del Entorno ya describe objetos con un detector local. Falta la parte de cámara que
sirva para una foto cotidiana con personas: decir cuántas hay, describir la ropa por color y, si se
lee con claridad, decir el nombre de un gafete. La app la usan alumnos con discapacidad visual y
puede haber menores; por eso cualquier dato de una persona es sensible.

## Decisión

1. **No hay reconocimiento facial.** El detector EfficientDet lite0 solo devuelve la clase
   `person` y una caja. No se usan modelos de caras, no se comparan rostros, no se identifica a
   nadie por su cara y no se guarda ninguna plantilla biométrica.
2. **Los gafetes se leen solo en el teléfono.** ML Kit Text Recognition corre en el dispositivo y
   únicamente sobre la zona del pecho de la persona detectada. El texto no sale del teléfono, no se
   envía a internet y no se guarda.
3. **La ropa se describe por color, sin tipo de prenda.** Se muestrean dos zonas de la caja de la
   persona y se nombra el color dominante en español. No se dice "camisa", "pantalón", "vestido" ni
   ninguna prenda: no hay clasificador de prendas y adivinarla sería inventar.
4. **Nada se guarda.** Ni la foto, ni la captura, ni el texto leído, ni el nombre del gafete se
   escriben en disco ni en la base de datos. La imagen vive solo en memoria mientras se analiza y se
   descarta al salir de la pantalla o al tomar otra foto. No se escribe en el historial.
5. **Sin red y sin IA.** Todo el análisis usa modelos incluidos en la app (EfficientDet lite0 y ML
   Kit). No se llama al backend, a Wikipedia ni a ningún servicio externo. La decisión 0002 sigue
   vigente: la IA está apagada por defecto.
6. **Las fotos de personas requieren autorización.** Quien usa la app debe tener permiso de la
   persona fotografiada. La app lo recuerda en la pantalla y no envía ni conserva la foto.
7. **Si algo es ambiguo, se dice menos.** Si la foto está oscura o movida, se avisa y no se
   describe. Si no hay confianza suficiente, se dice que no se reconoce bien. Si el gafete no se lee
   con claridad, no se menciona. Nunca se inventa un nombre, un color ni una posición.
8. **Los logs no imprimen texto leído.** Ni en depuración ni en release se registra el contenido de
   un gafete, un nombre o un letrero. Como mucho se registran etiquetas genéricas y conteos, por
   ejemplo "persona detectada, confianza 0.82".

## Consecuencias

- La lógica de posición, selección de personas, encuadre, color de ropa, filtro de nombres y
  construcción de frases es código puro (`CamaraPersonas`, `Gafete`, `CalidadFoto`) y se
  prueba con JUnit sin Android.
- La captura se hace en memoria con CameraX. No se usa la carpeta de fotos de la app para esta
  función.
- El detector se reutiliza: el modelo EfficientDet lite0 ya incluye la etiqueta `person` en su
  metadata. Se exige confianza mínima de 0.5 para contar personas.
- La ropa se muestra en dos franjas: de 20 % a 55 % y de 55 % a 95 % del alto de la caja, usando
  solo el 60 % central del ancho. Si la zona mezcla colores o está demasiado oscura, se dice "ropa
  de color no claro" en lugar de adivinar.
- El gafete se busca entre 25 % y 60 % del alto de la caja. Se acepta un bloque de 1 a 3 palabras,
  solo letras con acentos y espacios, cada palabra con mayúscula inicial, de 2 a 25 caracteres y
  fuera de la lista de palabras descartadas. Si hay varios, gana el de letra más grande.
- En Android 10 o anterior la cámara sigue funcionando, pero el análisis puede ser más lento; no se
  usa ninguna API de captura de accesibilidad para esta función.
- La descripción no reemplaza ni cambia los gestos de volumen. Volumen abajo ×2 sigue leyendo texto
  y volumen arriba ×2 sigue describiendo imágenes de pantalla, sin tocar su código.
- Queda pendiente la prueba real en un A55 y validar el muestreo de color con ropa de varios tonos,
  estampados y poca luz.

## Lo que no se hace

- No se reconoce la identidad de nadie.
- No se deduce edad, género, etnia, discapacidad ni estado de ánimo.
- No se describen caras ni rasgos físicos.
- No se guardan fotos, recortes, texto ni nombres.
- No se usan servicios externos ni IA generativa.

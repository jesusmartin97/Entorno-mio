# Propuesta: cámara de personas y gafetes del Asistente del Entorno

> Propuesta · Dueño: Equipo 5 (Jesús y Edgar) · Escrito el 27 oct 2026 contra
> `integracion/combinado` en `bc88e20` · **Fase A a D para esta semana**. La decisión que la
> respalda es la 0009.

## 1. Objetivo

Que un alumno con discapacidad visual pueda tomar una foto cotidiana y escuchar, en español y sin
internet:

- qué objetos principales hay y a qué lado están;
- cuántas personas hay y cómo va vestida la más cercana;
- el nombre del gafete, solo si se lee con claridad.

La app no reconoce caras, no identifica personas y no guarda fotos, texto ni nombres. Todo el
procesamiento ocurre en el teléfono.

## 2. Reglas que no se negocian

- Sin reconocimiento facial ni biometría.
- Sin red, sin backend, sin IA generativa y sin servicios externos.
- La foto y la captura viven solo en memoria. No se guardan en disco ni en Room.
- La ropa se describe por color; nunca se nombra una prenda.
- El gafete se lee solo en el teléfono y solo en la zona del pecho.
- Si no hay confianza suficiente, se dice menos o nada. No se inventa.
- Los logs nunca imprimen texto leído; solo etiquetas genéricas y conteos.
- Las fotos de personas requieren autorización de la persona fotografiada.
- Los gestos de volumen abajo ×2 y arriba ×2 no cambian.

## 3. Fases

### Fase A. Foto cotidiana con voz (prioridad 1)

1. Botón grande **Tomar foto** en la pantalla del Asistente del Entorno, con
   `contentDescription` que explique la cuenta regresiva y que se tomará una foto.
2. Al tocarlo: vibración corta, voz "Tomando foto en 3, 2, 1" y, después de la captura, voz
   "Foto tomada, analizando".
3. La captura se hace en memoria con CameraX; no se escribe ningún archivo.
4. Antes de describir se revisa la calidad con los mismos umbrales del lector de documentos:
   luminancia media menor a 45 = muy oscura; varianza del laplaciano menor a 60 = movida. En esta
   rama no existe `LuminanciaUtil`; la revisión se encapsula en `CalidadFoto`, código puro.
5. Si está oscura o movida, se avisa en voz y **no se describe**.
6. Si está bien, se reutiliza el pipeline de `EnvironmentRepository` (detector EfficientDet lite0,
   etiquetador ML Kit y `describirFotoCompleta` como respaldo) para decir hasta 3 objetos
   principales, ordenados por confianza, con posición "a tu izquierda", "al centro" o
   "a tu derecha".
7. Si no hay nada con confianza suficiente: "No reconozco bien lo que hay. Intenta con más luz o
   acércate."
8. La última descripción se guarda **solo en memoria**. El botón **Repetir** la vuelve a decir sin
   tomar otra foto.

### Fase B. Personas (prioridad 1)

9. Se usa el detector existente; la clase `person` existe en la metadata de
   `efficientdet_lite0.tflite` (verificado en `labels.txt`). Confianza mínima: 0.5, en una
   constante.
10. Sin personas por encima del umbral: no se menciona ninguna persona.
11. Con varias personas: se dice el número ("Hay 3 personas") y se describe la de mayor caja, que es
    la más cercana.
12. Antes de describir la ropa se revisa el encuadre:
    - caja con alto menor a 20 % de la imagen: "La persona está lejos, acércate.";
    - caja a menos de 10 % de cualquier borde: "La persona está cortada, aléjate un poco."
13. Ropa: dos franjas dentro de la caja, de 20 % a 55 % y de 55 % a 95 % del alto, usando el 60 %
    central del ancho. Se obtiene el color dominante con `NombreColor`.
14. Nombres posibles: blanco, negro, gris, azul, rojo, verde, amarillo, café, naranja, rosa y
    morado. Si la franja mezcla colores o está demasiado oscura, se dice "ropa de color no claro".
15. Frase de ejemplo: "Hay una persona al centro con ropa blanca arriba y negra abajo." Nunca se
    dice "camisa" ni "pantalón".

### Fase C. Gafete (prioridad 1)

16. Dentro de la caja de la persona se busca texto con ML Kit entre 25 % y 60 % del alto, dentro de
    su ancho.
17. Un bloque se acepta como nombre solo si:
    - tiene de 1 a 3 palabras;
    - solo tiene letras con acentos y espacios;
    - cada palabra empieza con mayúscula;
    - mide entre 2 y 25 caracteres;
    - no está en la lista de palabras descartadas: CECAPI, ALUMNO, ALUMNA, MAESTRO, MAESTRA,
      VISITANTE, PERSONAL, GAFETE, NOMBRE, UTD.
18. Si hay varios candidatos, gana el primero que pase el filtro, revisando línea por línea del
    texto leído.
19. Con nombre: "Hay una persona al centro con ropa azul arriba y negra abajo que tiene un gafete
    que dice Dylan."
20. Si el texto del pecho no pasa el filtro, no se menciona. Si no se lee con claridad, tampoco.
21. No se registra el texto reconocido en ningún log.

### Fase D. Utilidades (prioridad 2)

22. Vibración corta al terminar el análisis, con el patrón que ya usa la app.
23. El botón **Repetir** funciona igual después de una foto con personas: repite la última frase
    sin volver a analizar.

## 4. Diseño técnico

- **Clases puras** (sin Android, con JUnit):
  - `CamaraPersonas`: posición, selección de personas, encuadre, color de ropa, frases.
  - `Gafete`: validación y elección del nombre.
  - `CalidadFoto`: oscura/movida a partir de una matriz de grises.
- **Capa Android** en `EnvironmentRepository`:
  - captura en memoria;
  - detector EfficientDet lite0 para `person` y objetos;
  - ML Kit Text Recognition solo en el pecho;
  - `NombreColor` para la ropa;
  - sin escrituras a Room ni al sistema de archivos.
- **UI** en `EnvironmentScreen` y `EnvironmentViewModel`:
  - botón grande, cuenta regresiva, voz y vibración;
  - vista previa en memoria;
  - botón Repetir con la última frase;
  - sin rutas de archivo para esta función.
- **Nada compartido se modifica**: no se toca `core/`, no se toca Módulo 4 y no se toca el código
  de los gestos de volumen.

## 5. Criterios de aceptación

- Persona con gafete: dice el nombre.
- Persona sin gafete: describe posición y ropa.
- Sin personas: no menciona personas.
- Foto oscura o movida: avisa y no describe.
- Repetir: dice la última descripción.
- Volumen abajo ×2 y volumen arriba ×2 siguen igual.
- Sin reconocimiento facial.
- Sin guardado de fotos ni capturas.
- Sin logs con texto leído.

## 6. Pruebas unitarias

- Posición con cajas de ejemplo (izquierda, centro, derecha).
- Selección de persona: una, varias, ninguna y confianza baja.
- Muestreo de ropa con listas de colores, sin bitmaps.
- Filtro de nombres: acepta "Dylan" y "María López"; rechaza CECAPI, alumno, 123, ABC123, "a",
  minúsculas y frases largas.
- Constructor de frases: con gafete, sin gafete, varias personas, ropa no clara y sin personas.
- Foto sin personas: no genera frase de ropa.

## 7. Pendiente

- Prueba real en el A55: foto de una persona con y sin gafete, ropa clara y oscura, estampados,
  poca luz, persona lejos y persona cortada.
- Ajustar umbrales de color y nitidez con fotos reales.
- Validar el aviso de autorización con el equipo y con Pp.

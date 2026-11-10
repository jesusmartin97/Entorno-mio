# Propuesta: obstáculos con la cámara (Módulo 7)

> Propuesta · Dueño: Equipo 5 (Jesús y Edgar) · Escrito el 19 oct 2026 contra `integracion/combinado` en `2ddc774` · **No integrado**: solo propuesta para que la revise Pp. El Módulo 7 no cambia de comportamiento.

Este documento es una **propuesta**, no una función. No avisa por su cuenta: solo dice cómo se
podría decidir, con el detector que ya existe, que algo es un obstáculo, a qué tamaño de caja
avisar, qué frase decir y qué pruebas hacer con fotos reales.

## 1. Qué detector existe hoy

- Archivo: `app/src/main/assets/efficientdet_lite0.tflite`, cargado en
  `feature/modulo7_entorno/EnvironmentRepository.kt` con la librería *TensorFlow Lite Task Vision*
  (`org.tensorflow:tensorflow-lite-task-vision` 0.4.4).
- Modelo: **EfficientDet-Lite0**, entrenado con las **80 clases de COCO** (persona, silla, mesa,
  auto, mochila, botella, perro, …). No conoce "escalón", "banqueta", "hoyo" ni "puerta": esas
  etiquetas **no** salen del modelo.
- Lo que devuelve (`Detection` de la Task Library), por cada objeto:
  - `categories[0].label`: la clase de COCO en inglés (por ejemplo `"chair"`).
  - `categories[0].score`: qué tan seguro está, de 0 a 1.
  - `boundingBox` (`RectF`): la caja en píxeles de la foto.
- Traducción al español: `feature/modulo7_entorno/EtiquetasEntorno.kt`
  (`traducir(etiqueta)`, `esDemasiadoGeneral`, `esIgnorada`, `esDebil`, `admiteColor`).
- Hoy la descripción **no** es un aviso de obstáculo: arma una frase general ("Veo una silla de
  color negro a la izquierda.") y guarda el historial. No estima distancia ni decide "cuidado".

Constantes actuales (`EnvironmentRepository`, al final del archivo):
`CONFIANZA_MINIMA = 0.4`, `CONFIANZA_RESPALDO = 0.55`, `CONFIANZA_SEGURA_DETECTOR = 0.6`,
`CONFIANZA_SEGURA_RESPALDO = 0.8`, `MAX_OBJETOS = 5`, `LADO_MAXIMO = 1280`. Cada objeto ya lleva
`posicion` (izquierda/centro/derecha, con `posicionHorizontal`) y `area` (ancho × alto de la caja).

## 2. Qué clases contarían como obstáculo

Un obstáculo es algo que **corta el paso a la altura del cuerpo o en el piso**, o algo que se mueve
y puede alcanzar a la persona. Con las clases de COCO que el modelo sí conoce, se agruparía así:

| Grupo | Clases de COCO | Por qué es obstáculo |
| --- | --- | --- |
| Muebles que se atravisesan | `chair`, `couch`, `bed`, `dining table`, `bench`, `toilet` | Están a la altura de las piernas o del torso y no se ven con bastón a tiempo |
| Cosas en el piso | `backpack`, `handbag`, `suitcase`, `bottle`, `cup`, `bowl`, `skateboard`, `skis`, `snowboard` | Tropezadero bajo; se ven poco |
| Voluminosos de casa | `refrigerator`, `oven`, `sink`, `toaster`, `microwave`, `tv`, `potted plant`, `vase` | Grandes y fijos; golpe al avanzar |
| Personas y animales | `person`, `dog`, `cat` | Pasan frente; hay que avisar aunque se muevan |
| Vehículos | `bicycle`, `motorcycle`, `car`, `bus`, `truck`, `train`, `boat` | Grandes y peligrosos, sobre todo en la calle |
| Postes y señales | `traffic light`, `fire hydrant`, `stop sign`, `parking meter` | Fijos a la altura de la cabeza o el torso |

**No** se contarían como obstáculo: comida, utensilios de mesa chicos, aves, juguetes, etc. Tampoco
se inventarían clases: `door`, `wall`, `stairs` **no** existen en COCO, así que con este modelo no se
pueden anunciar. Anunciarlos sería inventar (regla de la entrega).

## 3. A qué distancia o tamaño de caja avisar

El modelo **no da distancia en metros** (es una sola cámara, sin profundidad). Lo único honesto es
usar el tamaño de la caja como medida relativa de cercanía: un objeto que ocupa casi toda la foto
está más cerca que uno chico.

- Proporción de la caja: `p = (ancho_caja × alto_caja) / (ancho_foto × alto_foto)`.
- Proporción de la altura: `h = alto_caja / alto_foto` (mejor que el área cuando algo es alto y
  angosto, como una persona o un poste).

Bandas propuestas (a **calibrar** con fotos reales; son un punto de partida, no un número final):

| Banda | Criterio (cualquiera de los dos) | Qué significa |
| --- | --- | --- |
| **Muy cerca** | `h ≥ 0.60` o `p ≥ 0.25` | A un paso o menos; avisar de inmediato |
| **Cerca** | `h ≥ 0.30` o `p ≥ 0.10` | A dos o tres pasos; avisar |
| **Lejos** | todo lo demás | No es obstáculo inmediato; no avisar (solo entra en la descripción normal) |

Regla de aviso: avisar **solo** si la clase está en la tabla del punto 2 **y** la banda es *muy
cerca* o *cerca*, con `score ≥ 0.5` (más exigente que la descripción normal, porque un falso aviso
asusta). Si hay varios, decir primero el **más cercano** y como mucho **dos**.

La posición se toma de `posicionHorizontal` (izquierda / al centro / derecha).

## 4. Qué frase decir

Frases cortas, siempre con la palabra "cuidado" delante para que se distinga de una descripción:

- Muy cerca: "Cuidado, silla muy cerca a la izquierda."
- Cerca: "Cuidado, mesa cerca al centro."
- Varios: "Cuidado, silla a la izquierda y mochila en el piso al centro."

Si no hay nada de la tabla o nada suficientemente cerca: **no** se dice "cuidado"; se deja la
descripción que ya existe ("Veo …") tal como está hoy.

Nota de precisión (para no afirmar de más): como no hay distancia real, conviene decir "cerca" o
"muy cerca" y **nunca** "a un metro" ni "a tres pasos".

## 5. Pruebas con fotos reales

Todavía no hay nada que correr: esto es lo que habría que probar **antes** de integrar.

1. **Carta de pruebas**: con el mismo teléfono (el Oppo), sacar cada objeto de la tabla a tres
   distancias (≈0.5 m, 1.5 m, 3 m), de frente y a 45°, con buena luz y con poca luz.
   - Silla sola en un pasillo.
   - Mesa y sillón de casa.
   - Mochila y maleta en el piso.
   - Refrigerador y estufa.
   - Persona de pie de frente y de lado.
   - Bicicleta y auto (en patio o calle cerrada, sin riesgo).
   - Bote de basura, poste y tope (para ver qué **no** reconoce y no anunciar).
2. **Qué anotar por foto**: qué dijo la app, si avisó cuando debía, si avisó de algo que no era
   obstáculo (falso positivo) y si dejó pasar algo que sí lo era (falso negativo).
3. **Calibrar las bandas** del punto 3 con esas fotos: subir o bajar `h`/`p` hasta que "muy cerca"
   coincida con "a un paso".
4. **Objetos que el modelo no conoce** (puerta cerrada, escalón, hoyo): confirmar que **no** avisa,
   para dejar claro que esta propuesta no sustituye bastón ni perro guía.
5. **Repetición**: la misma escena 10 veces para ver si el aviso es estable o tiembla.

## 6. Qué NO hace esta propuesta

- No cambia el Módulo 7 (ni `EnvironmentRepository`, ni la descripción, ni el historial).
- No promete distancia en metros ni detecta lo que COCO no conoce.
- No decide sola: queda para revisión de Pp antes de escribir una línea de código.

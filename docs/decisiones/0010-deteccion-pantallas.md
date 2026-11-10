# 0010. Detección de pantallas, laptops y periféricos: decir menos y no confundir

> Vigente · Dueño: Equipo 5 · Actualizado el 28 oct 2026 contra `integracion/combinado` en `8665429`

## Contexto

El lector del entorno ya detecta objetos con EfficientDet lite0 y usa ML Kit Image Labeling como
respaldo. Las pantallas, laptops, teclados, ratones y controles remotos se parecen entre sí (y una
ventana o un reflejo puede parecer una pantalla), así que una traducción uno a uno producía frases
poco confiables como "televisor" o "computadora" sin base suficiente.

En el Asistente del Entorno (foto en memoria) llegaron dos confusiones concretas de uso real: una
laptop descrita como "celular" y un monitor descrito como "silla de color negro". El diagnóstico con
35 fotos mostró que el detector marca la pantalla de la laptop como `cell phone` (confianza
0.41–0.57, cajas de 7–12 % de ancho) y la base del monitor como `chair` (confianza 0.59, caja de
31.2 % × 19.6 %, solo 6.1 % del área).

## Etiquetas reales revisadas

Del archivo `efficientdet_lite0.tflite` (metadata `labels.txt`, 90 clases COCO) se usan solo estas,
porque son las que existen para este grupo:

- `tv`
- `laptop`
- `keyboard`
- `mouse`
- `remote`

Del modelo incluido en ML Kit Image Labeling 17.0.9 (`0-labels-en.txt`, 447 clases) **solo existen**
estas dos para el grupo:

- `Computer`
- `Television`

No se usan `Monitor`, `Laptop`, `Keyboard`, `Mouse` ni `Remote` de ML Kit porque ese modelo no las
tiene. Tampoco se usan `screen`, `display device` ni `computer monitor`: no son etiquetas reales de
los dos modelos que corren en el teléfono.

## Decisión

1. **Una clase pura decide el grupo.** `TraductorPantallas` recibe etiquetas, confianza, posición y
   origen (detector o ML Kit). No usa Android, red ni archivos, y se prueba con JUnit.
2. **`tv` sola se dice "pantalla".** Nunca se afirma "televisor" ni "monitor". Si además hay un
   control remoto, se agrega la duda "parece televisor o monitor" sin convertirla en afirmación.
3. **Regla de conjunto.** Pantalla + teclado + ratón en la misma foto se dicen como
   "computadora de escritorio: pantalla, teclado y ratón". Si además hay laptop, se dice
   "computadora de escritorio o laptop". La regla necesita las tres señales; una pantalla sola
   sigue siendo "pantalla".
4. **Laptop manda sobre pantalla suelta.** Si el detector vio `laptop`, se dice "laptop" aunque ML
   Kit también haya visto `Television`, porque es más específico y evita contradecirse.
5. **Teclado, ratón y control remoto solos se dicen por su nombre.** `keyboard` → "teclado",
   `mouse` → "ratón", `remote` → "control remoto".
6. **ML Kit es segunda opinión, no primera.** Solo se consulta cuando el detector ya vio alguna
   señal del grupo. `Computer` y `Television` con confianza alta se reconcilian con el detector y
   se funden en una sola frase: una pantalla no se repite.
7. **Si no hay claridad, se calla.** Las etiquetas de pantalla del respaldo (`Computer`,
   `Television`) se excluyen del camino genérico para que nunca reaparezcan como "televisor",
   "monitor" o "computadora" por su cuenta.
8. **Umbrales por clase, en constantes.** Pantalla `0.65`, laptop `0.55`, periféricos `0.50`,
   control remoto `0.55` y segunda opinión de ML Kit `0.75`. La pantalla pide más confianza para no
   confundirse con ventanas o rectángulos brillantes. Se pueden ajustar con fotos reales.
9. **Sin cambios de privacidad.** Todo corre en el teléfono, la foto sigue solo en memoria y no se
   guardan fotos ni textos. Los logs no imprimen nombres ni texto leído; como mucho, conteos.
10. **La pantalla conserva su diseño anterior.** La lógica nueva (cuenta regresiva, calidad,
    personas, ropa, gafete y vibración) se mantiene detrás del obturador redondo y los botones que
    ya existían.
11. **`cell phone` necesita caja y confianza.** Una detección `cell phone` solo se dice "celular" si
    la caja no pasa del 55 % del ancho (los celulares reales medidos llegan a 53.3 %) y la confianza
    llega a `0.60`. Una caja más ancha o apaisada (aspecto ancho/alto mayor que 1.2) no es un
    celular: se dice "pantalla" o nada. Sin caja no se afirma nada.
12. **"Laptop" necesita una segunda señal.** Que una caja sea ancha no autoriza a decir "laptop":
    hacen falta un `keyboard` en la misma foto o la segunda opinión de ML Kit (`Computer`, que es la
    única etiqueta de ese grupo que existe en su modelo; `Laptop` no existe). Si no, se queda en
    "pantalla".
13. **`chair` necesita área y confianza.** Una silla solo se menciona si su caja ocupa más del 30 %
    de la foto, la confianza es al menos `0.60` y en la imagen no hay `tv`, `laptop` ni `keyboard`.
    Así se elimina la base del monitor (6.1 % del área) y las sillas del fondo.
14. **`tv` siempre se dice "pantalla".** También en la descripción normal, no solo en el Asistente.
    Sin una señal adicional (por ejemplo, un control remoto) no se afirma "televisor" ni "monitor".
15. **El color es opcional y exige confianza.** Se dice solo si el detector llega a `0.75` y un
    color domina al menos el 45 % de la zona muestreada. Si no, se omite el color en lugar de
    arriesgar "silla de color negro".
16. **Sin señales suficientes se dice la verdad.** La frase queda en "No reconozco bien lo que hay."
    antes que inventar un objeto o un color.

## Diagnóstico

Se agregó un log interno, apagado salvo que se pida, con la etiqueta, la confianza y el tamaño de
cada caja en porcentajes de la foto:

```
adb logcat -s EntornoDetecciones:D
```

Nunca imprime texto leído, nombres de personas ni rutas; como mucho, nombres de clases COCO y
conteos. El resumen de cada foto dice cuántas señales de pantalla y cuántas de ML Kit hubo.

Con ese log y el mismo `efficientdet_lite0.tflite` se midieron 35 fotos reales (10 laptop, 5
monitor, 10 celular y 10 silla), aplicando el umbral de la app (`0.40`). Los datos que importan:

| Observación | Confianza | Ancho de caja | Área | Aspecto |
|---|---|---|---|---|
| `cell phone` falso en laptops | 0.41–0.57 | 7.5–10.7 % | 0.4–2.0 % | 0.35–0.58 |
| `cell phone` real en celulares | 0.50–0.79 | 32.9–53.3 % | 16.6–20.2 % | 0.60–1.00 |
| `chair` falso en el monitor | 0.59 | 31.2 % | 6.1 % | 0.63 |
| `chair` real en sillas | 0.50–0.74 | 52–66 % | 42.6–56.6 % | 0.55–0.85 |

Conclusión del diagnóstico: los falsos "celular" de laptop se quitan sobre todo **subiendo la
confianza a 0.60** (ninguno llegaba), y los falsos "silla" del monitor se quitan con **área > 30 %**
(la base del monitor ocupa 6.1 %).

## Validación con fotos reales

Las 35 fotos viven fuera del repositorio, en `/home/jesus62/validacion_entorno`. Para no depender
del teléfono se corrió el mismo modelo con MediaPipe 1.2.0 en la computadora y se reprodujeron las
reglas de `TraductorPantallas` y `ReglasEntorno`. La segunda opinión de ML Kit no se puede correr
en la computadora, así que los casos de monitor pueden mejorar en el teléfono.

| Caso | Fotos | Resultado con las reglas |
|---|---|---|
| laptop | 10 | 9 "laptop"; la décima trae una persona y va por la ruta de personas |
| monitor | 5 | 5 "teclado" en la computadora (sin segunda opinión de ML Kit); la silla del monitor ya no aparece |
| celular | 10 | 5 "celular", 4 "mesa" y 1 "control remoto" (ver límite abajo) |
| silla | 10 | 6 "silla"; 4 "No reconozco bien lo que hay" (confianza menor a 0.60) |
| escena vacía | 0 | pendiente por decisión del equipo: no se capturaron las 5 fotos |

**Ajuste aplicado tras validar:** el ancho máximo para celular se subió de `0.25` a `0.55`, porque
los celulares reales ocupan 33–53 % del ancho y las pantallas de laptop 81–99 %. Aun así, 5 de 10
fotos de celular dicen "celular"; las otras 4 tienen confianza 0.50–0.59 y se quedan por debajo de
`UMBRAL_CELULAR = 0.60`, que es el valor que elimina los falsos positivos de laptop (0.41–0.57). Si
se quisiera recuperar una foto más sin recuperar ningún falso positivo, el siguiente paso sería
bajar la confianza a `0.58`, no tocar el ancho. No se aplicó para no debilitar la regla sin otra
medición en el teléfono.

## Lo que no se puede distinguir

- **Televisor contra monitor:** sin una señal adicional solo se puede decir "pantalla". Un control
  remoto sugiere televisor, pero no lo confirma.
- **Computadora de escritorio sola:** una etiqueta `Computer` de ML Kit no distingue una torre, un
  monitor o una laptop; por eso se dice "pantalla" salvo que el conjunto pantalla + teclado + ratón
  esté completo.
- **Laptop contra computadora de escritorio:** el detector puede ver una laptop como `laptop`, pero
  no separa sus partes; la regla de conjunto es la única que permite la frase "de escritorio o
  laptop".
- **Monitor como etiqueta:** ML Kit 17.0.9 no tiene `Monitor`; no se inventa.
- **Confianza baja:** si no se alcanza el umbral de la clase, no se describe ese objeto.

## Consecuencias

- Se agregó `TraductorPantallas` y `TraductorPantallasTest` con pruebas de traducción, conjunto,
  contradicciones, umbrales y etiquetas inexistentes.
- Se agregó `ReglasEntorno` (código puro) y `ReglasEntornoTest` con las reglas de tamaño, área y
  confianza de celulares y sillas, más el filtro de color.
- `EnvironmentRepository.analizarFotoEnMemoria` separa el grupo de pantalla del resto de objetos,
  pide la segunda opinión solo cuando hace falta, descarta sillas chicas o con pantalla al lado y
  aplica el filtro de celular antes de describir.
- El respaldo genérico excluye pantallas para no volver a afirmar televisores o monitores.
- `tv` y `television` se traducen a "pantalla" también en la descripción normal.
- El log `EntornoDetecciones` queda disponible para volver a medir en el teléfono sin exponer texto
  leído, nombres ni rutas.
- No se tocaron las monedas, las personas, el gafete, la interfaz ni el guardado de historial.
- Queda pendiente: capturar las 5 fotos de escena vacía y correr la validación dentro del teléfono
  (la segunda opinión de ML Kit no se pudo medir en la computadora).

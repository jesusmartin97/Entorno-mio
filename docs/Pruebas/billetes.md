# Pruebas de billetes (Asistente del Entorno)

Cómo usarlo: en la pantalla del Asistente del Entorno di **"qué billete es"** (o toca el botón del mismo nombre).
La app toma la foto y dice de cuánto es. Filtro de Logcat: `EntornoBillete` (lo que leyó el OCR y las denominaciones que encontró) y `EntornoTiempo` (tiempo total).

Cómo decide: lee los números y palabras impresas con ML Kit (los números más grandes pesan más), y usa el color solo
como apoyo. Si en la foto hay dos o más denominaciones distintas con su número grande, las dice todas (de mayor a menor).
No cuenta billetes repetidos ni suma el total: dos billetes de 100 salen como uno solo. Si no hay una lectura clara dice "no estoy segura" en lugar de adivinar. Si la foto sale de lado prueba
girarla 90 y 270 grados.

| Billete | Luz | Distancia | Esperado | Real (anotar) |
| :-- | :-- | :-- | :-- | :-- |
| 20 | buena | 30 cm, plano | "Es un billete de veinte pesos." | |
| 50 | buena | 30 cm, plano | "Es un billete de cincuenta pesos." | |
| 100 | buena | 30 cm, plano | "Es un billete de cien pesos." | |
| 200 | buena | 30 cm, plano | "Es un billete de doscientos pesos." | |
| 500 | buena | 30 cm, plano | "Es un billete de quinientos pesos." | |
| 1000 | buena | 30 cm, plano | "Es un billete de mil pesos." | |
| cualquiera | buena | 30 cm, de lado (horizontal / vertical) | igual que arriba | |
| cualquiera | poca luz | 30 cm | la denominación o "no estoy segura" (nunca una equivocada) | |
| cualquiera | buena | 1 m | "no alcanzo a leer cuánto es" | |
| 100 y 50 juntos, separados | buena | 30 cm | "Veo billetes de cien pesos y de cincuenta pesos." | |
| 500, 100 y 20 juntos, separados | buena | 30 cm | "Veo billetes de quinientos pesos, de cien pesos y de veinte pesos." | |
| 100 y 100 juntos | buena | 30 cm | "Es un billete de cien pesos." (los repetidos no se cuentan; es una limitación conocida) | |
| 100 y 50 encimados (uno tapa parte del otro) | buena | 30 cm | las dos denominaciones, o "no estoy segura"; nunca una sola dicha con seguridad si se ven las dos | |
| 100 cerca y 50 lejos | buena | 30 cm y 60 cm | las dos, o solo la del billete cercano (anotar cuál sale) | |
| hoja con "100" | buena | 30 cm | no debe confirmar billete si la lectura es dudosa | |

Anotar también: modelo del teléfono, billete nuevo o viejo (polímero o papel), y si la respuesta tardó más de 3 segundos.
Cualquier respuesta equivocada con voz segura es un error grave: anotarla con su captura del Logcat.

## Varios billetes y cuenta (conteo)

Foto con varios billetes: dice cuántos hay de cada denominación ("Veo un billete de cincuenta pesos y dos billetes de
veinte pesos") y avisa que el conteo es aproximado. Cuenta los billetes iguales agrupando sus números grandes por
posición: billetes **separados** se cuentan bien; billetes **pegados, encimados o doblados** se cuentan de menos.

Cuenta exacta, de uno en uno: "agrega este billete" (foto de un solo billete, lo anota), "cuánto llevo" (dice cuántos
y el total), "borra los billetes" (empieza de cero). Si en la foto hay más de un billete o no se lee, no suma nada.

| Prueba | Esperado | Real (anotar) |
| :-- | :-- | :-- |
| 2 billetes de 20 separados (≈ un billete de distancia) | "dos billetes de veinte pesos" | |
| 2 de 20 y 1 de 50 separados | "un billete de cincuenta y dos de veinte" | |
| 2 de 20 pegados | puede decir uno solo (límite conocido) | |
| 2 de 20 uno encima del otro | puede decir uno solo (límite conocido) | |
| 3 billetes de 100 en fila separados | "tres billetes de cien pesos" | |
| "agrega este billete" ×3 (20, 20, 50) y "cuánto llevo" | "Llevas un billete de cincuenta y 2 de veinte... 90 pesos" | |
| "agrega este billete" con 2 billetes en la foto | "No sumé nada..." | |
| "borra los billetes" y luego "cuánto llevo" | "Todavía no has sumado ningún billete" | |

## Objetos básicos, partes del cuerpo y color (Entorno)

Probar con "¿qué hay enfrente?" y revisar Logcat (`EntornoEtiquetas`, `EntornoTiempo`):
- Mano, cabeza, brazo, dedos, cara, pelo: deben nombrarse (sin color).
- Celular, lápiz, pluma, goma, estuche, cuaderno, escritorio, computadora, almohada: se nombran con su color ("de color azul").
- Si la confianza es baja, dice "posiblemente ...".
- Si no reconoce nada, dice el color liso que ve.
Los colores se estiman desde la caja del objeto; luz mala o fondos del mismo color pueden fallar.

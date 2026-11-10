# 0006. "Silencio" y "para" solo como frase completa

> Vigente · Dueño: Pp · Verificado el 28 sep 2026 contra integracion/actividades en 8019dd1

## Decisión (Pp, 25 sep 2026)

| Orden | Frases que la activan | Qué hace |
| --- | --- | --- |
| Silencio | silencio, espera, espérame, cállate, un momento, un segundo | Calla al asistente. Vuelve con "hola" o con su nombre |
| Para | para, detente, alto, basta, para ya, ya para, hasta luego, adiós, apágate | Detiene todo hasta que se vuelve a abrir la app |

Solo funcionan si son la frase completa, porque "para" también es una palabra común ("para qué sirve"). Se atienden antes que cualquier otro comando, en `VoiceEngine.handleControl`, y funcionan sin decir "hola".

## Pendiente

El hallazgo P-09 del Agente 1 propone dejar solo "para" y "detente" como orden de detener. Decide Pp.

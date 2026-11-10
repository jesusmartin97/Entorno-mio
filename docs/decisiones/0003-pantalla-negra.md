# 0003. Pantalla negra con salida por voz y por toque

> Vigente · Dueño: Pp · Verificado el 28 sep 2026 contra integracion/actividades en 8019dd1

## Contexto

Una persona ciega puede preferir la pantalla en negro, por batería o por privacidad. El riesgo es quedarse sin forma de volver.

## Decisión

- "Pantalla negra" y "brillo mínimo" funcionan en cualquier pantalla.
- Siempre hay dos salidas: decir "pantalla normal" (o uno de sus sinónimos) y mantener presionada la pantalla (`core/ui/BlackScreen.kt`).
- Los comandos de pantalla se atienden antes que cualquier otro comando global (`GlobalVoiceCommands.handle`), para que la salida funcione siempre.

## Consecuencias

- Falta comprobar en un teléfono que la salida funciona después de "silencio" y de "para" (encargo del Agente 1).

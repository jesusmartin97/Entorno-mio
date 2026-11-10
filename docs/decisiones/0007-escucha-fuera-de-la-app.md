# 0007. Escuchar fuera de la app: opcional y apagado

> Vigente · Dueño: Pp · Verificado el 28 sep 2026 contra integracion/actividades en 8019dd1

## Decisión (Pp, 25 sep 2026)

- Se activa en Configuración y empieza apagado (`DeviceSettings.backgroundListening` empieza en falso).
- Funciona como servicio en primer plano, con un aviso fijo y un botón "Detener" (`service/ListeningService.kt`).
- Fuera de la app solo responde a los comandos generales. A todo lo demás contesta que abras la app.

## Consecuencias

- Android 11 y superiores limitan el micrófono en segundo plano, y ColorOS (Oppo) cierra procesos. Nada de esto se ha probado en un teléfono.
- Queda como opción futura un detector local de "hola" (Vosk o Porcupine), por revisar.

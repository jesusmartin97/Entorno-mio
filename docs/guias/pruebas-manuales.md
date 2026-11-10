# Pruebas manuales y cómo reportar un fallo

> Vigente · Dueño: Agente 1 (pruebas) y Pp · Revisado el 28 sep 2026

## Probar en el teléfono

La lista guiada de 30 minutos la escribe el Agente 1. Está en su rama `pruebas/agente1`, en `docs/agentes/pruebas/lista-telefono.md`. Hoy vive en la copia de trabajo `CECAPI-App-pruebas`, que va en 173fa87, y todavía no está en esta carpeta. Se prueba solo en el Oppo CPH2387 de Pp y con Pp presente.

## Reportar un fallo

Cada fallo se escribe con este formato, que usan todos los agentes:

| Campo | Qué poner |
| --- | --- |
| ID | Letra y número: P (pruebas), S (seguridad), O (optimización), N (normativa), D (documentación). Ejemplo: P-01 |
| Severidad | Crítico, alto, medio o bajo |
| Dónde | Archivo y línea, pantalla o documento |
| Qué pasa | Una o dos frases |
| Cómo reproducirlo | Pasos cortos |
| Sugerencia | Qué cambiar, sin aplicarlo |
| Estado | Nuevo, confirmado, corregido, verificado o descartado |

- Separa lo que viste (lo corriste, lo leíste en el código) de lo que supones.
- Anota el commit contra el que probaste.
- Quien encontró el fallo lo vuelve a revisar después de que se corrige.

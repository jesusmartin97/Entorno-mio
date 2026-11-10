# Documentación de CECAPI

> Vigente · Dueño: Pp (redacta el Agente 2) · Verificado el 29 sep 2026 contra integracion/actividades en d5ffd5e

CECAPI es una app Android para personas ciegas o con muy poca visión que se maneja casi solo por voz. Cada documento empieza con una línea que dice si está vigente, quién es su dueño y contra qué commit se revisó.

Para compartir con los equipos: [Documentación CECAPI](https://claude.ai/artifact/1Cawemf7QziJfgzUkGFZ62), una página con todo esto y las ligas a cada página del proyecto. Se genera con `python docs/herramientas/generar_pagina.py`.

## En el repositorio

| Para | Documento |
| --- | --- |
| Empezar | [README del proyecto](../README.md): qué es, cómo abrirlo y las cuentas de prueba |
| Colaborar | [Cómo colaborar](guias/colaborar.md): ramas, PR y qué no tocar |
| Probar | [Pruebas manuales y cómo reportar un fallo](guias/pruebas-manuales.md) |
| Consultar | [Comandos de voz](referencia/comandos.md), generada desde el código |
| Consultar | [Funciones recientes y cuentas de demostración](referencia/funciones-y-cuentas.md) |
| Consultar | [Módulos, equipos y tablas](referencia/modulos.md) |
| Consultar | [Qué sale del teléfono](referencia/que-sale-del-telefono.md), función por función |
| Consultar | [Glosario](referencia/glosario.md) |
| Entender | [Cómo funciona la voz](explicacion/voz.md) |
| Decidir | [Registro de decisiones](decisiones/README.md): 0001 a 0007 |
| Seguir | [Registro de cambios](CHANGELOG.md), una entrada por semana |

## Informes de los agentes

| Agente | Informe | Base |
| --- | --- | --- |
| 1. Pruebas | En la rama `pruebas/agente1`, carpeta `docs/agentes/pruebas/`. Todavía no está aquí | 173fa87; ronda 2 sobre 8019dd1 |
| 2. Documentación | [Informe](agentes/documentacion/informe.md) (8019dd1) y [ronda 2](agentes/documentacion/ronda-2.md) (d5ffd5e) | d5ffd5e |
| 3. Seguridad | [Resumen](agentes/seguridad/README.md), [informe](agentes/seguridad/informe-seguridad.md) (8019dd1), [ronda 2](agentes/seguridad/informe-seguridad-ronda2.md) (d5ffd5e), [modelo de amenazas](agentes/seguridad/modelo-de-amenazas.md) y parches sin aplicar en `agentes/seguridad/parches/` | d5ffd5e |
| 4. Optimización | [Informe previo](agentes/optimizacion/informe-previo-ronda2.md), [análisis sin teléfono](agentes/optimizacion/informe-analisis-estatico.md) (8019dd1) y [ronda sobre d5ffd5e](agentes/optimizacion/informe-ronda-d5ffd5e.md). Falta medir en el teléfono | d5ffd5e |
| 5. Normativa | [Informe](agentes/normativa/README.md) (173fa87) y [ronda 2](agentes/normativa/ronda-2.md) (8019dd1). **Sus textos legales son borradores para revisión de abogado** | 8019dd1 |

También está [Mapa de trabajo CECAPI](agentes/diagrama-organizacion.html), un diagrama en HTML dentro de `docs/agentes/`. Por confirmar quién lo hizo y si sigue vigente.

## Páginas publicadas

Las páginas se comparten desde claude.ai. Las marcadas como desactualizadas no se muestran al maestro hasta corregirlas.

| Página | Para quién | Estado |
| --- | --- | --- |
| [Documentación CECAPI](https://claude.ai/artifact/1Cawemf7QziJfgzUkGFZ62) | Todos | Vigente; es la entrada a todo lo demás |
| [Tareas por equipo, 26 sep al 4 oct](https://claude.ai/artifact/HBF3rJTytZj34HZ9H1bWon) | Equipos | Vigente; nota del 29 sep sobre la IA |
| [Reporte de avances](https://claude.ai/artifact/9xB69wgCjSYpXpQdm9gHSt) | Pp | Vigente; formato nuevo desde el 2 oct |
| [Encargos para agentes](https://claude.ai/artifact/4XvQVvFmzZFaVFqqT9RVeM) | Pp y agentes | Vigente |
| [Esquema y formatos de documentación](https://claude.ai/code/artifact/2566c623-5572-494e-ab08-23c9536b19de) | Pp | Aprobado el 28 sep |
| [Alcance](https://claude.ai/artifact/VvoXRHZf8pST4g7CKd2EBX) | Maestro | Vigente (v3) |
| [Guía Equipo IA](https://claude.ai/artifact/MFHP3Vihse48rqGQZj24Y9) | Equipo 2 | Con aviso del 29 sep: IA apagada, revisar proveedor por menores |
| [Guía Equipo Cámaras](https://claude.ai/artifact/1MPBhgeFa36dUnXZCCE7Eg) | Equipos 3 y 5 | Con aviso del 29 sep: ML Kit en el teléfono, galería hecha |
| [Probar CECAPI](https://claude.ai/artifact/My5wz3xvbvUiZHzaXy2Pai) | Compañeros | Con aviso del 29 sep: menú y cuentas actuales |
| [Simulador](https://claude.ai/artifact/F1sG8C2wmUip7oG26jQGJo) | Por confirmar | Por actualizar al menú nuevo |
| [Reporte semanal, 28 sep al 2 oct](https://claude.ai/artifact/ME9WSrduQrCTGkAQkeWpdA) | Equipos | Reemplazado por Tareas por equipo |
| [Bitácora](https://claude.ai/artifact/3nQUjLJtYZtb85cNj24q9v), [Planteamiento](https://claude.ai/artifact/XYfZCZHHZCjuPvQYDKm27f), [Cronograma](https://claude.ai/artifact/1MyXNyG2Qjw6aJgBEigtqg), [Arranque](https://claude.ai/artifact/JaBmouCVwbUUA1dxAHxCL8), [Entorno](https://claude.ai/artifact/CtQkaauTGcYZoiRwvtSbfC) | Maestro y equipos | Desactualizadas; se corrigen cuando el maestro decida el Equipo 1 |

## Fechas

- Meta 1: sábado 3 oct 2026. Meta 2: sábado 17 oct 2026.
- Primer reporte con el formato nuevo: viernes 2 oct 2026.

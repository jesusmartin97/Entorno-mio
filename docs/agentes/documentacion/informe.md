# Agente 2 · Documentación: informe

> Vigente · Dueño: Agente 2 (Claude) · Verificado el 28 sep 2026 contra integracion/actividades en 8019dd1

## Qué se hizo

- Se creó la rama `docs/agentes-y-documentacion` desde 8019dd1, sin cambiar de rama y sin commits. Los archivos están en `docs/` y en el `README.md` de la copia de trabajo, sin commit, para que Pp los suba en esa rama.
- Se escribió la estructura de `docs/` (índice en [docs/README.md](../../README.md)): 7 decisiones, guía de comandos generada desde el código, módulos, funciones y cuentas, glosario, mapa de la voz, guía para colaborar, pruebas manuales y registro de cambios.
- Se corrigió en el README la línea que decía que el proyecto no se había compilado, y la cuenta de prueba (ahora son las 4 de demostración, con JORGE en 4321).
- La guía de comandos se genera con `python docs/herramientas/generar_comandos.py`, que solo lee `CommandCatalog.kt` y `CommandAlternatives.kt`.

## Hallazgos

| ID | Severidad | Dónde | Qué pasa | Cómo reproducirlo | Sugerencia | Estado |
| --- | --- | --- | --- | --- | --- | --- |
| D-01 | Medio | `core/voice/CommandAlternatives.kt`, `GENERAL_LIST` y área "generales" | La lista de comandos generales ofrece "dime más", pero con la IA apagada `IntentFallback.deepQuestionFor` siempre devuelve nulo. Lo que oye la persona está por confirmar en el teléfono | Decir "comandos generales" y luego "dime más" con la IA apagada | Ofrecer "dime más" solo cuando la IA esté encendida, o explicar que la necesita | Nuevo |
| D-02 | Bajo | `README.md`, secciones de módulos y limitaciones | Fuera de la línea corregida, el README sigue describiendo el proyecto del 1 sep: el Asistente de Voz y la IA como pantallas del menú, el Módulo 6 como "Centro de Aprendizaje" y "sin pruebas automatizadas" | Leer el README | Reescribirlo corto y enlazar a `docs/` para el detalle | Nuevo; espera aprobación de Pp |
| D-03 | Bajo | `SettingsScreen.kt:239` | El texto de ayuda de la IA le dice a la persona que "los proveedores de IA no permiten su uso con menores de edad". Es una afirmación legal que viene de un borrador del Agente 5, todavía sin revisión de abogado | Abrir Configuración, sección Privacidad | Que la revise la persona abogada junto con los borradores | Nuevo |
| D-04 | Bajo | `docs/agentes/` | Los informes del Agente 1 están en otra copia de trabajo (`CECAPI-App-pruebas`, 173fa87) y el del Agente 3 no se ha entregado. Los de los Agentes 4 y 5 sí están aquí | Listar `docs/agentes/` | Que cada agente deje su informe en `docs/agentes/<nombre>/` | Nuevo |
| D-05 | Bajo | `docs/agentes/normativa/README.md` | Su informe principal dice que se revisó contra 173fa87. La ronda 2 ya usa 8019dd1; falta ver si el mapa de datos incluye el borrado de cuenta y el interruptor de IA | Leer el encabezado | Que el Agente 5 confirme el mapa de datos contra 8019dd1 | Nuevo |
| D-06 | Bajo | Página Simulador | Está desactualizada frente al menú nuevo | Abrir la página | Actualizarla cuando Pp confirme si sigue en uso | Marcado "por actualizar" |

## Sin verificar

- Qué dice la app al pedir "dime más" con la IA apagada (D-01): falta probarlo en el teléfono.
- Que el borrado en cascada no deje nada: lo revisa el Agente 3.
- Los pasos 4 y 5 de la guía para colaborar (correo de git y formato del PR): por confirmar con Pp.

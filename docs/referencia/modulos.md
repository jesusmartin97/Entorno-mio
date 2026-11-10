# Módulos, equipos y tablas

> Vigente · Dueño: Pp · Verificado el 28 sep 2026 contra integracion/actividades en 8019dd1 · Equipos confirmados por Pp; la composición del Equipo 1 la decide el maestro

Cada equipo trabaja solo dentro de su carpeta en `app/src/main/java/com/cecapi/app/feature/`. Lo compartido vive en `core/` y lo mantiene el Equipo 1. La base es una sola (`cecapi.db`, versión 3, 23 tablas).

| Módulo | Carpeta | Equipo | En el menú | Tablas |
| --- | --- | --- | --- | --- |
| 1. App principal | `modulo1_aplicacionprincipal/` | Equipo 1: Pp | Inicio, menú, sesión, Chats, Personalización, Configuración | `usuarios`, `configuracion_usuario`, `permisos_modulos` |
| 2. Asistente de voz | `modulo2_asistentevoz/` | Equipo 1 | Fuera del menú; el código se queda | `comandos_voz`, `historial_comandos`, `respuestas_auditivas` |
| 3. Asistente inteligente (IA) | `modulo3_asistenteinteligente/` | Equipo 2: Alondra, Owen, Alejandro | Fuera del menú; la IA está apagada | `consultas_ia`, `respuestas_ia`, `contextos_conversacion` |
| 4. Lector de documentos | `modulo4_lectordocumentos/` | Equipo 3: Lalo, Miranda, Humberto | Dentro de Cámara: "leer texto" | `documentos_escaneados`, `textos_extraidos`, `historial_lecturas` |
| 5. Documentos y solicitudes | `modulo5_solicitudes/` | Equipo 3 | Documentos (en pausa) | `plantillas_solicitud`, `solicitudes_generadas`, `datos_solicitud` |
| 6. Actividades | `modulo6_aprendizaje/` | Equipo 4: José y Luis | Actividades | `ejercicios`, `resultados_ejercicios`, `niveles_aprendizaje`, `ejercicios_vibracion`, `resultados_vibracion` |
| 7. Asistente del entorno | `modulo7_entorno/` | Equipo 5: Jesús y Edgar | Dentro de Cámara: "qué hay enfrente" | `escaneos_entorno`, `objetos_detectados`, `descripciones_entorno` |
| Base de datos del asistente, voces y personalización | Por definir | Alonso, a distancia | — | Chats: migración 8 o posterior |

Desde el 1 oct 2026 los avances se cuentan por equipo, no por persona. Alonso aparece por su nombre mientras Pp decide si se reporta como Equipo 6.

## Fuera de los módulos

| Carpeta | Qué hace |
| --- | --- |
| `core/voice/` | Motor de voz único (`VoiceEngine`), comandos globales, "otras formas de decirlo", tolerancia a errores (`VoiceText`) y palabra de activación |
| `core/data/`, `core/di/` | Base de datos, migraciones, borrado de cuenta y cuentas de demostración |
| `core/ui/` | Componentes compartidos, pantalla negra y cámara |
| `core/util/` | Estado del teléfono, volumen, brillo, almacenamiento y contraseñas |
| `notifications/`, `service/`, `widget/` | Lectura de notificaciones, escucha fuera de la app y widget |

## Notas

- Chats está en el Módulo 1, pero lee la tabla `consultas_ia` del Módulo 3. Alonso lo redefine con la versión 4 de la base.
- Los 17 audios de Actividades están en `app/src/main/res/raw/`; `keep.xml` los protege si algún día se activa la reducción de código.
- Por confirmar: si Pp y Alonso siguen siendo el mismo equipo.

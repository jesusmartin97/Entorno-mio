# Agente 2 · Documentación: ronda 2

> Vigente · Dueño: Agente 2 (Claude) · Verificado el 29 sep 2026 contra integracion/actividades en d5ffd5e · Solo lectura del código; no se hicieron consultas a Wikipedia ni a otros servidores

## Qué revisé

1. Que lo que la app **le dice a la persona** (listas de comandos, tutorial, textos de Configuración) coincida con lo que el código hace de verdad.
2. Que las **páginas publicadas** del proyecto no lleven a un equipo o a una persona a hacer algo que dañe a alguien.
3. Cómo explican otras apps de accesibilidad qué datos salen del teléfono ([comparación](#comparación-con-otras-apps)).

Lo actualizado en `docs/` para d5ffd5e: [comandos](../../referencia/comandos.md) (regenerada), [funciones y cuentas](../../referencia/funciones-y-cuentas.md), [qué sale del teléfono](../../referencia/que-sale-del-telefono.md) (nueva), [decisión 0002](../../decisiones/0002-ia-apagada-por-defecto.md) y el [registro de cambios](../../CHANGELOG.md).

## Hallazgos

| ID | Severidad | Dónde | Qué pasa | Cómo reproducirlo | Sugerencia | Estado |
| --- | --- | --- | --- | --- | --- | --- |
| D-08 | **Alto** | `HomeViewModel` y `DashboardViewModel`, `WIKI_TRIGGER` | El disparador de Wikipedia busca "qué es", "quién es" o "busca" **en cualquier parte de la frase**, no solo al principio. Frases cotidianas que no son búsquedas salen a Wikipedia. Probé la misma expresión regular fuera de la app: "No sé qué es lo que me pasa, me siento muy triste" envía "lo que me pasa, me siento muy triste"; "Me dijeron que es peligroso vivir en la calle Juárez 123" envía la dirección; "Mi maestra me busca todos los días" envía "todos los días". Luego la app lee en voz alta el artículo que Wikipedia considere más cercano, sin filtro. En Inicio no hace falta sesión, así que cualquiera, incluido un niño, puede provocarlo | Con internet e IA apagada, en Inicio, decir una de esas frases completas | Activar la búsqueda solo si la frase **empieza** con el disparador, y preguntar antes de salir: "¿Busco *X* en Wikipedia?". Una frase que expresa malestar nunca debería terminar leyendo un artículo al azar | Nuevo. Para el Agente 1 (probarlo en el teléfono), el Agente 3 (amplía S-17, que da el filtro por bueno) y el Agente 5 (contenido para menores) |
| D-07 | **Alto** | `SettingsScreen.kt:248`, `CommandCatalog.kt:105`, además de `SettingsViewModel.kt:289` (S-17) | La app afirma dos veces más que nada sale del teléfono: "Qué sale de tu teléfono: por defecto, nada" y "apagada, nada sale de tu teléfono". Con d5ffd5e es falso por Wikipedia, y ya lo era por el reconocedor de voz (S-05). Google Play considera "recoger" cualquier dato que sale del dispositivo | Leer la sección Privacidad o pedir la lista de comandos de Configuración | Cambiar los tres textos para que digan lo cierto, función por función (ver [qué sale del teléfono](../../referencia/que-sale-del-telefono.md)). Subo la severidad frente al Medio de S-17: quien decide con ese texto puede ser el tutor de un menor | Nuevo; se suma a S-17 |
| D-09 | Medio | `core/voice/HelpTopics.kt`, tema "session" | El tutorial dice: "Puedes usar la cámara y otras partes sin cuenta, pero no se guardará nada". Es falso: el lector y Entorno piden iniciar sesión (`VoiceMessages.NEEDS_LOGIN` en `DocumentReaderViewModel.kt:128` y `EnvironmentViewModel.kt:104`). Quien aprende sola con el tutorial se topa con un bloqueo que el tutorial le dijo que no existía | Sin sesión, decir "tutorial de la sesión" y luego "leer texto" | Corregir el tutorial o permitir la cámara sin cuenta. Decide Pp | Nuevo |
| D-10 | Medio | `HelpTopics.GENERAL`, `CommandAlternatives`, textos al mantener presionado | Ninguna ayuda hablada menciona que dos toques en el micrófono callan al asistente. Quien lo hace sin querer no sabe por qué se calló ni cómo volver ("hola") | Sin TalkBack, tocar dos veces rápido el micrófono en Inicio | Decirlo en el tutorial y en la ayuda del micrófono, y al callarse por doble toque decir "Me callo. Di hola para volver" | Nuevo |
| D-11 | Bajo | `ModuleCarousel.kt`, `CameraHubScreen.kt`, `ChatsScreen.kt`, `DashboardScreen.kt` | Las ayudas que la app dice en voz alta al mantener presionado terminan en "Toca dos veces para abrir", que es el gesto de TalkBack. Sin TalkBack basta un toque, y en el micrófono dos toques hacen otra cosa | Sin TalkBack, mantener presionada una tarjeta del menú | Decir "Tócala para abrir" cuando TalkBack está apagado | Nuevo |
| D-01 | Bajo | `CommandAlternatives.GENERAL_LIST` | "Dime más" ahora sirve sin IA, pero solo para leer el resto de un resumen de Wikipedia. Depende de D-07 y D-08 | — | Revisarlo junto con Wikipedia | Cambió con d5ffd5e |

### Páginas publicadas

| ID | Severidad | Página | Qué pasa | Sugerencia | Estado |
| --- | --- | --- | --- | --- | --- |
| D-12 | **Alto** | [Guía Equipo IA](https://claude.ai/artifact/MFHP3Vihse48rqGQZj24Y9) | Le indica al Equipo 2 construir el servidor con una llave gratuita de Gemini. Según el informe del Agente 5 (N-01, borrador), los términos de la API de Gemini no permiten servicios que probablemente usen menores de 18 años, y la IA quedó apagada por eso (decisión 0002). La guía no lo menciona: el equipo puede invertir las dos semanas que quedan en algo que no se podrá encender | Poner un aviso arriba: "La IA está apagada en la app hasta decidir proveedor y edades. No elijan proveedor sin revisar sus términos para menores" | Aviso puesto el 29 sep |
| D-13 | Medio | [Guía Equipo Cámaras](https://claude.ai/artifact/1MPBhgeFa36dUnXZCCE7Eg) | Plantea "IA primero, ML Kit de respaldo" y fotos que salen hacia Google. Hoy la app funciona al revés (ML Kit en el teléfono, IA apagada) y ya tiene la galería, que la guía da como pendiente | Aviso del mismo tipo y agregar el estado de d5ffd5e | Aviso puesto el 29 sep |
| D-14 | Medio | [Probar CECAPI](https://claude.ai/artifact/My5wz3xvbvUiZHzaXy2Pai) | El paso 10 enumera un menú que ya no existe (asistente de voz, cámara inteligente, solicitudes); el recuadro verde habla del Asistente Inteligente, que salió del menú; y manda a descargar main, que no tiene nada de lo nuevo | Actualizar los pasos 9 y 10 y el recuadro, y aclarar qué versión se prueba | Aviso puesto el 29 sep |
| D-15 | Bajo | [Alcance](https://claude.ai/artifact/VvoXRHZf8pST4g7CKd2EBX), para el maestro | Presenta Gemini como camino del Equipo 2, sin la restricción de edad | Alinearlo con la decisión 0002 antes de enseñarlo | Anotado; no se toca hasta que decidas |
| D-16 | Medio | [Tareas por equipo](https://claude.ai/artifact/HBF3rJTytZj34HZ9H1bWon) | Sus reglas dicen que los PR van "hacia feature/interfaz-invidentes, nunca hacia main", pero en la misma página el Equipo 4 tiene la tarea de abrir su PR hacia main. El trabajo actual está en `integracion/actividades`. Además, la comparación de voces de Alonso (Gemini, ElevenLabs y OpenAI) no incluye si cada proveedor permite menores | Que Pp diga a qué rama van los PR, y agregar "¿permite menores?" a la comparación | Nota puesta arriba de la página el 29 sep; la rama de los PR sigue por confirmar |

### Actualización del 1 oct, contra main local en e968668

| ID | Severidad | Dónde | Qué pasa | Sugerencia | Estado |
| --- | --- | --- | --- | --- | --- |
| D-08 | **Crítico** (subió) | `HomeViewModel.kt:306` y `DashboardViewModel` | Desde ecc7b8b, cualquier frase que la app no entienda (3 letras o más) va completa a Wikipedia. Los disparadores crecieron: "por qué", "porque", "dónde está", "cuánto cuesta"... siguen sin anclarse al inicio de la frase | Buscar solo con petición explícita y preguntar antes de salir; quitar el envío de la frase completa | Abierto |
| D-07 | Alto | `SettingsScreen.kt:253`, `CommandCatalog.kt:105` | Siguen diciendo que nada sale del teléfono | Igual | Abierto |
| D-09 | — | Tutorial de la sesión | La cámara ya funciona sin cuenta (d50a1b0); el tutorial ya es cierto | — | Resuelto |
| D-17 | Medio | Commit 9b1c9d8 | Atribuye el backend a Alonso, pero todos los commits de `modulo03/backend-asistente` son del Equipo 2. Alonso no tiene commits desde el 20 sep | Corregir la atribución en los reportes; no reescribir el historial de git | Nuevo |
| D-18 | Medio | Ramas de los equipos 3 y 4 | 9 commits del Equipo 3 y 3 del Equipo 4 están solo en sus ramas, y el Equipo 1 cambió los mismos archivos (lector y Actividades) | Fusionar cuanto antes con cada equipo presente | Nuevo |
| D-19 | Medio | `AndroidManifest.xml` | `usesCleartextTraffic="true"` en el manifiesto principal; la guía del Equipo IA lo indicaba solo para `app/src/debug/` | Moverlo al manifiesto de pruebas; lo revisa el Agente 3 | Nuevo |
| D-20 | Bajo | `decisiones/0004` | La versión 4 de la base, reservada para los chats de Alonso, ya se usó para el rol Educador | Asignarle a Alonso la versión 8 o la siguiente libre | Documentado |

Actualización del 29 sep: con tu visto bueno para terminar la documentación, puse un aviso arriba en D-12, D-13 y D-14, y una nota en Tareas por equipo. No cambié nada más del contenido de esas páginas. Toda la información para compartir quedó reunida en [Documentación CECAPI](https://claude.ai/artifact/1Cawemf7QziJfgzUkGFZ62).

## Comparación con otras apps

| App o fuente | Cómo lo explica | Qué conviene copiar |
| --- | --- | --- |
| [Be My Eyes, política de privacidad](https://www.bemyeyes.com/privacy-policy/) | Dice qué sale del teléfono con su función de IA (fotos, a un proveedor de IA en Estados Unidos), que no entrena modelos con ellas y que el servicio no es para menores de 18 años | Decir por función qué sale, a quién y para qué. No nombra al proveedor, y eso es una debilidad que no conviene copiar |
| [Google Lookout, ayuda](https://support.google.com/accessibility/android/answer/9031274?hl=es) | La guía de uso no explica qué se procesa en el teléfono y qué en los servidores; solo muestra un interruptor de "recogida de datos" | Ni una app grande lo resuelve bien en su ayuda. CECAPI puede hacerlo mejor con una tabla corta y hablada |
| [Wikimedia, política de privacidad](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy) | Recibe la IP, el dispositivo y las páginas pedidas; en la mayoría de los casos los borra o anonimiza a los 90 días | Es lo que hay que decirle a la persona sobre las búsquedas |
| [Google Play, seguridad de los datos](https://support.google.com/googleplay/android-developer/answer/10787469?hl=es) | "Recoger" es transmitir datos fuera del dispositivo | Con Wikipedia y el reconocedor de voz, la declaración de Play ya no podría decir "no recoge datos" |

Fuentes consultadas el 29 sep 2026.

## Sin verificar

- Qué artículo devuelve Wikipedia para las frases de D-08. No lo consulté para no mandar esas frases a un servidor externo; debe probarse con Pp presente.
- Si ML Kit envía datos de uso a Google.
- D-08 lo probé con una copia en Python de `WIKI_TRIGGER` y `VoiceText.fold`, no dentro de la app. Además, solo aplica cuando la frase no coincide antes con un comando.

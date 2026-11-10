# Funciones recientes y cuentas de demostración

> Vigente · Dueño: Pp · Verificado el 1 oct 2026 contra main local en e968668 (sin subir) · e968668 se instaló en el Oppo el 1 oct: compila, migra de la versión 6 a la 7 y abre; las funciones nuevas no se han probado una por una

## Cuentas de demostración

Se crean solas cada vez que se abre la base de datos, si faltan (`core/di/DatabaseModule.kt`). Son las únicas cuentas que se usan hasta que Pp autorice datos reales.

| Usuario | Contraseña | Rol | Institución |
| --- | --- | --- | --- |
| CECAPI | 1234 | Administrador (cuenta compartida para probar) | admin |
| PEPE | 1234 | Administrador | admin |
| JORGE | 1234 (se crea con 4321) | Directivo | CECAPI |
| MIRIAM | 1234 | Educador | CECAPI |
| JUAN | 1234 | Alumno, asignado a MIRIAM | CECAPI |

- Desde ecc7b8b (30 sep), al abrir la app **todas** las cuentas pasan a 1234 una sola vez (`resetAllPasswordsOnce`, con una bandera para que no se repita). JORGE se crea con 4321 y ese reinicio lo deja en 1234. Por confirmar en el teléfono.
- El usuario no distingue mayúsculas: "pepe" y "PEPE" son la misma cuenta.
- Tras 10 intentos fallidos, el inicio de sesión se bloquea 15 minutos. Se mide con el reloj interno del teléfono, que la persona no puede cambiar; se reinicia si el teléfono se apaga.
- **El teléfono recuerda la sesión** (d50a1b0): al volver a abrir la app entra directo con la última cuenta, sin contraseña, hasta cerrar sesión o borrar la cuenta.
- "Olvidé mi contraseña" no la recupera: la app responde que se la pida a un administrador o a quien le dio de alta.

## Roles

| Rol | Ve a | Puede |
| --- | --- | --- |
| Administrador | Todas las instituciones y los usuarios independientes | Cambiar roles, asignar educador, validar a cualquiera |
| Directivo | Toda su institución | Cambiar roles (nunca dar administrador), asignar educador, validar educadores de su institución |
| Educador | Solo sus alumnos, sin poder cambiarlos | Validar a sus alumnos |
| Alumno | Nadie | Ver quién es su educador y reportar un problema |
| Usuario independiente | Nadie | Reportar un problema (le llega a los administradores) |

Las cuentas nuevas entran desde el primer día, pero quedan marcadas "pendiente" en Gestión hasta que alguien de arriba las valida (e968668). Reglas en `RolePermissions.kt`.

## Funciones nuevas

| Función | Cómo se usa | Qué hace | Dónde está |
| --- | --- | --- | --- |
| Gestión | Botón "Gestión" en el menú o decir "gestión"; solo administrador, directivo y educador | Lista a las personas que el rol puede ver, con su rol, educador y si están pendientes de validar | `GestionScreen.kt`, `RolePermissions.kt` |
| Ayuda y soporte | Botón en el menú, solo para alumno y usuario independiente | Muestra su educador y permite reportar un problema con la app o con alguien de su institución | `SoporteScreen.kt`, tabla `incidencias` |
| Clasificación | Pantalla de clasificación | Tabla individual por puntos de Actividades (con apodo, nunca el nombre completo) y tabla por instituciones. La vibración todavía no da puntos | `RankingScreen.kt`, columna `usuarios.apodo` |
| Editar mi cuenta | Desde la cuenta | Cambiar los datos propios | e968668 |
| Cámara sin cuenta | Leer texto o "qué hay enfrente" sin iniciar sesión | Funciona igual, pero sin guardar historial. Actividades sigue pidiendo cuenta | d50a1b0 |
| Guía de encuadre | En el lector, al apuntar | Dice por voz cómo mover el teléfono para que el texto entre en la foto | 95c5145 |
| Interruptor de IA | Configuración, sección Privacidad | Empieza apagado. La IA ya está conectada en el código (`AiResolverSetup`), pero la dirección del servidor es un ejemplo (`TU-IP-LOCAL`), así que no responde fuera de la computadora de quien corre el backend. **No controla Wikipedia** | `AiResolverSetup.kt`, `IntentFallback.kt` |
| Wikipedia y Wikidata | Cualquier frase que la app no entienda, con internet | **Manda la frase completa a Wikipedia** si tiene 3 letras o más, y busca datos puntuales (nació, capital, población) en Wikidata. Funciona con la IA apagada, sin interruptor ni filtro para menores | `WikipediaLookup.kt`, `WikidataLookup.kt`, `HomeViewModel`, `DashboardViewModel` |
| Elegir motor de voz | Botón que aparece si el teléfono no tiene un motor de voz activo | Abre los ajustes de Android para elegir uno | f51503b |
| Borrar mi cuenta y mis datos | Configuración: "borra mi cuenta", con contraseña y confirmación | Borra la cuenta y todo lo que guardó, fotos incluidas | `AccountEraser.kt` |
| Aprender sola la app | "Tutorial", "dónde estoy", "cómo voy", "habla más despacio" | Explica cada parte en voz alta | `HelpTopics.kt` |
| Perfiles por edad | Personalización: "perfil de niño", "normal" o "de persona mayor" | Cambian velocidad, tono y tú o usted, sin pedir la edad | `VoiceProfile.kt` |
| Pantalla negra, espacio, caché, "otras formas de decirlo" | Ver la [guía de comandos](comandos.md) | Sin cambios esta semana | — |

## Cambios de interfaz de la semana del 28 sep al 2 oct

| Pantalla | Qué cambió | Commit |
| --- | --- | --- |
| Actividades | Tarjetas de pantalla completa que se deslizan: Sonidos, Vibración y una nueva de actividades recreativas (diez, informativas). Los puntos bajo el título llevan directo a una tarjeta; el nivel solo aparece en Sonidos | bbf4f21, e968668 |
| Lector de documentos | Al leer, tarjetas grandes del mismo tamaño (Volver al menú, Repetir, Anterior, Pausa, Siguiente). Diseño del Equipo 3, traído desde su rama | d016faf |
| Lector de documentos | Guía por voz para encuadrar antes de tomar la foto (`TextFramingAnalyzer`, del Equipo 3) | 95c5145 |
| Qué hay enfrente | Foto enderezada, posición de cada objeto y concordancia ("una silla negra"), con el diccionario del Equipo 5 | 51dde3a, 36b780f |
| Micrófono | Un toque habla, dos toques callan, mantener presionado solo explica | 7454143 |
| Iniciar sesión y Crear cuenta | Flecha visible para volver | 7454143 |
| Configuración | Botones Editar mi cuenta y elegir motor de voz | e968668, f51503b |
| Menú | Botones nuevos según el rol: Gestión (administrador, directivo, educador), Ayuda y soporte (alumno, usuario) y Clasificación | 1b4c042, 190ddd0, 2865f80 |

## Base de datos

Versión 7 desde el 1 oct (era 3 el 28 sep). Migraciones nuevas en `core/data/Migrations.kt`: de 3 a 4, rol Educador y educador de cada alumno; de 4 a 5, tabla de incidencias; de 5 a 6, apodo para la clasificación; de 6 a 7, cuentas por validar. Ninguna se ha probado en un teléfono que ya tenga la app.

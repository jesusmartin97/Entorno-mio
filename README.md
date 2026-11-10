# CECAPI

App Android para personas ciegas o con muy poca visión del CECAPI (Centro de Capacitación para Invidentes, Durango). Se maneja casi solo con la voz: lee textos con la cámara, describe lo que hay enfrente, tiene ejercicios de oído y tacto, y funciona sin internet con comandos locales. Es un proyecto de estadía de la UTD, con metas el 3 y el 17 de octubre de 2026.

> Revisado el 29 sep 2026 contra la rama `integracion/actividades` en d5ffd5e. La rama compila y se instaló en el teléfono de prueba el 29 sep; todavía no hay resultados de prueba en el teléfono.

## Abrir y correr el proyecto

1. Instala **Android Studio** (Ladybug 2024.2 o más reciente).
2. Abre esta carpeta con **File → Open**. No crees un proyecto nuevo.
3. Espera a que sincronice. La primera vez tarda varios minutos.
4. Si Android Studio ofrece actualizar el Android Gradle Plugin, di que no: las versiones están congeladas ([decisión 0001](docs/decisiones/0001-versiones-congeladas.md)).
5. Corre la app en un teléfono con **Android 8.0 o superior**, o en un emulador **con Google Play**: sin él no hay reconocimiento de voz ni ML Kit.

| Pieza | Versión |
| --- | --- |
| AGP | 8.5.2 |
| Gradle | 8.9 |
| Kotlin | 2.0.21 |
| compileSdk y targetSdk | 35 |
| minSdk | 26 (Android 8.0) |

## Cuentas de prueba

Solo se usan estas cuentas de demostración, hasta que el responsable del proyecto autorice datos reales.

| Usuario | Contraseña |
| --- | --- |
| CECAPI | 1234 |
| PEPE | 1234 |
| JORGE | 4321 |
| JUAN | 1234 |

Se crean solas al abrir la base de datos, si faltan (`core/di/DatabaseModule.kt`). El usuario no distingue mayúsculas.

## Dónde está cada cosa

Cada equipo trabaja solo en su carpeta de `app/src/main/java/com/cecapi/app/feature/`. Lo compartido vive en `core/` y lo mantiene el Equipo 1; si necesitas una tabla o un cambio ahí, pídelo, no lo copies.

| Carpeta | Qué es | Equipo |
| --- | --- | --- |
| `feature/modulo1_aplicacionprincipal/` | Inicio, sesión, menú, Chats, Personalización y Configuración | Equipo 1 |
| `feature/modulo2_asistentevoz/` | Asistente de voz (fuera del menú) | Equipo 1 |
| `feature/modulo3_asistenteinteligente/` | IA, apagada por defecto y sin conectar | Equipo 2 |
| `feature/modulo4_lectordocumentos/` | Leer texto con la cámara | Equipo 3 |
| `feature/modulo5_solicitudes/` | Documentos y solicitudes | Equipo 3 |
| `feature/modulo6_aprendizaje/` | Actividades de sonido y vibración | Equipo 4 |
| `feature/modulo7_entorno/` | Describir lo que hay enfrente | Equipo 5 |
| `core/voice/` | Motor de voz único, comandos globales y tolerancia a errores | Equipo 1 |
| `docs/` | Documentación | Todos |

Los módulos 8 a 14 están diseñados en `database/schema.sql`, pero todavía no tienen código.

## Por qué está hecho así

- **Kotlin nativo con Compose:** reconocimiento de voz, síntesis de voz, CameraX y ML Kit tienen soporte directo en Android, y Compose se integra con TalkBack.
- **Un solo motor de voz** (`VoiceEngine`): Android solo permite un reconocedor y una voz confiables a la vez. Ver [cómo funciona la voz](docs/explicacion/voz.md).
- **Ninguna llave de IA dentro de la app:** quien descompila el APK la obtendría. La IA pasaría por un servidor propio, y hoy está apagada ([decisión 0002](docs/decisiones/0002-ia-apagada-por-defecto.md)).
- **Tema oscuro fijo:** para baja visión importa más un contraste constante que seguir el tema del sistema.

## Documentación

- [Índice de la documentación](docs/README.md): comandos de voz, módulos, qué sale del teléfono, decisiones y cambios.
- [Cómo colaborar](docs/guias/colaborar.md): ramas, PR y qué no tocar.
- [Registro de cambios](docs/CHANGELOG.md).

## Pendiente

- Probar en un teléfono todo lo hecho desde el 25 de septiembre, sobre todo la migración de la base de datos de la versión 2 a la 3.
- Las pruebas automáticas del agente de pruebas están en la rama `pruebas/agente1` y todavía no se integran.
- Falta el ícono final de la app.

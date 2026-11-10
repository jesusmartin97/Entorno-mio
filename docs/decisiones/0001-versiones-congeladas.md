# 0001. Versiones de Gradle, AGP y Kotlin congeladas

> Vigente · Dueño: Pp · Verificado el 28 sep 2026 contra integracion/actividades en 8019dd1

## Contexto

El 20 sep 2026 el commit 14506d0 subió el proyecto a AGP 9.4.1, Kotlin 2.2.10 y Gradle 9.6.0. Desde un clon limpio fallaba con "Cannot add extension with name 'kotlin'". AGP 9 además exige un Android Studio reciente en las 12 computadoras del equipo.

## Decisión

El 26 sep 2026 main quedó en e5e1fed con estas versiones, que no se cambian sin acuerdo de Pp:

| Pieza | Versión | Dónde se lee |
| --- | --- | --- |
| AGP | 8.5.2 | `gradle/libs.versions.toml` |
| Gradle | 8.9 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | 2.0.21 | `gradle/libs.versions.toml` |
| KSP | 2.0.21-1.0.28 | `gradle/libs.versions.toml` |
| Room | 2.6.1 | `gradle/libs.versions.toml` |
| Hilt | 2.51.1 | `gradle/libs.versions.toml` |
| compileSdk y targetSdk | 35 | `app/build.gradle.kts` |
| minSdk | 26 (Android 8.0) | `app/build.gradle.kts` |

## Consecuencias

- El commit de Alonso quedó revertido; hay que avisarle.
- **Riesgo abierto (28 sep 2026):** según el informe del Agente 5 (N-06), Google Play pide targetSdk 36 desde el 31 ago 2026 para apps nuevas y actualizaciones. No se cambia ahora. Se decide junto con el canal de publicación (tienda o web), que sigue pendiente.
- Ningún agente ni equipo cambia estas versiones. Si una prueba las necesita, se le pide a Pp.

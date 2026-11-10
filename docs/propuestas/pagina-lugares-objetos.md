# Propuesta: página de lugares y objetos

> Propuesta · Dueño: Equipo 5 (Jesús y Edgar) · Escrito el 19 oct 2026 contra `integracion/combinado` en `5a4d365` · **No implementada**: solo propuesta para que la revise Pp.

Solo es un documento. **No hay código**, no hay pantalla nueva y no se toca la navegación. La lista de
lugares la entrega Pp más adelante; aquí **no se inventa**: solo se dice dónde viviría la página, con
qué se haría y qué datos mostraría.

## 1. Qué es

Una pantalla donde la persona puede **repasar en voz alta** lo que la app ya conoce de su entorno:

- **Objetos** que el Módulo 7 ya guardó al describir fotos (por ejemplo, la última vez que vio una
  silla o una mochila).
- **Lugares**: una lista de lugares (aula, baño, salida…) que **Pp todavía no ha entregado**. La
  página se diseñaría para mostrarla, pero el contenido de esa lista queda pendiente.

No es un mapa ni un GPS. Es una lista que se recorre de una en una con voz y tacto, como el resto de
la app.

## 2. Dónde viviría

- Carpeta: `feature/modulo7_entorno/` (es lo del entorno). Archivos propuestos, sin crear todavía:
  - `LugaresObjetosScreen.kt` — la pantalla Compose.
  - `LugaresObjetosViewModel.kt` — el `@HiltViewModel` que lee la base.
  - `LugaresObjetosData.kt` — solo si hacen falta tablas nuevas (ver punto 4).
- **Pendiente de Equipo 1 (navegación):** hoy no se puede llegar a ninguna pantalla nuestra porque
  no hay ruta en `core/navigation/` (`CecapiDestinations.kt` / `CecapiNavGraph.kt`). Igual que
  `PantallaAccesibilidad` del Módulo 10, haría falta que Equipo 1 agregue la ruta y el botón en el
  menú. **No se toca `core/`.**

## 3. Con qué se haría (stack)

Todo lo que la app ya usa, sin agregar dependencias (no se toca Gradle):

- **Kotlin + Jetpack Compose + Material 3** para la pantalla, con los componentes grandes de
  `core/ui/` (`BigBtn`, `ScreenTopBar`, `ModuleCarousel`… si aplican).
- **Hilt** para el ViewModel y el repositorio.
- **Room** para leer lo guardado.
- **`VoiceEngine`** (de `core/voice/`) para leer en voz alta al recorrer la lista. Solo se usa; no se
  modifica.
- La navegación con voz seguiría el patrón actual (cada pantalla atiende sus comandos), y los
  comandos nuevos, si se aprueban, habría que agregarlos a `CommandCatalog` (pendiente con Pp /
  Equipo 1).

## 4. Qué datos mostraría

### Objetos (ya existen)

Salen de las tablas del Módulo 7 que ya están en uso:

- `escaneos_entorno` (cuándo y qué foto) — `EnvironmentData.kt`.
- `objetos_detectados` (`etiqueta`, `confianza`) — lo que se reconoció.
- `descripciones_entorno` (`texto_descripcion`) — la frase que se dijo.

Se mostrarían los objetos más recientes (`observeRecent`), agrupados por escaneo, con su fecha. Nada
nuevo de base.

### Lugares (pendiente)

La lista de lugares **no está definida**. Para no inventarla, la propuesta es que, si Pp la aprueba,
cada lugar tenga como mínimo:

| Dato | Para qué |
| --- | --- |
| Nombre del lugar | Lo que se dice en voz alta ("Aula 3"). |
| Descripción corta | Una frase de ayuda ("Salón con bancas y pizarrón"). |
| Objetos típicos | Qué suele haber, para cruzarlo con lo que la cámara ya vio. |

Si esa lista necesita tabla nueva en la base, **es cambio de esquema** y hay que pedírselo a Pp /
Equipo 1 (una migración nueva); este documento no la propone todavía.

## 5. Cómo se recorrería (voz y tacto)

- Al entrar, la pantalla se narra sola y dice cuántos lugares y objetos hay.
- Se recorre con los mismos gestos del perfil de accesibilidad (Módulo 10): deslizar para pasar de
  uno en uno, doble toque para repetir.
- Cada elemento se dice completo: nombre, y si es un objeto, su última fecha.

## 6. Qué NO hace esta propuesta

- No crea archivos ni pantallas (solo documentos).
- No inventa la lista de lugares: la espera de Pp.
- No toca `core/`, ni la navegación, ni Gradle, ni el comportamiento actual del Módulo 7.

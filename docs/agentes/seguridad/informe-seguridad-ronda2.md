# Informe de seguridad — Ronda 2

**Base:** commit `d5ffd5e` (HEAD del árbol coincide) · **Revisión:** 28 sep 2026 · **Agente 3** · Solo lectura.

Complementa [`informe-seguridad.md`](informe-seguridad.md) (ronda 1, base `8019dd1`). Los hallazgos de la ronda 1 siguen abiertos salvo donde se diga. Aquí van los encargos de esta ronda y un hallazgo nuevo que apareció al revisarlos.

## Encargo A — Paso de contraseña para borrar cuenta

### S-18 — La contraseña para borrar cuenta se dicta por voz (mismo riesgo que S-05)
- **Severidad:** Alto (privacidad). Es el mismo canal que S-05, ahora en un segundo punto.
- **Dónde:** `SettingsViewModel.kt:221-254` (`askDeleteAccount` → `rawInput=true` → `checkDeletePassword`).
- **Qué pasa:** para borrar la cuenta, la app pide decir la contraseña. Al dictarse, pasa por el `SpeechRecognizer` del sistema (Google), que por defecto envía el audio a la nube. La contraseña de la cuenta viaja a un tercero, igual que en el login. Ocurre **al margen del interruptor de IA**.
- **Cómo reproducirlo:** revisión de código; en dispositivo, borrar cuenta por voz sin motor de reconocimiento offline y observar que requiere red.
- **Sugerencia:** misma que S-05: ofrecer/priorizar la entrada por teclado para este paso (ya existe el campo, ver S-18b), intentar reconocimiento offline y avisar, y declararlo en el aviso de privacidad (Agente 5).

### S-18b — El campo de contraseña en pantalla está bien hecho (verificado correcto)
- **Severidad:** Info.
- **Dónde:** `SettingsScreen.kt:294-324`.
- **Qué pasa:** el `OutlinedTextField` usa `PasswordVisualTransformation()` (enmascarado, con un botón para mostrarlo a propósito) y `KeyboardType.Password`. Eso oculta la contraseña en pantalla y evita que el teclado la aprenda/sugiera o la autocomplete. Comportamiento correcto.
- **Sugerencia:** ninguna. Para personas ciegas que teclean, es la opción más segura frente a S-18; conviene ofrecerla claramente como alternativa a dictarla.

### La contraseña NO queda en logs (verificado)
- `SettingsViewModel` no tiene ninguna llamada `Log.*`. `checkDeletePassword` hashea y compara; no escribe la contraseña en logcat. Correcto. **Pero** ver S-20: el habla reconocida se emite en un flujo compartido y puede persistirse en la base por otro colector.

## Encargo B — Wikipedia como respaldo

### S-17 — La consulta a Wikipedia sale a un tercero aun con la IA/privacidad apagada
- **Severidad:** Medio (parte de privacidad/flujo de datos; el contenido y lo legal es del Agente 5).
- **Dónde:** `WikipediaLookup.kt:26-72`; se dispara desde `HomeViewModel.kt:290-323` y `DashboardViewModel.kt:~240-260` cuando `wikiQuery != null && isOnline`.
- **Qué pasa:**
  1. La app envía a `es.wikipedia.org` (Wikimedia Foundation, servidores en EE. UU.) **la parte que dijo la persona** después de "busca / qué es / quién es / información sobre…" (`HomeViewModel.kt:301-304`). Va por HTTPS y URL-encoded, en la cadena de consulta.
  2. **Sucede con la IA apagada.** El interruptor de privacidad dice, al apagar la IA: *"Ninguna de tus preguntas se envía a un servidor"* (`SettingsViewModel.kt:289`). Pero las consultas de Wikipedia **sí** salen a un servidor externo cuando la IA está apagada. Para una persona ciega (y ahora menores) que confía en "apagado = nada sale", es una promesa incumplida.
  3. Si la persona lo formula como búsqueda —"busca mi domicilio en la calle…", "busca a Juan Pérez García" (un nombre real)—, ese dato personal se manda a Wikimedia. Wikimedia registra las peticiones según su política de privacidad. El filtro por intención (regex `WIKI_TRIGGER`, `HomeViewModel.kt:387-390`) evita mandar **cualquier** frase —eso es bueno—, pero no evita que una búsqueda contenga datos personales.
- **Cómo reproducirlo:** con la IA apagada e internet, decir "busca " + un nombre o dirección reales; la consulta llega a `es.wikipedia.org` con ese texto.
- **Sugerencia (parte de seguridad/privacidad):**
  - Corregir el mensaje del interruptor para que no prometa que "nada se envía": aclarar que Wikipedia (fuente pública) puede consultarse aunque la IA esté apagada, o gatear Wikipedia bajo su propio consentimiento.
  - Considerar un aviso la primera vez que se usa Wikipedia, y una forma de desactivarla (hoy no hay).
  - No hay forma fiable de impedir que una búsqueda lleve datos personales; la mitigación real es consentimiento + aviso claro.
  - **Handoff Agente 5:** filtro de contenido para menores (Wikipedia no filtra violencia/sexualidad/suicidio), términos de Wikimedia y CC BY-SA. Esta parte no es de seguridad técnica.
- **Nota técnica menor:** sin certificate pinning (consistente con S-06, Bajo). `User-Agent` es un identificador estático sin datos personales (correcto, cumple la etiqueta de Wikimedia).

## Hallazgo nuevo (fuera de los dos encargos, apareció al seguir el flujo del habla)

### S-20 — El Asistente de voz guarda cada frase reconocida en texto plano en la base
- **Severidad:** Alto.
- **Dónde:** `VoiceAssistantViewModel.kt:44-59` → `VoiceAssistantRepository.logInteraction` (`:17-32`) → `comandos_voz.texto_comando` (tabla Room).
- **Qué pasa:** mientras la pantalla "Asistente de voz" está activa, **toda** frase reconocida se guarda verbatim en la base (`ComandoVozEntity(textoComando = speech.text)`), junto al `usuario_id`, sin cifrar. Dos consecuencias:
  1. **Transcripción de voz en claro.** Queda un historial de lo que la persona dijo (que puede incluir datos personales o de salud), en texto plano, sujeto a S-03 (sale en copias de seguridad) y S-04 (sin cifrado en reposo).
  2. **Posible captura de una contraseña dictada.** Durante los pasos con `rawInput=true` (contraseña de login y **contraseña para borrar cuenta**, S-18), el texto —la contraseña— **también se emite** en `recognizedSpeech` (`VoiceEngine.kt:283`, la emisión no depende de `rawInput`; `rawInput` solo salta los interceptores en la línea 277). Si el `VoiceAssistantViewModel` está vivo y colectando en ese momento (p. ej. quedó en la pila de navegación), `logInteraction` guardaría la contraseña en `comandos_voz`. El login ocurre sin sesión (`currentUser==null` → el colector sale antes, línea 46), así que ahí no aplica; pero el borrado de cuenta ocurre **con sesión iniciada**, así que sí puede aplicar.
- **Cómo reproducirlo:** entrar; abrir "Asistente de voz"; navegar a Configuración sin cerrar la anterior; iniciar el borrado de cuenta y dictar la contraseña; inspeccionar `comandos_voz` (por adb/backup, ver S-03). **Confirmación en dispositivo: tarea del Agente 1.** Si el ViewModel no sigue vivo en ese flujo de navegación, el punto 2 no se dispara, pero el punto 1 (transcripción en claro) es incondicional.
- **Sugerencia:**
  - No emitir el texto en el flujo compartido `recognizedSpeech` durante `rawInput`, o enrutar la contraseña por un canal dedicado que no sea el flujo global (arreglo de raíz para S-18 y S-20 punto 2).
  - Como salvaguarda mínima inmediata: que `VoiceAssistantViewModel` **no** registre cuando `voiceEngine.rawInput` sea `true`. Parche propuesto (sin aplicar) en [`parches/S-20-no-log-en-rawinput.patch`](parches/S-20-no-log-en-rawinput.patch); es de un módulo de otro equipo (Módulo 2), así que lo decide su dueño/el constructor.
  - Revisar si guardar la transcripción completa de voz es necesario, y por cuánto tiempo; documentarlo en el aviso de privacidad (Agente 5) y considerar límite de retención.
- **Bueno:** `comandos_voz` tiene FK a `usuarios` con `ON DELETE CASCADE`, así que "borra mi cuenta" (S-09) sí elimina este historial. El problema es que está en claro mientras existe.

## Estado de hallazgos previos relevantes a esta ronda

- **S-05:** sigue abierto y ahora se confirma en un segundo punto (S-18).
- **S-03 / S-04:** siguen abiertos; S-20 aumenta su impacto (ahora sabemos que hay transcripciones de voz en claro entre lo respaldado/sin cifrar).
- **S-13 (IA apagada):** sigue correcto para la IA, pero **S-17 matiza el mensaje del interruptor**: la promesa "nada se envía" no contempla Wikipedia.

## Resumen y prioridad de esta ronda

| ID | Sev. | Una línea |
|----|------|-----------|
| S-20 | Alto | El Asistente de voz guarda cada frase (posible contraseña) en la base en texto plano |
| S-18 | Alto | La contraseña para borrar cuenta se dicta por voz → reconocedor de Google (como S-05) |
| S-17 | Medio | La consulta de Wikipedia sale a un tercero aun con la IA apagada; puede llevar datos personales; el mensaje del interruptor promete de más |
| S-18b | Info | Campo de contraseña en pantalla: enmascarado y `KeyboardType.Password` — correcto |

Orden sugerido esta ronda: **S-20 → S-18 → S-17**. Los tres tocan el mismo tema de fondo: lo que la persona dice por voz (a veces su contraseña) no debe quedar guardado ni salir del teléfono sin que ella lo sepa.

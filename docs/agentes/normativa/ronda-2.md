# Ronda 2 · Normativa

**Base:** commit `8019dd1` (rama `integracion/actividades`, local). **Fecha:** 28-09-2026.
**Aviso:** no es asesoría legal. Todo lo que diga "borrador" es un **borrador para revisión de abogado**.

## 1. Decisiones de Pp registradas

| Tema | Decisión | Qué cambia en los borradores |
|---|---|---|
| IA y menores | Por ahora solo lo que funciona sin IA. La IA está apagada por defecto y sin conectar hasta decidir proveedor y edades | El aviso dice que la IA está apagada y no disponible para menores |
| Responsable legal | Pp, mientras solo haya cuentas de demostración. Antes de datos reales buscará un convenio con el CECAPI o la escuela | Los avisos dejan el responsable entre corchetes |
| Edades | Pendiente. Se redacta para el **peor caso**: menores de 13 años incluidos y consentimiento obligatorio de madre, padre o tutor | Ver `borradores-politicas.md`, secciones 3 y 9 |
| Documentos de equipos y del CECAPI | Los pide Pp; no se inventan | Siguen como `[por entregar]` |
| `targetSdk 36` | No se cambia ahora | Registrado como riesgo R-1 (abajo) |
| Aviso y consentimiento dentro de la app | No se construyen hasta que un abogado revise y Pp decida las edades | Sin cambio |

## 2. Qué se verificó de lo construido en `8019dd1`

| Hallazgo | Qué se hizo | Verificado en el código | Estado |
|---|---|---|---|
| N-01 (IA y menores) | Interruptor "inteligencia artificial" en Configuración, apagado por defecto (`DeviceSettings.kt`, `KEY_AI`, valor inicial `false`); `IntentFallback.resolver` es nulo si está apagado | Sí. Pero la pantalla del Asistente Inteligente no pasa por el interruptor (N-21) y el interruptor vale para todo el teléfono, no para cada cuenta (N-23) | **Mitigado en parte** |
| N-04 (borrar cuenta) | `AccountEraser` borra las fotos (rutas en `documentos_escaneados` y `escaneos_entorno`) y la fila de `usuarios`; lo demás se borra en cascada | Sí: todas las tablas con `usuarioId` declaran `ForeignKey.CASCADE` hacia `usuarios`, y sus hijas hacia su tabla madre (Módulos 1 a 7). Quedan fuera datos que no están en la base (N-22), y no hay forma de pedirlo sin sesión ni por web (N-24) | **Mitigado en parte** |
| N-08 (seguridad, parte del log) | `LoginViewModel` ya no escribe el usuario | Sí: `Log.d(TAG, "login attempt success=...")` | **Corregido** (solo el log; lo demás de N-08 sigue abierto, ver Agente 3) |
| N-15 (historiales sin límite) | Ahora se pueden borrar al borrar la cuenta | Sí | Sigue abierto: no hay borrado automático por tiempo |

Los demás hallazgos de la ronda 1 (N-02, N-03, N-05 a N-07, N-09 a N-14, N-16 a N-20) siguen como **Nuevo**, sin cambios.

## 3. Hallazgos nuevos

### N-21 · Alto · La pantalla del Asistente Inteligente no respeta el interruptor de IA
- **Dónde:** `feature/modulo3_asistenteinteligente/AiAssistantViewModel.kt` (`onQuestionAsked` → `repository.ask`) → `AiAssistantRepository.kt:20` → `ProxyAiAssistantApi.ask` (`AiAssistantApi.kt`). La ruta sigue registrada en `core/navigation/CecapiNavGraph.kt` (`AI_ASSISTANT`).
- **Qué pasa:** el interruptor solo apaga `IntentFallback.resolver`. Esta pantalla envía la pregunta directo a `BuildConfig.AI_PROXY_BASE_URL` sin revisar el interruptor. **Hoy no sale nada:** la pantalla no está en el menú (`ModuleVoice.menu` no la incluye) y la dirección es un ejemplo (`https://TU-BACKEND.example.com/api/asistente`, `app/build.gradle.kts:21`). Pero cuando el Equipo 2 ponga una dirección real, cualquier camino que abra esa pantalla enviaría preguntas con la IA apagada. Eso contradice lo que la app le dice a la persona y la decisión de "sin IA para menores".
- **Cómo reproducirlo:** leer las llamadas: `grep -rn "api.ask\|repository.ask" app/src/main/java`. Ninguna consulta `aiEnabled`.
- **Sugerencia:** poner la revisión en un solo lugar, en la capa que sale a internet (`ProxyAiAssistantApi.ask` y la futura `AiVisionApi`): si la IA está apagada, devolver un fallo sin conectarse. Así ningún módulo actual ni futuro se lo salta. No incluyo un parche porque hoy no hay envío real (no es un riesgo inmediato).
- **Estado:** Nuevo.

### N-22 · Medio · "Borrar mi cuenta" deja datos fuera de la base
- **Dónde:** `core/data/AccountEraser.kt`; `core/voice/DeviceSettings.kt:128` (`preferred_name`); `AndroidManifest.xml:23` (`allowBackup="true"`); `cacheDir`.
- **Qué pasa:**
  - el nombre con que el asistente llama a la persona (`preferred_name`) está en DataStore del teléfono y no se borra;
  - la caché no se limpia;
  - si el teléfono hizo una copia de seguridad en Google, ahí siguen la base y las fotos anteriores.

  El art. 24 de la LFPDPPP da derecho a la cancelación, y la persona escucha "borré todos tus datos".
- **Cómo reproducirlo:** decir tu nombre en Personalización, borrar la cuenta y abrir de nuevo: el asistente te sigue llamando igual (**por verificar** en el teléfono con el Agente 1).
- **Sugerencia:**
  - borrar también `preferred_name` y la caché al borrar la cuenta, o cambiar el mensaje hablado a "borré tu cuenta y tus datos de la app";
  - desactivar `allowBackup` o excluir la base y las fotos (tarea del Agente 3).
- **Estado:** Nuevo.

### N-23 · Medio · El interruptor de IA es del teléfono, no de la cuenta
- **Dónde:** `DeviceSettings.kt` (comentario de la clase: "They are per device, not per account").
- **Qué pasa:** si una persona adulta enciende la IA, queda encendida también para la cuenta de un menor en el mismo teléfono (por ejemplo, un teléfono compartido en la familia o en el CECAPI). La advertencia hablada ("No la actives si quien usa la aplicación es menor de edad") depende de que alguien la escuche y la cumpla.
- **Sugerencia:** cuando exista el filtro por edad, guardar el permiso de IA por cuenta y que una cuenta de menor no pueda encenderlo. Hasta entonces sirve como está, porque la IA no está conectada.
- **Estado:** Nuevo.

### N-24 · Medio · No se puede pedir el borrado sin sesión ni por web
- **Dónde:** `SettingsViewModel.askDeleteAccount` exige una sesión iniciada.
- **Qué pasa:** quien olvidó la contraseña, un tutor que pide borrar los datos de su hija o hijo, o una persona sin el teléfono no tiene cómo pedirlo. La ley permite ejercer ARCO por medio del representante (LFPDPPP art. 21; Reglamento de 2011 art. 89, último párrafo, que remite al Código Civil para menores). Google Play pide además un enlace web ([fuente](https://support.google.com/googleplay/android-developer/answer/13327111)).
- **Sugerencia:** no es código: un correo de privacidad y una página web con el procedimiento. Ya está en el borrador del aviso integral.
- **Estado:** Nuevo.

### N-25 · Medio · El mensaje "nada de lo que digas se envía por internet" no es exacto
- **Dónde:** `SettingsViewModel.onAiEnabledChanged` (mensaje al apagar la IA); `core/voice/VoiceEngine.kt` usa `SpeechRecognizer.createSpeechRecognizer`.
- **Qué pasa:** la documentación oficial de Android dice que la implementación de `SpeechRecognizer` "probablemente transmita el audio a servidores remotos" ([fuente](https://developer.android.com/reference/android/speech/SpeechRecognizer), consultada el 28-09-2026). Al apagar la IA, la voz puede seguir saliendo al servicio de reconocimiento del teléfono. La ley prohíbe obtener datos por medios engañosos y protege la "expectativa razonable de privacidad" (LFPDPPP art. 6).
- **Sugerencia:** una de dos, que decide Pp:
  - (a) cambiar el mensaje a "La inteligencia artificial está apagada: no se le envía nada. El reconocimiento de voz de tu teléfono puede usar internet";
  - (b) usar `SpeechRecognizer.createOnDeviceSpeechRecognizer`, que existe desde la API 31, Android 12 (verificado en la misma fuente; el Oppo tiene Android 12), cuando `isOnDeviceRecognitionAvailable` lo permita. Lo mide el Agente 4, porque puede reconocer peor.
- **Estado:** Nuevo.

## 4. Riesgos registrados

### R-1 · `targetSdk 35` y Google Play
- **Fecha del riesgo:** desde el **31-08-2026**, Google Play no acepta apps nuevas ni actualizaciones con `targetSdk` menor a 36. Se puede pedir prórroga hasta el **01-11-2026** ([fuente](https://support.google.com/googleplay/android-developer/answer/11926878), consultada el 28-09-2026).
- **Estado en el código:** `compileSdk = 35`, `targetSdk = 35` (`app/build.gradle.kts:11` y `:16`, commit `8019dd1`).
- **Decisión:** no se cambia ahora (Pp, 28-09-2026). AGP 8.5.2 está congelado.
- **Decisión pendiente:** el canal de publicación. Si es Google Play, hay que mover versiones antes de la primera subida. Si es un APK por la web, esta regla no aplica, pero desde 2027 sí aplica la verificación de desarrollador (N-07).
- **Por verificar:** qué versión mínima de AGP necesita `compileSdk 36` (Agente 4).

### R-2 · Reglamento de la ley de datos
- La nueva LFPDPPP (DOF 20-03-2025) abrogó la ley de 2010 (transitorio Segundo), pero **no menciona el Reglamento de 2011**. Su transitorio Décimo Segundo ordena adecuar los reglamentos en 90 días.
- Al 28-09-2026 **no encontré un Reglamento nuevo publicado**. La Cámara de Diputados todavía publica el de 2011 ([fuente](https://www.diputados.gob.mx/LeyesBiblio/regley/Reg_LFPDPPP.pdf)).
- Supuesto: el de 2011 sigue aplicando en lo que no contradiga la ley nueva. **Por verificar con el abogado.** Los borradores citan el Reglamento solo como apoyo.

## 5. Peor caso de edades: qué cambia

Verificado: la LFPDPPP vigente **no menciona a menores de edad** (búsqueda de "menor" en el texto oficial: sin resultados sobre titulares menores). En México la regla sale de otras leyes:

- **Mayoría de edad a los 18** (Código Civil Federal art. 646).
- **Representan a menores quienes ejercen la patria potestad** (CCF arts. 412 y 425).
- **Para derechos ARCO de menores se aplican las reglas de representación del Código Civil** (Reglamento de 2011, art. 89, último párrafo).
- **Niñas, niños y adolescentes tienen derecho a la protección de sus datos, y quienes los tienen a su cargo deben orientarlos y supervisarlos** (LGDNNA art. 76).

Consecuencia para México: la ley no pone un corte especial en 13 años. **Todo menor de 18** necesita que firme su madre, padre o tutor. El corte de 13 años viene de fuera:

- **Google Play, políticas de Familias** ([fuente](https://support.google.com/googleplay/android-developer/answer/9893335), 28-09-2026). Si el público objetivo incluye niñas y niños:
  - declarar con exactitud edades, seguridad de datos y clasificación;
  - contenido apropiado;
  - no enviar identificadores del equipo (ID de publicidad, IMEI, número de serie, MAC, SSID, entre otros);
  - no pedir la ubicación precisa en apps solo para niños;
  - cumplir COPPA, el RGPD "y cualquier otra ley aplicable";
  - nada de chat anónimo con desconocidos;
  - publicidad solo con SDK certificados (la app no tiene publicidad; verificado: no hay SDK de anuncios en `app/build.gradle.kts`).
- **COPPA (EE. UU., menores de 13)**: solo si se ofrece en EE. UU.

Lo que la app debe hacer en el peor caso está en `borradores-politicas.md`, sección 9.

## 6. Fuentes nuevas de esta ronda

| Fuente | Consultada | Qué se usó |
|---|---|---|
| [Android `SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer) | 28-09-2026 | "likely to stream audio to remote servers"; `createOnDeviceSpeechRecognizer` desde API 31 |
| [Reglamento LFPDPPP 2011, Cámara de Diputados](https://www.diputados.gob.mx/LeyesBiblio/regley/Reg_LFPDPPP.pdf) | 28-09-2026 | Art. 89 (menores), art. 20 (carga de la prueba del consentimiento), art. 21 (revocación por el mismo medio) |
| [Políticas de Familias, Google Play](https://support.google.com/googleplay/android-developer/answer/9893335) | 28-09-2026 | Requisitos si el público incluye niñas y niños |
| [LFPDPPP, transitorios](https://www.diputados.gob.mx/LeyesBiblio/pdf/LFPDPPP.pdf) | 28-09-2026 | Segundo (abrogaciones) y Décimo Segundo (reglamentos) |

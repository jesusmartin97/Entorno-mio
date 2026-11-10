# Requisitos y hallazgos de normativa

Commit revisado: `173fa87` · 28-09-2026 · Borrador para revisión legal, no es asesoría legal.
"Verificado" = se leyó el código o la fuente oficial. "Por verificar" = no se pudo confirmar.

## 1. Mapa de datos

Ruta base del código: `app/src/main/java/com/cecapi/app/`.

| Dato | Para qué | Dónde se guarda (verificado) | Cuánto tiempo hoy | Con quién se comparte | ¿Puede borrarlo la persona? | ¿Sensible? |
|---|---|---|---|---|---|---|
| Usuario, nombre completo, rol, fecha de registro | Cuenta | Room, tabla `usuarios` (`MainAppData.kt`) | Sin límite | Nadie | No hay "borrar cuenta" (se buscó; cero resultados) | No por sí solo |
| Hash de la contraseña | Iniciar sesión | `usuarios.contrasenaHash`, SHA-256 sin sal (hallazgo del Agente 3) | Sin límite | Nadie | No | No, pero es dato de autenticación |
| Voz (audio) | Comandos y dictado, también la contraseña dicha en voz alta | No se guarda en la app. Pasa por `SpeechRecognizer.createSpeechRecognizer` (`core/voice/VoiceEngine.kt:450` y `:569`) sin pedir modo sin conexión | No se guarda en la app | **Por verificar:** el reconocedor del teléfono (en muchos es Google) puede enviar el audio a sus servidores | No aplica en la app | La voz puede identificar a la persona |
| Texto de los comandos y respuestas | Historial | `comandos_voz`, `historial_comandos`, `respuestas_auditivas` | Sin límite | Nadie | Parcial (borrar chats) | Depende de lo que diga |
| Preguntas y respuestas a la IA, resumen de contexto | Asistente | `consultas_ia`, `respuestas_ia`, `contextos_conversacion` | Sin límite | Servidor propio → proveedor de IA (Gemini u otros) cuando se conecte | Parcial | Puede incluir salud u otros |
| Fotos de documentos y texto leído | Lector | `filesDir/<carpeta>/scan_*.jpg` y `pick_*.jpg` (`core/ui/CameraComponents.kt:218` y `:241`); `documentos_escaneados`, `textos_extraidos` | Se ofrece borrar las de más de 30 días | ML Kit en el teléfono; servidor de IA si se usa "investigar" o modos con IA | Sí, con "liberar espacio" | Pueden mostrar datos de terceros (recibos, recetas, identificaciones) |
| Fotos del entorno y descripciones | Describir lo que hay enfrente | `filesDir`, `escaneos_entorno`, `objetos_detectados`, `descripciones_entorno` | Igual que arriba | ML Kit en el teléfono; servidor de IA con el diseño previsto | Sí | Pueden mostrar caras de terceros |
| Resultados de Actividades | Progreso | `resultados_ejercicios`, `resultados_vibracion`, `niveles_aprendizaje` | Sin límite | Nadie | No | Puede revelar capacidades físicas |
| Solicitudes generadas y sus datos | Plantillas | `solicitudes_generadas`, `datos_solicitud` | Sin límite | Nadie | No | Depende del contenido |
| Notificaciones de otras apps (título y texto) | Leerlas en voz alta | Solo en memoria (`notifications/NotificationInbox.kt`) | Mientras corre el proceso | Nadie | Se pierden al cerrar | Pueden contener mensajes privados de terceros |
| Métricas de ML Kit (modelo del teléfono, versión, tiempos, tamaño de imagen) | Diagnóstico de Google | Las envía el SDK | Lo que decida Google | Google ([fuente](https://developers.google.com/ml-kit/android-data-disclosure)) | No | No |
| Nombre de usuario en logs | Depuración | `LoginViewModel.kt:297` escribe `user=$username` con `Log.d` | Log del sistema | Quien tenga acceso por ADB | No | No, pero no debería registrarse |
| Copia de seguridad | Respaldo del sistema | `allowBackup="true"` en `AndroidManifest.xml` | Lo que dure la copia | Google Drive de la persona | No desde la app | Todo lo anterior |
| El hecho de tener discapacidad visual | Implícito: usar la app lo revela | No hay un campo, pero la cuenta existe en una app para personas ciegas | Sin límite | Proveedores que reciben datos de la app | No | **Sí, estado de salud** (art. 2, fr. VI LFPDPPP) |

## 2. Tabla de requisitos

Prioridad: **P0** = antes de usar la app con cualquier persona real; **P1** = antes de publicar en una tienda o en la web; **P2** = al crecer.

| # | Requisito | Fuente y fecha | Qué falta en la app | Prioridad |
|---|---|---|---|---|
| R-01 | Aviso de privacidad simplificado al recoger datos por medios electrónicos o sonoros, con identidad y domicilio del responsable, datos (marcando los sensibles), finalidades y opciones para limitar uso; y dónde está el integral | LFPDPPP arts. 15 y 16, fr. II (DOF 20-03-2025) | No existe. Debe poder escucharse, no solo leerse (art. 16 admite formato "sonoro") | P0 |
| R-02 | Aviso integral con además: medios para derechos ARCO y cómo se comunican los cambios | LFPDPPP art. 15, fr. V y VI | No existe; se necesita una página web pública | P0 |
| R-03 | Consentimiento expreso y por escrito para datos sensibles, con firma autógrafa, electrónica o mecanismo de autenticación | LFPDPPP art. 8 | No existe. Para personas ciegas conviene un formato físico firmado o una firma electrónica accesible; decidir con abogado | P0 |
| R-04 | Menores: consentimiento de quien ejerce la patria potestad (sus representantes legítimos); mayoría de edad a los 18 | Código Civil Federal arts. 412, 425 y 646; LGDNNA art. 76 (derecho a la protección de sus datos) | No hay pregunta de edad ni flujo de tutor | P0 |
| R-05 | Revocar el consentimiento en cualquier momento, con mecanismo descrito en el aviso | LFPDPPP art. 7 | No hay | P0 |
| R-06 | Derechos ARCO: responder en máximo 20 días y hacerlo efectivo en 15 más; gratis | LFPDPPP arts. 21 a 34 (art. 31 plazos, art. 34 gratuidad) | No hay contacto ni procedimiento; no hay "borrar mi cuenta" ni "exportar mis datos" | P0 |
| R-07 | Medidas de seguridad administrativas, técnicas y físicas acordes a la sensibilidad | LFPDPPP art. 18 | Ver hallazgos del Agente 3: SHA-256 sin sal, `allowBackup`, base y fotos sin cifrar, log con usuario | P0 |
| R-08 | Avisar de inmediato a la persona si hay una fuga que afecte sus derechos | LFPDPPP art. 19 | No hay plan; borrador en `borradores-politicas.md` | P0 |
| R-09 | Confidencialidad de todas las personas que tratan datos (equipos, maestros), aun después de terminar | LFPDPPP art. 20 | No hay acuerdo firmado con los equipos | P0 |
| R-10 | Minimizar el tiempo de tratamiento de datos sensibles | LFPDPPP art. 12 | Historiales sin límite | P1 |
| R-11 | Transferencias a terceros (proveedor de IA, si no actúa como encargado) con el aviso y cláusula de aceptación | LFPDPPP arts. 35 y 36 | Falta decidir si Google/otros son "encargados" (remisión) o terceros (transferencia) y ponerlo en el aviso | P0 si se conecta la IA |
| R-12 | Proveedores de IA: edad mínima y uso de datos | Gemini: 18+, no apps que probablemente usen menores, plan gratuito con revisión humana (términos 28-04-2026); Groq 18+; Tavily 18+; OpenRouter 18+ y "no enviar datos de menores de 18"; Mistral 13+ con permiso de tutor y plan gratuito que entrena por defecto | Incompatible con menores. Ver N-01 | P0 |
| R-13 | Gemini no se usa para dar consejo médico ni en práctica clínica | Términos Gemini 28-04-2026 | Falta la política de contenido de la IA y avisos | P0 si se conecta |
| R-14 | Google Play: `targetSdk` 36 para apps nuevas y actualizaciones desde 31-08-2026; prórroga posible al 01-11-2026 | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878) | La app tiene 35 (`app/build.gradle.kts:16`) | P1 |
| R-15 | Google Play: política de privacidad pública, formulario de seguridad de datos (incluidas las métricas de ML Kit) | Políticas de datos de usuario; [ML Kit](https://developers.google.com/ml-kit/android-data-disclosure) | No existe | P1 |
| R-16 | Google Play: borrado de cuenta dentro de la app y por un enlace web | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/13327111), obligatorio desde 2024 | No existe | P1 |
| R-17 | Google Play: declarar el servicio en primer plano de micrófono con descripción, impacto y video | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/13392821) | Falta la declaración y el video de "escuchar fuera de la app" | P1 |
| R-18 | Google Play: declaración de apps de salud (todas las apps la llenan) | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/14738291) | Decidir si la app declara funciones de salud; **por verificar** si la ayuda a personas con discapacidad entra en la categoría | P1 |
| R-19 | Google Play: público objetivo; si incluye menores, cumplir políticas de Familias; no chat anónimo dirigido a niños (anunciado 15-07-2026) | [Familias](https://support.google.com/googleplay/android-developer/answer/9893335); [anuncio 15-07-2026](https://support.google.com/googleplay/android-developer/answer/17134731) | Decidir franjas de edad | P1 |
| R-20 | Google Play: contenido generado por IA seguro y con forma de reportarlo | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/14094294); **por verificar** la política completa (answer/13985936) | No hay forma de reportar una respuesta de la IA | P1 |
| R-21 | Cuentas personales nuevas de Play: prueba cerrada con 12 personas durante 14 días antes de producción | [Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465) | Planear el calendario; las personas probadoras no pueden ser datos reales sin aviso | P1 |
| R-22 | Distribuir fuera de la tienda: registrar la app como desarrollador verificado (identidad, 25 USD) | [Android developer verification](https://developer.android.com/developer-verification): Brasil, Indonesia, Singapur y Tailandia desde 30-09-2026; global en 2027 | Aún no aplica en México; planear para 2027 | P2 (P1 en 2027) |
| R-23 | Accesibilidad: el Estado debe promover tecnologías accesibles; no hay una NOM de accesibilidad para apps privadas | LGIPD art. 32 (obliga a autoridades) | No es obligación legal directa para Pp; usar WCAG 2.2 y EN 301 549 como referencia de calidad | P2 |
| R-24 | Licencias y atribuciones: Open-Meteo CC BY 4.0 y solo uso no comercial en la API gratuita; Wikipedia CC BY-SA; ML Kit con sus términos | [Open-Meteo](https://open-meteo.com/en/terms); [ML Kit](https://developers.google.com/ml-kit/terms) | Falta pantalla o comando de "créditos y licencias"; falta saber quién grabó los 17 `.wav` | P1 |
| R-25 | UE: avisar que se habla con una IA (art. 50 Ley de IA, aplicable desde 02-08-2026); Gemini gratuito prohibido para personas en EEE, Suiza y Reino Unido | [Comisión Europea](https://digital-strategy.ec.europa.eu/en/faqs/transparency-obligations-under-article-50-ai-act); términos Gemini | Solo si se expande | P2 |
| R-26 | EE. UU.: COPPA (menores de 13) con consentimiento verificable de padres; reglas nuevas obligatorias desde 22-04-2026 | [Federal Register](https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule) | Solo si se expande | P2 |

## 3. Hallazgos

Formato común. Estado en la ronda 1: **Nuevo**. **Actualización de la ronda 2 (`8019dd1`):**
- N-01: mitigado en parte.
- N-04: mitigado en parte.
- N-08: corregido solo lo del log.
- Los demás siguen igual.

Detalle en [ronda-2.md](ronda-2.md). La tabla de datos de arriba se revisó contra `173fa87`; en `8019dd1` ya no está la fila "Nombre de usuario en logs" y ya existe "borrar cuenta".

### N-01 · Crítico · Los proveedores de IA previstos prohíben a menores
- **Dónde:** diseño del servidor, `respaldo-cecapi-2026-09-23/ia-orquestador/README.md` (Groq, Mistral, Cloudflare, OpenRouter, Tavily, Gemini) y `feature/modulo3_asistenteinteligente/AiAssistantApi.kt`.
- **Qué pasa:** Gemini exige 18+ y prohíbe usarse en apps que "probablemente usen" menores; Groq, Tavily y OpenRouter piden 18+; OpenRouter además dice no enviar datos de menores de 18. Pp confirmó que habrá menores.
- **Cómo reproducirlo:** leer la sección "Age Requirements" de [los términos de Gemini](https://ai.google.dev/gemini-api/terms) (28-04-2026).
- **Sugerencia:** elegir una de tres salidas y consultarla con abogado: (a) la IA solo para cuentas de personas adultas verificadas y los menores usan únicamente lo local; (b) buscar un proveedor cuyos términos permitan menores con permiso del tutor (Mistral dice 13+ con permiso, pero su plan gratuito entrena con los datos; **por verificar** su plan de pago); (c) un modelo en el propio teléfono o en un servidor propio, sin terceros. Mientras tanto, no conectar `/entender` con cuentas de menores.
- **Estado:** Nuevo.

### N-02 · Crítico · No hay consentimiento para datos sensibles
- **Dónde:** registro (`RegisterViewModel.kt`) y todo el flujo de inicio.
- **Qué pasa:** usar la app revela un estado de salud (discapacidad visual) y se guardan voz transcrita, fotos y resultados de ejercicios. La ley pide consentimiento expreso y por escrito con firma (art. 8). Para menores, de quien ejerce la patria potestad (CCF arts. 412 y 425).
- **Cómo reproducirlo:** crear una cuenta: no se pide ningún consentimiento.
- **Sugerencia:** antes de cualquier persona real, formato de consentimiento firmado (en papel o firma electrónica) y registrar en la app la fecha y versión del aviso aceptado. En la app, pantalla hablada que explique el aviso simplificado y pida confirmación.
- **Estado:** Nuevo.

### N-03 · Alto · No hay aviso de privacidad
- **Dónde:** `app/src/main` (búsqueda de "privacidad": cero resultados).
- **Qué pasa:** falta el aviso simplificado (al recoger datos) y el integral (en un sitio consultable).
- **Sugerencia:** usar los borradores de `borradores-politicas.md`; agregar el comando "aviso de privacidad" que lo lea en voz alta y un enlace al integral.
- **Estado:** Nuevo.

### N-04 · Alto · No se pueden ejercer derechos ARCO ni borrar la cuenta
- **Dónde:** no hay función de borrar cuenta ni exportar datos.
- **Qué pasa:** la ley da 20 días para responder (art. 31); Google Play exige borrado dentro de la app y por web.
- **Sugerencia:** comando "borrar mi cuenta" con doble confirmación que borre la fila de `usuarios` y todo lo ligado (tablas y fotos); correo de contacto ARCO en el aviso.
- **Estado:** Nuevo.

### N-05 · Alto · La voz y las fotos pueden salir a terceros sin aviso
- **Dónde:** `core/voice/VoiceEngine.kt:450`, `:569` (reconocedor sin modo sin conexión); diseño del servidor de IA (fotos a Gemini).
- **Qué pasa:** el audio puede procesarse en servidores del proveedor del reconocedor (**por verificar** en el Oppo) y las fotos saldrían a Google. Con el plan gratuito de Gemini, personas revisoras pueden leer lo enviado. Las fotos pueden incluir a terceros.
- **Sugerencia:** decirlo en el aviso; pedir al constructor evaluar `EXTRA_PREFER_OFFLINE` o `createOnDeviceSpeechRecognizer` (Android 12+) para la contraseña; no usar un plan gratuito que entrene o revise para datos de personas reales.
- **Estado:** Nuevo.

### N-06 · Alto · `targetSdk 35` no se acepta en Google Play desde el 31-08-2026
- **Dónde:** `app/build.gradle.kts:11` y `:16`.
- **Qué pasa:** apps nuevas y actualizaciones deben apuntar a API 36. **Por verificar** con el Agente 4 si AGP 8.5.2 soporta compileSdk 36 (probablemente no; eso rompería la regla de no mover versiones).
- **Sugerencia:** Pp decide si se publica en Play; si sí, planear el cambio de versiones en una rama aparte o pedir la prórroga al 01-11-2026.
- **Estado:** Nuevo.

### N-07 · Medio · Publicar "por la web" también exigirá identidad de desarrollador
- **Dónde:** plan de publicación.
- **Qué pasa:** desde 2027 (global) las apps instaladas fuera de la tienda deben estar registradas por un desarrollador verificado; si no, la instalación pide pasos avanzados y una espera de 24 horas. El anuncio del 15-07-2026 dice que también se registran en Play Console las apps distribuidas fuera de Play.
- **Sugerencia:** si Pp publica a su nombre, esto liga su identidad oficial a la app. Considerarlo al decidir si el responsable será él o una institución.
- **Estado:** Nuevo.

### N-08 · Alto · Seguridad insuficiente para datos sensibles (depende del Agente 3)
- **Dónde:** `core/util/PasswordHasher.kt`, `AndroidManifest.xml` (`allowBackup="true"`), `filesDir` sin cifrar, `LoginViewModel.kt:297` (registra el usuario en el log), cuentas de demostración fijas en `core/di/DatabaseModule.kt`.
- **Qué pasa:** el art. 18 pide medidas acordes a la sensibilidad; con datos de salud y de menores el estándar esperado sube.
- **Sugerencia:** aplicar lo que priorice el Agente 3 antes de datos reales; quitar las cuentas de demostración de la versión que usen personas reales.
- **Estado:** Nuevo.

### N-09 · Medio · Notificaciones de otras apps contienen datos de terceros
- **Dónde:** `notifications/CecapiNotificationListener.kt:66-67`.
- **Qué pasa:** se lee título y texto de mensajes de otras personas. Hoy solo se guardan en memoria (bien). Si algún día se envían a la IA, sería tratar datos de terceros sin su aviso.
- **Sugerencia:** regla escrita: el contenido de notificaciones nunca sale del teléfono ni se guarda en la base. Ponerlo en el aviso.
- **Estado:** Nuevo.

### N-10 · Medio · Declaraciones de Play para micrófono en segundo plano
- **Dónde:** `service/ListeningService.kt`, `AndroidManifest.xml` (`foregroundServiceType="microphone"`).
- **Qué pasa:** Play pide descripción, impacto y video del uso; Android no permite iniciar este servicio desde segundo plano.
- **Sugerencia:** grabar el video cuando se pruebe en el Oppo; mantener la función apagada por defecto.
- **Estado:** Nuevo.

### N-11 · Medio · No hay plan ante fugas de datos
- **Qué pasa:** el art. 19 obliga a informar de inmediato; los arts. 62 y 64 prevén prisión si hay ánimo de lucro, y se duplica con datos sensibles.
- **Sugerencia:** adoptar el borrador de respuesta ante fugas.
- **Estado:** Nuevo.

### N-12 · Medio · No hay acuerdos de confidencialidad con los equipos
- **Qué pasa:** varios equipos, un integrante remoto y el maestro tendrían acceso a datos si se usan reales (art. 20).
- **Sugerencia:** acuerdo corto firmado por cada persona antes de la autorización de datos reales.
- **Estado:** Nuevo.

### N-13 · Medio · Origen de los audios sin documentar
- **Dónde:** `app/src/main/res/raw/*.wav` (17 archivos).
- **Qué pasa:** no se sabe quién los grabó o de dónde salieron ni con qué licencia.
- **Sugerencia:** el Equipo 4 dice el origen de cada uno; si alguno es de internet sin licencia clara, reemplazarlo.
- **Estado:** Nuevo.

### N-14 · Medio · Falta política de contenido de la IA
- **Qué pasa:** Gemini prohíbe dar consejo médico; Play exige que la IA no genere contenido dañino; personas ciegas preguntarán por medicinas, dinero y trámites.
- **Sugerencia:** adoptar el borrador de política de contenido; incluir el comando "reportar respuesta".
- **Estado:** Nuevo.

### N-15 · Medio · Historiales sin límite de tiempo
- **Qué pasa:** el art. 12 pide limitar el tiempo de los datos sensibles.
- **Sugerencia:** borrar automáticamente historial de IA y comandos a los 90 días (propuesta; decide Pp).
- **Estado:** Nuevo.

### N-16 · Bajo · Atribuciones de Open-Meteo y Wikipedia
- **Qué pasa:** CC BY 4.0 exige atribución; la API gratuita de Open-Meteo es solo no comercial. Wikipedia es CC BY-SA.
- **Sugerencia:** la respuesta hablada puede decir "según Open-Meteo" o "según Wikipedia" (el diseño ya prevé el campo `fuente`); agregar "créditos" en Configuración.
- **Estado:** Nuevo.

### N-17 · Bajo · La contraseña se dice en voz alta
- **Qué pasa:** personas cercanas la oyen; el reconocedor la procesa.
- **Sugerencia:** avisarlo al crear cuenta y ofrecer alternativa (PIN por toques o huella del teléfono). Coordinar con el Agente 3.
- **Estado:** Nuevo.

### N-18 · Bajo · La accesibilidad no es obligación legal directa, pero conviene medirla
- **Qué pasa:** la LGIPD obliga a las autoridades, no a particulares. Si la app se usa en escuelas públicas o con el CECAPI como institución, puede exigirse.
- **Sugerencia:** tomar WCAG 2.2 (nivel AA) como referencia de calidad; el Agente 1 puede probar con TalkBack.
- **Estado:** Nuevo.

### N-19 · Bajo · Apps web e iPhone: reglas por verificar
- **Qué pasa:** no se verificó qué permite Apple a una app web (micrófono en segundo plano, notificaciones) ni sus reglas para menores.
- **Sugerencia:** investigar cuando Pp decida la plataforma.
- **Estado:** Nuevo.

### N-20 · Bajo · Responsable persona física
- **Qué pasa:** la excepción de "uso exclusivamente personal" (art. 1, fr. II) no aplica, porque la app trata datos de otras personas. Pp responde personalmente; las multas van de 100 a 320,000 UMA y se pueden duplicar con datos sensibles (art. 59).
- **Sugerencia:** antes de datos reales, hablar con la institución para que el CECAPI o la escuela sea el responsable, o que firme un convenio; ver preguntas.
- **Estado:** Nuevo.

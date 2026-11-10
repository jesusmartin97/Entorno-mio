# Qué sale del teléfono

> Vigente · Dueño: Pp · Verificado el 1 oct 2026 contra main local en e968668, leyendo cada llamada a la red del código · No es un texto legal: los avisos para usuarios los redacta el Agente 5 y los revisa una persona abogada

Con e968668, lo que la persona dice sale del teléfono por dos caminos sin que pueda apagarlos: el reconocedor de voz del teléfono, siempre, y Wikipedia, con **cualquier frase que la app no entienda**. El interruptor de IA no controla ninguno de los dos.

| Función | ¿Sale del teléfono? | A dónde | Qué se envía | ¿Lo controla la persona? | Dónde se ve en el código |
| --- | --- | --- | --- | --- | --- |
| Reconocer la voz | Puede salir | Al servicio de reconocimiento del teléfono (en el Oppo, el de Google) | El audio de lo que se dice, incluido el usuario y la contraseña al dictarlos | No desde la app. La app no pide reconocimiento sin conexión, así que el teléfono decide | `VoiceEngine.kt`: `SpeechRecognizer` sin `EXTRA_PREFER_OFFLINE` |
| Buscar en Wikipedia y Wikidata | **Sí, con la IA apagada** | es.wikipedia.org y wikidata.org (Fundación Wikimedia) | Desde ecc7b8b, **la frase completa** si no es un comando y tiene 3 letras o más; antes, solo lo que seguía a "qué es", "busca"... Más la dirección IP y el identificador "CECAPI-App/1.0" | No: no hay interruptor | `WikipediaLookup.kt`, `WikidataLookup.kt`, `HomeViewModel.kt:306` |
| IA en internet | No, mientras el interruptor esté apagado (así empieza) | Al backend del Equipo 2 (Gemini, con respaldo de Groq). La app apunta a `http://TU-IP-LOCAL:8000/preguntar`, un ejemplo | La pregunta y el contexto de la conversación | Sí, en Configuración, sección Privacidad | `AiAssistantApi.kt` revisa `intentFallback.enabled` antes de conectarse |
| Tráfico sin cifrar | Permitido | Cualquier dirección `http://` | Lo que se envíe por ahí | No | `usesCleartextTraffic="true"` en el manifiesto principal, no solo en el de pruebas |
| Leer texto de una foto | No | En el teléfono (ML Kit incluido en la app) | Nada | No aplica | `mlkit-text-recognition` 16.0.1 |
| Describir lo que hay enfrente | No | En el teléfono (ML Kit) | Nada | No aplica | `mlkit-object-detection`, `mlkit-image-labeling` |
| Voz de la app | Depende de la voz elegida | Algunas voces del teléfono necesitan internet | El texto que la app lee en voz alta | Sí: Personalización marca las voces que necesitan internet | `VoiceOption.needsInternet` |
| Notificaciones de otras apps | No | Se guardan solo en memoria | Nada | Sí, en Configuración | `notifications/` |
| Copias de seguridad de Android | No, desde f5e7fe8 | — | Nada | No aplica | `allowBackup="false"` |

## Lo que la app le dice hoy a la persona

- Configuración, sección Privacidad: "Qué sale de tu teléfono: por defecto, nada" (`SettingsScreen.kt:248`).
- Lista de comandos de Configuración: "apagada, nada sale de tu teléfono" (`CommandCatalog.kt:105`).

Las dos frases dejaron de ser ciertas con d5ffd5e, por Wikipedia. Y nunca fueron del todo ciertas, por el reconocedor de voz (hallazgo S-05 del Agente 3). Ver el hallazgo D-07.

## Por verificar

- Si ML Kit envía a Google datos de uso o diagnóstico, aunque analice las fotos en el teléfono.
- Qué hace el reconocedor del Oppo sin internet: si tiene el paquete de español descargado, o si deja de funcionar.

## Fuentes

- [Política de privacidad de la Fundación Wikimedia](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy), consultada el 29 sep 2026: recibe la IP, el navegador o dispositivo y las páginas pedidas, y en la mayoría de los casos los borra o anonimiza a los 90 días.
- [Sección de seguridad de los datos de Google Play](https://support.google.com/googleplay/android-developer/answer/10787469?hl=es), consultada el 29 sep 2026: "recoger" significa transmitir datos fuera del dispositivo del usuario.

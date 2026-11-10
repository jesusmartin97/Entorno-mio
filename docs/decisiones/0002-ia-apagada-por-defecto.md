# 0002. IA como "cerebro temporal", apagada por defecto

> Vigente · Dueño: Pp · Verificado el 1 oct 2026 contra main local en e968668

## Contexto

La app funciona sin internet con comandos locales. Una IA en un servidor puede entender las frases que la app no reconoce. Según el informe del Agente 5 (N-01, borrador sin revisión legal), los términos de la API de Gemini no permiten apps dirigidas a menores de 18 años o que probablemente usen, y Pp confirmó que habrá menores.

## Decisión

1. **Cerebro temporal (25 sep 2026).** La IA solo traduce una frase no entendida a un comando que la app ya conoce, o da una respuesta corta. Se conecta en `IntentFallback.resolver` (`core/voice/IntentFallback.kt`), que espera el endpoint `/entender` del Equipo 2.
2. **Apagada por defecto (28 sep 2026).** El interruptor está en Configuración, sección Privacidad, y se guarda en `DeviceSettings.aiEnabled`, que empieza en falso. Mientras está apagada, `resolver` devuelve nulo y ninguna frase se envía a un servidor de IA.
3. **Por ahora, solo lo que funciona sin IA**, hasta decidir proveedor y edades.

## Consecuencias

- Desde 024dfa8 (30 sep), `AiResolverSetup` registra el resolver: la IA está conectada en el código y sigue apagada por defecto. El backend es del Equipo 2 (Alejandro: Gemini con respaldo de Groq). La app apunta a `http://TU-IP-LOCAL:8000/preguntar`, un ejemplo, así que fuera de la red de quien corre el backend no responde.
- **Desde ecc7b8b (30 sep), cualquier frase que la app no entienda va completa a Wikipedia**, con la IA apagada o encendida (hallazgo D-08, crítico).
- "Dime más" sin IA solo sirve para leer el resto de un resumen de Wikipedia (hallazgo D-01, cambió con d5ffd5e).
- **Desde d5ffd5e (29 sep 2026), Wikipedia sale a internet con la IA apagada.** "Qué es", "quién es", "busca"... envían las palabras a es.wikipedia.org sin pasar por el interruptor. Esto no es una decisión de Pp: está por decidir si se acepta, con qué filtro y con qué aviso (hallazgos D-07 y D-08 del informe de documentación; el Agente 5 revisa el contenido para menores).

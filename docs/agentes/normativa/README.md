# Agente 5 · Normativa, políticas y expansión

**Ronda 2:** base `8019dd1`, 28-09-2026 (ver [ronda-2.md](ronda-2.md)). Todo texto de política es un **borrador para revisión de abogado**.
**Ronda 1, revisada contra:** rama `integracion/actividades`, commit `173fa87` (el encargo decía `f032349`; la rama ya avanzó dos commits, que solo agregan galería y comandos sin internet; no cambian el mapa de datos salvo "elegir foto de la galería").
**Fecha:** 28 de septiembre de 2026.
**Aviso:** esto no es asesoría legal. Es una lista de trabajo y borradores para que los revise una persona abogada.

## Índice

1. [Ronda 2](ronda-2.md) (base `8019dd1`): decisiones de Pp, verificación de lo construido, hallazgos N-21 a N-25, riesgos R-1 (`targetSdk`) y R-2 (Reglamento), y peor caso de edades.
2. [Requisitos y hallazgos](requisitos-y-hallazgos.md) (ronda 1, base `173fa87`): mapa de datos, tabla de requisitos con fuente y hallazgos N-01 a N-20.
3. [Borradores de políticas](borradores-politicas.md) (versión 2, **borrador para revisión de abogado**): avisos, consentimiento de tutor y de personas adultas, términos, conservación, IA, fugas, menores y contenido.
4. [Preguntas y documentos que necesito](preguntas-y-documentos.md): lo que solo Pp o la institución pueden responder.

**Estado al 28-09-2026 (ronda 2):** el punto 1 de abajo está mitigado en parte (IA apagada por defecto, ver N-21 y N-23). "Borrar mi cuenta" ya existe (N-24 sigue abierto) y el log ya no escribe el usuario. Los puntos 2, 3 y 5 siguen abiertos. El punto 4 queda como riesgo R-1, con la decisión pendiente.

## Lo más urgente, en cinco puntos

1. **Gemini (y Groq, Tavily y OpenRouter) no se pueden usar si la app la usan menores.** Los términos de la API de Gemini dicen que no se usará "como parte de un sitio web, aplicación u otro servicio (…) dirigido o que probablemente usen personas menores de 18 años". Pp confirmó que habrá menores. Hay que decidir antes de conectar la IA (ver N-01).
2. **El dato "tiene discapacidad visual" es un dato sensible** (estado de salud). La ley pide consentimiento **expreso y por escrito**, con firma autógrafa, electrónica u otro mecanismo de autenticación (art. 8 LFPDPPP). En menores, lo firma quien ejerce la patria potestad. La app hoy no pide ningún consentimiento (N-02).
3. **La app no tiene aviso de privacidad** en ninguna pantalla ni archivo (se buscó "privacidad" en `app/src/main`: cero resultados) (N-03).
4. **Google Play exige `targetSdk 36` desde el 31 de agosto de 2026** para apps nuevas y actualizaciones; la app tiene 35. Subirlo choca con la regla de no mover AGP/Gradle. Se puede pedir prórroga al 1 de noviembre de 2026 (N-06).
5. **Distribuir el APK "por la web" también tendrá reglas:** Google exigirá desde 2027, en todo el mundo, que las apps instaladas fuera de la tienda estén registradas por un desarrollador verificado (identificación oficial y pago de 25 USD). En Brasil, Indonesia, Singapur y Tailandia empieza el 30 de septiembre de 2026 (N-07).

## Qué está verificado y qué no

- **Verificado leyendo el texto oficial:** LFPDPPP (texto vigente, última reforma DOF 14-11-2025), Ley General para la Inclusión de las Personas con Discapacidad (última reforma DOF 14-06-2024), Ley General de los Derechos de Niñas, Niños y Adolescentes (última reforma DOF 15-01-2026), Código Civil Federal (última reforma DOF 14-11-2025), términos de la API de Gemini (actualizados el 28-04-2026), términos de Open-Meteo.
- **Verificado con páginas de ayuda de Google Play y resúmenes de búsqueda** (no se leyó cada política completa): nivel de API, verificación de desarrolladores, borrado de cuenta, servicio en primer plano, declaración de apps de salud, prueba cerrada de 12 personas, anuncio de políticas del 15-07-2026.
- **Verificado solo por resúmenes de búsqueda** (hay que leer el documento original antes de firmar nada): términos de Groq, Mistral, OpenRouter y Tavily; COPPA; Ley de IA de la UE.
- **Verificado en el código:** permisos, tablas, llamadas a red, logs y archivos (ver el mapa de datos).
- **Supuesto o sin verificar:** marcado como "por verificar" en cada punto. En particular: si el nuevo Reglamento de la LFPDPPP ya se publicó (no se encontró; se sigue usando el de 2011 en lo que no contradiga la ley), términos de Cloudflare Workers AI para menores, reglas de Apple para apps web.

## Fuentes principales

| Fuente | Fecha consultada | Versión |
|---|---|---|
| [LFPDPPP, Cámara de Diputados](https://www.diputados.gob.mx/LeyesBiblio/pdf/LFPDPPP.pdf) | 28-09-2026 | Nueva ley DOF 20-03-2025, última reforma DOF 14-11-2025 |
| [Ley General para la Inclusión de las Personas con Discapacidad](https://www.diputados.gob.mx/LeyesBiblio/pdf/LGIPD.pdf) | 28-09-2026 | Última reforma DOF 14-06-2024 |
| [Ley General de los Derechos de Niñas, Niños y Adolescentes](https://www.diputados.gob.mx/LeyesBiblio/pdf/LGDNNA.pdf) | 28-09-2026 | Última reforma DOF 15-01-2026 |
| [Código Civil Federal](https://www.diputados.gob.mx/LeyesBiblio/pdf/CCF.pdf) | 28-09-2026 | Última reforma DOF 14-11-2025 |
| [Términos adicionales de la API de Gemini](https://ai.google.dev/gemini-api/terms) | 28-09-2026 | Actualizados 28-04-2026 |
| [Nivel de API objetivo, Google Play](https://support.google.com/googleplay/android-developer/answer/11926878) | 28-09-2026 | Vigente |
| [Anuncio de políticas de Google Play, 15-07-2026](https://support.google.com/googleplay/android-developer/answer/17134731) | 28-09-2026 | 15-07-2026 |
| [Verificación de desarrolladores de Android](https://developer.android.com/developer-verification) | 28-09-2026 | Vigente |
| [Borrado de cuenta, Google Play](https://support.google.com/googleplay/android-developer/answer/13327111) | 28-09-2026 | Vigente |
| [Servicios en primer plano, Google Play](https://support.google.com/googleplay/android-developer/answer/13392821) | 28-09-2026 | Vigente |
| [Declaración de apps de salud, Google Play](https://support.google.com/googleplay/android-developer/answer/14738291) | 28-09-2026 | Vigente |
| [Prueba cerrada para cuentas personales nuevas](https://support.google.com/googleplay/android-developer/answer/14151465) | 28-09-2026 | 12 personas, 14 días |
| [Políticas de Familias, Google Play](https://support.google.com/googleplay/android-developer/answer/9893335) | 28-09-2026 | Vigente |
| [Contenido generado por IA, Google Play](https://support.google.com/googleplay/android-developer/answer/14094294) | 28-09-2026 | Vigente |
| [Términos y privacidad de ML Kit](https://developers.google.com/ml-kit/terms) y [su guía de divulgación](https://developers.google.com/ml-kit/android-data-disclosure) | 28-09-2026 | Vigente |
| [Términos de Open-Meteo](https://open-meteo.com/en/terms) | 28-09-2026 | Vigente |
| [Groq Services Agreement](https://console.groq.com/docs/legal/services-agreement) | 28-09-2026 | Por leer completo |
| [Mistral: ¿pueden usarlo menores?](https://help.mistral.ai/en/articles/347631-can-children-use-mistral-products-and-services) | 28-09-2026 | Por leer completo |
| [OpenRouter Terms](https://openrouter.ai/terms) | 28-09-2026 | Por leer completo |
| [Tavily Terms](https://www.tavily.com/terms) | 28-09-2026 | Por leer completo |
| [COPPA, Federal Register 22-04-2025](https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule) | 28-09-2026 | Cumplimiento obligatorio desde 22-04-2026 |
| [Ley de IA de la UE, art. 50 (Comisión Europea)](https://digital-strategy.ec.europa.eu/en/faqs/transparency-obligations-under-article-50-ai-act) | 28-09-2026 | Aplicable desde 02-08-2026 |

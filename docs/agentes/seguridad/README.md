# Agente 3 — Seguridad técnica y privacidad

**Base de trabajo:** commit `8019dd1` (verificado: el árbol de trabajo está en `98eb671`, dos commits por delante, pero esos dos commits **solo tocan comandos de voz, ayuda y personalización**; ningún archivo de seguridad cambió entre `8019dd1` y `98eb671`, así que este informe es válido para `8019dd1`).

**Modo:** solo lectura. No se modificó `app/src/main`, ni Gradle, ni módulos de otros equipos. No hay commit ni push.

**Fecha de la revisión:** 28 de septiembre de 2026.

## Índice

- [`informe-seguridad.md`](informe-seguridad.md) — ronda 1 (base `8019dd1`): tabla de hallazgos (formato común) y orden de arreglo.
- [`informe-seguridad-ronda2.md`](informe-seguridad-ronda2.md) — ronda 2 (base `d5ffd5e`): contraseña de borrado por voz, Wikipedia y un hallazgo nuevo (S-20).
- [`modelo-de-amenazas.md`](modelo-de-amenazas.md) — quién atacaría qué y cómo (una página).
- [`parches/S-03-allowBackup.patch`](parches/S-03-allowBackup.patch) — parche **sin aplicar**: `allowBackup` (fuga de datos por copias de seguridad).
- [`parches/S-20-no-log-en-rawinput.patch`](parches/S-20-no-log-en-rawinput.patch) — parche **sin aplicar** (propuesta, módulo de otro equipo): no registrar el habla mientras se dicta una contraseña.

## Alcance de esta ronda

- Confirmación de la tabla de partida (contraseñas, cuentas demo, `allowBackup`, componentes exportados, llamada a la IA, `isMinifyEnabled`).
- Superficies nuevas pedidas: `AccountEraser`, selector de fotos, interruptor de IA, bloqueo por intentos, log de login.
- Búsqueda de secretos en el árbol y en el historial local de git.
- Revisión de dependencias (versiones), sin pruebas contra servidores externos.

## Lo que NO se hizo (y por qué)

- **Servidor de IA (`modulo03/backend-asistente`):** es una rama remota; sin acceso de red desde aquí no se pudo leer. Sus tareas (claves fuera del repo, límite por dispositivo, `X-CECAPI-Token`, no registrar preguntas) quedan **pendientes** y deben revisarse con el código del servidor a la vista.
- **Pruebas dinámicas** (decompilar el APK, `adb backup` real, tráfico de red): no se ejecutaron; los hallazgos son por lectura de código y configuración. Lo que necesita confirmación en dispositivo está marcado como tal y es tarea del Agente 1.
- **CVE de dependencias:** no se afirma ninguna vulnerabilidad concreta sin una fuente con fecha. Ver la nota en el informe.

## Resumen ejecutivo

Nada es **crítico** hoy porque solo existen cuentas de demostración y la IA está apagada y sin conectar. El riesgo real aparece **antes de usar datos reales**. Prioridad de arreglo:

1. **S-03** `allowBackup=true` (Alto) — la base de datos y las fotos privadas salen en copias de seguridad. Arreglo de una línea; parche adjunto.
2. **S-05** La contraseña dictada por voz sale al reconocedor de Google (Alto, privacidad).
3. **S-01** Contraseñas con SHA-256 sin sal (Alto) — antes de cuentas reales.
4. **S-09** "Borra mi cuenta" no es permanente para cuentas demo (Medio).
5. Resto: `isMinifyEnabled`, cifrado en reposo, token/pinning del backend, etc.

Verificado además como **correcto** (sin hallazgo): el interruptor de IA apaga de verdad el envío (S-13), el log de login ya no escribe el usuario (S-14), y el selector de fotos no pide permiso de galería y copia a almacenamiento privado (S-11).

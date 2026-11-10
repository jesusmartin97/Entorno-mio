# Registro de cambios

> Vigente · Dueño: Pp · Una entrada por semana, lo más nuevo arriba, con el formato de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) · Verificado contra `git log` hasta e968668

`main` local va en e968668, sin subir; en GitHub, `main` va en 794ffe5. **e968668 se instaló en el Oppo el 1 oct: compila, migra de la versión 6 a la 7 y abre. Las funciones nuevas no se han probado una por una.**

## Semana del 26 sep al 2 oct de 2026

### Agregado

- Roles con Educador, reglas de quién ve a quién y panel de Gestión (221bbb7, 1b4c042); validación de cuentas en cadena y editar mi cuenta (e968668).
- Pantalla de Ayuda y soporte para alumno y usuario independiente (190ddd0).
- Tabla de clasificación individual (con apodo) y por instituciones (2865f80, eb51aa2).
- Cámara sin cuenta y sesión recordada en el teléfono (d50a1b0); Solicitudes guarda las respuestas (e71400f).
- Guía de encuadre por voz y botones grandes en el lector (95c5145, d016faf); lector de entorno con orientación de la foto, posición y diccionario ampliado (51dde3a, 36b780f).
- IA conectada a la app, apagada por defecto (024dfa8). Backend del Equipo 2 con claves en `.env`, respaldo con Groq y contador diario (6348944, PR #1), y su catálogo de comandos de navegación (794ffe5).
- Wikidata para datos puntuales (36e4acd). **Cualquier frase no entendida va completa a Wikipedia (ecc7b8b); hallazgo D-08, crítico.**
- Botón para elegir motor de voz cuando el teléfono no tiene uno (f51503b, 1a8cd6a).

- Wikipedia como respaldo cuando la IA está apagada: "qué es", "quién es", "busca"... (d5ffd5e). **Sale a internet sin pasar por el interruptor de IA y sin filtro para menores; pendiente de decisión de Pp.**
- Borrar cuenta pide la contraseña antes de confirmar; el inicio de sesión reconoce "olvidé mi contraseña" y sale con "atrás" o "volver"; doble toque en el micrófono para callar al asistente; la foto se ve mientras se procesa (d5ffd5e).
- Comandos para aprender la app sin ayuda ("tutorial", "dónde estoy", "cómo voy", "habla más despacio") y perfiles de voz para niño, normal y persona mayor (992e1fb).

- Privacidad: interruptor de IA, apagado por defecto; borrar mi cuenta y mis datos, con confirmación "sí, borrar" (8019dd1).
- Elegir una foto de la galería en el lector y en Entorno, sin permiso de galería (173fa87).
- Comandos que funcionan sin internet y limpieza de la caché (c17aa22).
- Lista de comandos corta por pantalla, "otras formas de decirlo" y crear cuenta con comandos (f032349).
- Tolerancia a sinónimos y errores de reconocimiento en los comandos (1d856e8).
- Actividades del Equipo 4: 40 ejercicios de audio y un prototipo de ejercicios de vibración (7589c78).
- Pantalla negra, brillo mínimo, Chats y reporte de almacenamiento (d55f29b).

### Cambiado

- Base de datos de la versión 3 a la 7: educador, incidencias, apodo y validación (sin probar en teléfono).
- Todas las contraseñas de prueba pasan a 1234 una vez al abrir la app (ecc7b8b).
- Actividades en tarjetas de pantalla completa (bbf4f21); un solo gesto por acción en el micrófono (7454143).
- Base de datos a la versión 3, con la migración de 2 a 3 (7589c78).
- Contraseña de demostración de JORGE: de "Hola" a 4321.
- Limpieza del Agente 4 (8a046d2). Se restauraron los módulos 3, 5 y 6 porque la limpieza quitaba comentarios de esos equipos.

### Corregido

- Hallazgos de la ronda 2 del Agente 1, incluido uno crítico (1fc3439). Falta que el Agente 1 los verifique.
- Una línea repetida en la lista de comandos de Personalización (98eb671).
- Hallazgos de la primera ronda de pruebas del Agente 1: P-01 a P-08, P-10, P-11 y P-14 a P-20 (d493698). Falta que el Agente 1 los verifique en la ronda 2. P-09, P-12 y P-13 siguen abiertos.

### Seguridad

- Sin copias de seguridad de Android (`allowBackup` en falso); el bloqueo por intentos usa el reloj interno, que no se puede adelantar; la app frena los reintentos cuando no hay red (f5e7fe8).
- El log del inicio de sesión ya no escribe el nombre de usuario (8019dd1).

### Fuera de `main`

- Equipo 3: 9 commits en `rama-miranda-sensor-camara` (interfaz del lector y de la cámara, botón de volver al menú), del 26 al 29 sep. El rediseño del lector y la guía de encuadre ya se trajeron a `main` el 1 oct (d016faf, 95c5145); el resto de la rama salió de un punto viejo.
- Equipo 5: diccionario de más de 140 objetos y `EnvironmentRepository`, entregados por zip el 30 sep e integrados en 51dde3a.
- Equipo 4: 3 commits en `modulo6_aprendizaje` (banco de audio y vibración, 11 audios), del 27 al 30 sep.

### Pendiente, anotado por el constructor (no son hallazgos nuevos)

- La foto capturada no corrige su orientación (EXIF) y, si la imagen nunca carga, el círculo de "cargando" gira para siempre (d5ffd5e).
- P-09, P-12 y P-13 sin decidir; el servidor de IA del Equipo 2 sin revisión de seguridad; las mediciones de peso sin repetir sobre d5ffd5e.

## Semana del 21 al 25 sep de 2026

### Agregado

- Interfaz nueva para personas invidentes: menú, voz global, Personalización, lectura de notificaciones y widget (894a489).
- Módulos 3, 4, 5 y 7 avisan por voz cuando no hay sesión iniciada (a9e1255).
- Respuesta de la IA con "dime más", voz sin markdown y textos largos por partes (520df96).
- Lector de cámara con sensor de oscuridad y poca nitidez, de Miranda y Humberto (4c6d5e1, 7e073e1).

### Cambiado

- main vuelve a AGP 8.5.2 y Gradle 8.9 al fusionar la interfaz nueva con la cámara de Edgar y Miranda (e5e1fed, 26 sep). Esto revierte el commit 14506d0 de Alonso.

# 0004. Base de datos en la versión 3 con migraciones

> Vigente · Dueño: Pp · Verificado el 28 sep 2026 contra integracion/actividades en 8019dd1

## Contexto

Una sola base, `cecapi.db`, guarda las tablas de todos los módulos: 23 tablas declaradas en `core/data/AppDatabase.kt`. El Equipo 4 necesitaba tablas de vibración y sonidos guardados en archivos.

## Decisión

- Versión 3 desde el 27 sep 2026, con dos migraciones en `core/data/Migrations.kt`:
  - **De 1 a 2:** los usuarios reciben rol y origen, y los nombres de usuario pasan a mayúsculas.
  - **De 2 a 3:** borra los ejercicios de comandos viejos y sus resultados, agrega el archivo de sonido y el volumen de cada oído, y crea `ejercicios_vibracion` y `resultados_vibracion`.
- La versión 4 queda reservada para la migración de chats de Alonso.
- Cada tabla de una persona apunta a `usuarios` con borrado en cascada. En eso se apoya "borrar mi cuenta".

## Actualización del 1 oct 2026

La base va en la **versión 7** (main local en e968668). Migraciones nuevas: de 3 a 4, rol Educador y `usuarios.educador_id`; de 4 a 5, tabla `incidencias`; de 5 a 6, `usuarios.apodo`; de 6 a 7, `usuarios.validado`. La versión 4 que estaba reservada para los chats de Alonso ya se usó: su migración tendrá que ser la 8 o posterior.

## Consecuencias

- **Es el riesgo principal del proyecto:** la migración de 2 a 3 no se ha probado en un teléfono que ya tenga la app instalada.
- `exportSchema` está en falso, así que no hay un esquema guardado para probar las migraciones. El Agente 1 pidió activarlo; decide Pp.
- Al migrar se pierden los resultados de los ejercicios viejos. Es a propósito.

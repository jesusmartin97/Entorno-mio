# Cómo colaborar

> Vigente · Dueño: Pp · Revisado el 28 sep 2026 · Por confirmar con Pp: los pasos 4 y 5

1. **Trabaja en tu carpeta.** Cada equipo edita solo `feature/moduloN_.../`. Si necesitas una tabla nueva o algo de `core/`, pídeselo a Pp; no copies ni muevas archivos de otros.
2. **Una rama por tarea**, con el nombre `moduloNN/descripcion`. Ejemplo: `modulo06/actividades-audio`.
3. **No cambies las versiones** de Gradle, AGP, Kotlin ni KSP ([decisión 0001](../decisiones/0001-versiones-congeladas.md)).
4. **Tu correo de git debe ser el de tu cuenta de GitHub**, o tus commits no contarán como tuyos: `git config user.email "tu-correo@ejemplo.com"`.
5. **Abre un PR.** El PR dice qué cambia, cómo se probó (en emulador, en teléfono o sin probar) y si cambia comandos o tablas. **Por confirmar con Pp a qué rama va:** la página de Tareas por equipo dice "hacia feature/interfaz-invidentes, nunca hacia main", pero en esa misma página el Equipo 4 tiene la tarea de abrir su PR hacia main, y el trabajo actual está en `integracion/actividades`.
6. **Si cambias lo que se puede decir**, cambia también `CommandCatalog` o `CommandAlternatives`, y vuelve a generar la [guía de comandos](../referencia/comandos.md) con `python docs/herramientas/generar_comandos.py`.
7. **No subas claves, contraseñas reales ni datos de personas.** Solo se usan las [cuentas de demostración](../referencia/funciones-y-cuentas.md).
8. **No borres los comentarios de otros equipos**, aunque parezcan de más.

## Documentación

- Todo documento empieza con una línea de estado: vigente o no, dueño, y la fecha y el commit contra los que se revisó.
- Cada dato se verifica en el código o con Pp. Lo que no, se escribe "por confirmar".
- La documentación va en `docs/`, nunca dentro de `app/src/main`.

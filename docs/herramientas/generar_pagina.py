"""Arma la página publicada "Documentación CECAPI" a partir de docs/.

La guía de comandos se toma de docs/referencia/comandos.md (que a su vez sale del código);
el resto del contenido está aquí abajo y se revisa contra el código antes de cada publicación.
No cambia ningún archivo del proyecto: escribe el HTML en la ruta que se le pase.

Uso, desde la raíz del repositorio:
    python docs/herramientas/generar_comandos.py
    python docs/herramientas/generar_pagina.py <ruta-de-salida.html>
"""

import html
import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parents[2]
COMANDOS = RAIZ / "docs/referencia/comandos.md"

COMMIT = "20c1e84"
FECHA = "5 de octubre de 2026"

# (página, liga, para quién, estado, clase de estado, nota)
PAGINAS = [
    ("Tareas por equipo", "https://claude.ai/artifact/HBF3rJTytZj34HZ9H1bWon", "Todos los equipos", "Vigente", "ok", "Una pestaña por semana. La de arriba es la del 5 al 11 de octubre."),
    ("Guía Equipo IA", "https://claude.ai/artifact/MFHP3Vihse48rqGQZj24Y9", "Equipo 2", "Con aviso", "warn", "La IA está apagada en la app hasta decidir proveedor y edades. Leer el aviso de arriba antes de avanzar."),
    ("Guía Equipo Cámaras", "https://claude.ai/artifact/1MPBhgeFa36dUnXZCCE7Eg", "Equipos 3 y 5", "Con aviso", "warn", "Hoy la cámara funciona con ML Kit en el teléfono y ya tiene galería. Leer el aviso de arriba."),
    ("Probar CECAPI", "https://claude.ai/artifact/My5wz3xvbvUiZHzaXy2Pai", "Compañeros", "Con aviso", "warn", "Los pasos para abrir el proyecto siguen sirviendo; el menú que describe ya cambió."),
    ("Pantallas CECAPI", "https://claude.ai/artifact/E1j5wh4SmJ14vTNbv4wYGe", "Todos", "Vigente", "ok", "Ocho pantallas del teléfono con los colores y textos reales al 1 de octubre. Se pueden editar botones, campos y colores."),
    ("Simulador", "https://claude.ai/artifact/F1sG8C2wmUip7oG26jQGJo", "Consulta", "Reemplazado", "off", "Muestra la app del 24 de septiembre. Lo reemplaza Pantallas CECAPI."),
    ("Alcance", "https://claude.ai/artifact/VvoXRHZf8pST4g7CKd2EBX", "Maestro", "Vigente (v3)", "ok", "Estado, alcance y ampliaciones por integrante."),
    ("Reporte de avances", "https://claude.ai/artifact/9xB69wgCjSYpXpQdm9gHSt", "Pp", "Vigente", "ok", "Una pestaña por semana; la más nueva primero. Desde el 9 de octubre, con métricas."),
    ("Encargos para agentes de apoyo", "https://claude.ai/artifact/4XvQVvFmzZFaVFqqT9RVeM", "Pp y agentes", "Vigente", "ok", "Reglas comunes y tareas de los cinco agentes."),
    ("Documentación: esquema y formatos", "https://claude.ai/artifact/5cscBPhaH7mRxdJ2dRYcwX", "Pp", "Aprobado", "ok", "Cómo se ordena la documentación y el estado de cada ronda."),
    ("Reporte semanal, 28 sep al 2 oct", "https://claude.ai/artifact/ME9WSrduQrCTGkAQkeWpdA", "Equipos", "Reemplazado", "off", "Lo reemplaza Tareas por equipo. Se guarda como registro."),
    ("Bitácora", "https://claude.ai/artifact/3nQUjLJtYZtb85cNj24q9v", "Maestro", "Desactualizada", "off", "No mostrar hasta que se decida la composición del Equipo 1."),
    ("Planteamiento", "https://claude.ai/artifact/XYfZCZHHZCjuPvQYDKm27f", "Maestro", "Desactualizada", "off", "Igual."),
    ("Cronograma", "https://claude.ai/artifact/1MyXNyG2Qjw6aJgBEigtqg", "Maestro", "Desactualizada", "off", "Igual."),
    ("Arranque", "https://claude.ai/artifact/JaBmouCVwbUUA1dxAHxCL8", "Equipos", "Desactualizada", "off", "Igual."),
    ("Entorno", "https://claude.ai/artifact/CtQkaauTGcYZoiRwvtSbfC", "Equipos", "Desactualizada", "off", "Igual."),
]

FUNCIONES = [
    ("Voz en toda la app", "Di \"hola\" o el nombre del asistente. \"Silencio\" lo calla hasta que digas \"hola\"; \"para\" lo detiene hasta abrir la app otra vez."),
    ("Cámara", "\"Leer texto\" lee un papel, cartel o etiqueta, con guía por voz para encuadrar y botones grandes mientras lee. \"Qué hay enfrente\" describe lo que ve la cámara. \"Elige una foto\" usa una foto que ya tienes. Todo se analiza en el teléfono. Funciona sin cuenta; con cuenta guarda el historial."),
    ("Actividades", "Tarjetas de pantalla completa que se deslizan: Sonidos (de dónde viene, tres niveles), Vibración (corto, largo o mixto) y actividades recreativas. Necesita cuenta."),
    ("Chats", "Escuchar, repasar y borrar las conversaciones con el asistente."),
    ("Personalización", "Voz, velocidad, tono, tú o usted, nombres, y perfiles listos para niño, normal o persona mayor."),
    ("Configuración", "Avisos de notificaciones, escuchar fuera de la app, espacio, caché, modo simple, privacidad (IA), editar mi cuenta y borrar la cuenta."),
    ("Micrófono", "Un toque para hablar, dos toques para callar al asistente, mantener presionado para que explique."),
    ("Aprender sola la app", "\"Tutorial\", \"dónde estoy\", \"lista de comandos\" y \"otras formas de decirlo\", en cualquier pantalla."),
    ("Pantalla negra", "\"Pantalla negra\" o \"brillo mínimo\". Para volver: \"pantalla normal\" o mantener presionada la pantalla."),
    ("Borrar mi cuenta", "En Configuración: \"borra mi cuenta\". Pide la contraseña y luego confirmar. Borra cuenta, chats, resultados, documentos y fotos."),
    ("Roles y Gestión", "Administrador, directivo, educador, alumno y usuario independiente. Gestión muestra a quién puede ver cada rol y las cuentas por validar."),
    ("Ayuda y soporte", "Para alumnos y usuarios: ver quién es su educador y reportar un problema."),
    ("Clasificación", "Tabla por puntos de Actividades, con apodo y nunca el nombre completo, y tabla por instituciones."),
    ("Buscar en Wikipedia", "Responde con Wikipedia y Wikidata lo que la app no entiende, con internet. En revisión: hoy manda la frase completa, aunque la IA esté apagada."),
]

SALE = [
    ("Reconocer la voz", "Puede salir", "warn", "El servicio de voz del teléfono (en el Oppo, Google). Incluye lo que se dicta: usuario y contraseña."),
    ("Buscar en Wikipedia", "Sí", "bad", "Wikipedia y Wikidata reciben cualquier frase que la app no entienda, y la dirección IP. No tiene interruptor. En revisión."),
    ("IA en internet", "No", "ok", "Empieza apagada. Si se enciende, va al servidor del Equipo 2, que hoy solo funciona en la red de quien lo corre."),
    ("Leer texto y describir fotos", "No", "ok", "ML Kit trabaja dentro del teléfono."),
    ("Voz de la app", "Depende", "warn", "Algunas voces del teléfono necesitan internet; Personalización lo indica."),
    ("Notificaciones de otras apps", "No", "ok", "Se quedan en la memoria del teléfono."),
    ("Copias de seguridad de Android", "No", "ok", "Desactivadas desde el 29 de septiembre."),
]

CUENTAS = [("CECAPI", "1234", "Administrador, cuenta compartida"), ("PEPE", "1234", "Administrador"), ("JORGE", "1234", "Directivo"), ("MIRIAM", "1234", "Educador"), ("JUAN", "1234", "Alumno de MIRIAM")]

DECISIONES = [
    ("0001", "Versiones congeladas", "AGP 8.5.2, Gradle 8.9 y Kotlin 2.0.21. No se actualizan sin acuerdo de Pp. Riesgo abierto: Google Play pide targetSdk 36."),
    ("0002", "IA apagada por defecto", "La IA solo traduce frases que la app no entiende. Queda apagada hasta decidir proveedor y edades, porque habrá menores."),
    ("0003", "Pantalla negra con salida", "Siempre se puede volver por voz o manteniendo presionada la pantalla."),
    ("0004", "Base de datos", "Va en la versión 7, con seis migraciones. Ninguna se ha probado en un teléfono que ya tenga la app."),
    ("0005", "Responsable, menores y datos", "Pp es responsable mientras solo haya cuentas de demostración. Habrá menores. Ningún dato real hasta que Pp lo autorice."),
    ("0006", "\"Silencio\" y \"para\"", "Solo cuentan si son la frase completa, porque \"para\" es una palabra común."),
    ("0007", "Escuchar fuera de la app", "Opcional y apagado al principio, con un aviso fijo y botón para detener."),
]

CAMBIOS = [
    ("3 y 4 oct", "Rediseño según el prototipo de Figma, podio en Clasificación, Mi cuenta en dos pestañas, unirse a una institución y validación; Módulo 6 unificado; el asistente entiende frases con /entender."),
    ("1 oct", "Pantallas nuevas: Gestión, Ayuda y soporte, Clasificación y Editar mi cuenta. Actividades en tarjetas deslizables con actividades recreativas. Lector con tarjetas grandes y guía de encuadre (diseño del Equipo 3). Instalado en el teléfono de prueba."),
    ("30 sep", "Roles con Educador; cámara sin cuenta; IA conectada pero apagada; backend del Equipo 2 integrado; diccionario de objetos del Equipo 5; Wikipedia y Wikidata."),
    ("29 sep", "Wikipedia como respaldo, borrar cuenta con contraseña, \"olvidé mi contraseña\", doble toque para callar al asistente, foto visible mientras se lee."),
    ("29 sep", "Sin copias de seguridad de Android; el bloqueo por intentos ya no se puede saltar cambiando la hora."),
    ("28 sep", "Tutorial por voz, \"dónde estoy\", perfiles por edad."),
    ("28 sep", "IA apagada por defecto, borrar mi cuenta y mis datos, log sin nombre de usuario."),
    ("28 sep", "Elegir foto de la galería, comandos sin internet, caché, \"otras formas de decirlo\"."),
    ("27 sep", "Actividades del Equipo 4 y tolerancia a errores de pronunciación."),
    ("26 sep", "Pantalla negra, brillo mínimo, Chats y reporte de espacio."),
]


def e(texto: str) -> str:
    return html.escape(texto, quote=True)


def leer_comandos():
    """Devuelve [(sección, [(subtítulo, [frases])])] desde comandos.md."""
    secciones, actual, sub = [], None, None
    for linea in COMANDOS.read_text(encoding="utf-8").splitlines():
        if linea.startswith("## "):
            actual = (linea[3:].strip(), [])
            secciones.append(actual)
            sub = None
        elif linea.startswith("### ") and actual:
            sub = (linea[4:].strip(), [])
            actual[1].append(sub)
        elif linea.startswith("- ") and actual:
            if sub is None:
                sub = ("", [])
                actual[1].append(sub)
            sub[1].append(linea[2:].strip())
    return secciones


def lista(frases):
    return "<ul>" + "".join(f"<li>{e(f)}</li>" for f in frases) + "</ul>"


def comandos_html():
    partes = []
    for titulo, grupos in leer_comandos():
        partes.append(f"<h3>{e(titulo)}</h3>")
        for sub, frases in grupos:
            if not sub:
                partes.append(lista(frases))
            else:
                partes.append(f"<details><summary>{e(sub)}</summary>{lista(frases)}</details>")
    return "\n".join(partes)


def main(salida: str) -> None:
    filas_paginas = "".join(
        f"<tr><td><a href=\"{e(u)}\" target=\"_blank\" rel=\"noopener\">{e(n)}</a><span class=\"nota\">{e(nota)}</span></td>"
        f"<td>{e(quien)}</td><td><span class=\"chip {c}\">{e(est)}</span></td></tr>"
        for n, u, quien, est, c, nota in PAGINAS
    )
    funciones = "".join(f"<div class=\"fn\"><h3>{e(t)}</h3><p>{e(d)}</p></div>" for t, d in FUNCIONES)
    sale = "".join(f"<tr><td>{e(f)}</td><td><span class=\"chip {c}\">{e(s)}</span></td><td>{e(d)}</td></tr>" for f, s, c, d in SALE)
    cuentas = "".join(f"<tr><td><code>{e(u)}</code></td><td><code>{e(p)}</code></td><td>{e(r)}</td></tr>" for u, p, r in CUENTAS)
    decisiones = "".join(f"<li><span class=\"num\">{e(n)}</span><div><strong>{e(t)}</strong><p>{e(d)}</p></div></li>" for n, t, d in DECISIONES)
    cambios = "".join(f"<tr><td class=\"fecha\">{e(f)}</td><td>{e(d)}</td></tr>" for f, d in CAMBIOS)

    pagina = PLANTILLA.format(
        commit=COMMIT, fecha=FECHA, paginas=filas_paginas, funciones=funciones, sale=sale,
        cuentas=cuentas, decisiones=decisiones, cambios=cambios, comandos=comandos_html(),
    )
    pathlib.Path(salida).write_text(pagina, encoding="utf-8")
    print(f"Escrito {salida}")


PLANTILLA = """<title>Documentación CECAPI</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible+Next:wght@400;600;800&family=Atkinson+Hyperlegible+Mono:wght@400;600&display=swap">
<style>
:root {{
  --bg: #F3F5F9; --panel: #FFFFFF; --ink: #121A2E; --soft: #4A5570; --line: #D6DCE8;
  --accent: #0A5F86; --accent-soft: #DCEFF8;
  --ok: #17693A; --ok-soft: #DDF2E4; --warn: #8A4F00; --warn-soft: #FBEBCC; --bad: #A5271C; --bad-soft: #FBE0DC;
  --off: #5B6478; --off-soft: #E7EAF0;
  --sans: "Atkinson Hyperlegible Next", "Atkinson Hyperlegible", system-ui, "Segoe UI", sans-serif;
  --mono: "Atkinson Hyperlegible Mono", ui-monospace, Consolas, monospace;
}}
@media (prefers-color-scheme: dark) {{
  :root:not([data-theme="light"]) {{
    color-scheme: dark;
    --bg: #0C1220; --panel: #141C2E; --ink: #EEF2FA; --soft: #A7B1C8; --line: #28324A;
    --accent: #6CC6F0; --accent-soft: #10304A;
    --ok: #6FD597; --ok-soft: #0F2E1C; --warn: #F2B64F; --warn-soft: #33250A; --bad: #FF8F82; --bad-soft: #3A1612;
    --off: #9AA3B8; --off-soft: #1D2638;
  }}
}}
:root[data-theme="dark"] {{
  color-scheme: dark;
  --bg: #0C1220; --panel: #141C2E; --ink: #EEF2FA; --soft: #A7B1C8; --line: #28324A;
  --accent: #6CC6F0; --accent-soft: #10304A;
  --ok: #6FD597; --ok-soft: #0F2E1C; --warn: #F2B64F; --warn-soft: #33250A; --bad: #FF8F82; --bad-soft: #3A1612;
  --off: #9AA3B8; --off-soft: #1D2638;
}}
* {{ box-sizing: border-box; }}
body {{ background: var(--bg); color: var(--ink); font-family: var(--sans); font-size: 17px; line-height: 1.55; }}
.wrap {{ max-width: 880px; margin: 0 auto; padding-inline: 16px; padding-block: 32px 64px; display: grid; gap: 40px; }}
h1, h2, h3 {{ margin: 0; text-wrap: balance; line-height: 1.2; }}
h1 {{ font-size: clamp(2rem, 6vw, 2.8rem); font-weight: 800; letter-spacing: -0.01em; }}
h2 {{ font-size: 1.45rem; font-weight: 800; }}
h3 {{ font-size: 1.05rem; font-weight: 600; }}
p {{ margin: 0; }}
a {{ color: var(--accent); text-underline-offset: 3px; }}
a:focus-visible, summary:focus-visible {{ outline: 3px solid var(--accent); outline-offset: 2px; border-radius: 4px; }}
code {{ font-family: var(--mono); font-size: 0.92em; background: var(--accent-soft); padding: 1px 6px; border-radius: 5px; }}
header {{ display: grid; gap: 14px; }}
.lede {{ color: var(--soft); max-width: 62ch; font-size: 1.1rem; }}
.meta {{ display: flex; flex-wrap: wrap; gap: 8px; }}
.meta span {{ font-family: var(--mono); font-size: 0.85rem; padding: 4px 10px; border-radius: 999px; background: var(--panel); border: 1px solid var(--line); color: var(--soft); }}
nav {{ display: flex; flex-wrap: wrap; gap: 8px; }}
nav a {{ text-decoration: none; padding: 8px 14px; border-radius: 999px; background: var(--accent-soft); color: var(--accent); font-weight: 600; font-size: 0.95rem; }}
section {{ display: grid; gap: 14px; scroll-margin-top: 16px; }}
.intro {{ color: var(--soft); max-width: 65ch; }}
.tabla {{ overflow-x: auto; background: var(--panel); border: 1px solid var(--line); border-radius: 12px; }}
table {{ border-collapse: collapse; width: 100%; min-width: 560px; }}
th, td {{ text-align: left; vertical-align: top; padding: 12px 14px; border-bottom: 1px solid var(--line); }}
th {{ font-size: 0.78rem; letter-spacing: 0.08em; text-transform: uppercase; color: var(--soft); font-weight: 600; }}
tr:last-child td {{ border-bottom: 0; }}
td a {{ font-weight: 600; }}
.nota {{ display: block; color: var(--soft); font-size: 0.92rem; margin-top: 2px; }}
.fecha {{ font-family: var(--mono); white-space: nowrap; color: var(--soft); font-variant-numeric: tabular-nums; }}
.chip {{ display: inline-block; white-space: nowrap; font-size: 0.82rem; font-weight: 600; padding: 3px 10px; border-radius: 999px; }}
.chip.ok {{ background: var(--ok-soft); color: var(--ok); }}
.chip.warn {{ background: var(--warn-soft); color: var(--warn); }}
.chip.bad {{ background: var(--bad-soft); color: var(--bad); }}
.chip.off {{ background: var(--off-soft); color: var(--off); }}
.grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }}
.fn {{ background: var(--panel); border: 1px solid var(--line); border-radius: 12px; padding: 14px 16px; display: grid; gap: 4px; align-content: start; }}
.fn p {{ color: var(--soft); font-size: 0.97rem; }}
ol.dec {{ list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }}
ol.dec li {{ display: grid; grid-template-columns: auto 1fr; gap: 14px; align-items: start; background: var(--panel); border: 1px solid var(--line); border-radius: 12px; padding: 12px 16px; }}
ol.dec .num {{ font-family: var(--mono); font-weight: 600; color: var(--accent); padding-top: 1px; }}
ol.dec p {{ color: var(--soft); font-size: 0.97rem; }}
.voz {{ display: grid; gap: 10px; }}
.voz h3 {{ margin-top: 10px; }}
.voz ul {{ margin: 0; padding-left: 1.2em; display: grid; gap: 4px; }}
details {{ background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 10px 14px; }}
details[open] {{ padding-bottom: 14px; }}
summary {{ cursor: pointer; font-weight: 600; }}
details ul {{ margin-top: 10px !important; }}
.aviso {{ background: var(--warn-soft); color: var(--ink); border: 1px solid var(--warn); border-radius: 12px; padding: 14px 16px; }}
.aviso strong {{ color: var(--warn); }}
footer {{ color: var(--soft); font-size: 0.92rem; border-top: 1px solid var(--line); padding-top: 16px; }}
@media (prefers-reduced-motion: reduce) {{ * {{ scroll-behavior: auto !important; }} }}
</style>

<div class="wrap">
  <header>
    <h1>Documentación CECAPI</h1>
    <p class="lede">Todo lo del proyecto en un solo lugar: las páginas para cada equipo, qué hace hoy la app, qué se puede decirle y qué datos salen del teléfono.</p>
    <div class="meta"><span>Revisado el {fecha}</span><span>Código: main local en {commit}, sin subir</span><span>Sin datos reales, solo cuentas de demostración</span></div>
    <nav aria-label="Secciones">
      <a href="#paginas">Páginas</a><a href="#app">Qué hace la app</a><a href="#datos">Qué sale del teléfono</a><a href="#comandos">Comandos de voz</a><a href="#decisiones">Decisiones</a><a href="#cambios">Cambios</a>
    </nav>
  </header>

  <section id="paginas">
    <h2>Páginas del proyecto</h2>
    <p class="intro">Abre la que te toca según tu equipo. Las marcadas con aviso tienen una nota arriba que hay que leer primero; las grises son de consulta y no se actualizan.</p>
    <div class="tabla"><table>
      <thead><tr><th>Página</th><th>Para quién</th><th>Estado</th></tr></thead>
      <tbody>{paginas}</tbody>
    </table></div>
  </section>

  <section id="app">
    <h2>Qué hace la app hoy</h2>
    <p class="intro">La versión del 1 de octubre se instaló en el teléfono de prueba: compila y abre. Las pantallas nuevas todavía no se prueban una por una, así que algo puede no funcionar igual que aquí se describe.</p>
    <div class="grid">{funciones}</div>
    <h3>Cuentas de demostración</h3>
    <div class="tabla"><table>
      <thead><tr><th>Usuario</th><th>Contraseña</th><th>Rol</th></tr></thead>
      <tbody>{cuentas}</tbody>
    </table></div>
    <p class="intro">El usuario no distingue mayúsculas. Tras 10 intentos fallidos, el inicio de sesión se bloquea 15 minutos. No uses cuentas ni datos reales de nadie.</p>
  </section>

  <section id="datos">
    <h2>Qué sale del teléfono</h2>
    <p class="intro">Revisado leyendo cada conexión a internet del código. No es un aviso de privacidad: ese texto lo prepara el equipo de normativa y lo revisará una persona abogada.</p>
    <div class="tabla"><table>
      <thead><tr><th>Función</th><th>¿Sale?</th><th>A dónde y qué</th></tr></thead>
      <tbody>{sale}</tbody>
    </table></div>
    <p class="aviso"><strong>En revisión.</strong> La app dice hoy en Configuración que "por defecto, nada" sale del teléfono. No es exacto por Wikipedia y por el reconocedor de voz. Ya está reportado para corregirlo.</p>
  </section>

  <section id="comandos">
    <h2>Comandos de voz</h2>
    <p class="intro">Es lo mismo que la app dice cuando alguien pide "lista de comandos" u "otras formas de decirlo". Sale directo del código. Abre una pantalla para ver sus comandos.</p>
    <div class="voz">{comandos}</div>
  </section>

  <section id="decisiones">
    <h2>Decisiones tomadas</h2>
    <p class="intro">Para no volver a discutirlas. Si alguna cambia, se escribe una nueva que la reemplaza.</p>
    <ol class="dec">{decisiones}</ol>
  </section>

  <section id="cambios">
    <h2>Cambios recientes</h2>
    <div class="tabla"><table>
      <thead><tr><th>Fecha</th><th>Qué cambió</th></tr></thead>
      <tbody>{cambios}</tbody>
    </table></div>
  </section>

  <footer>Esta página la mantiene el agente de documentación. En el repositorio, la misma información está en la carpeta <code>docs/</code>, en la rama <code>docs/agentes-y-documentacion</code>.</footer>
</div>
"""

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "documentacion-cecapi.html")

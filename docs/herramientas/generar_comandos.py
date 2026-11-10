"""Genera docs/referencia/comandos.md a partir del código de la app.

Lee los textos que la app dice en voz alta (CommandCatalog.kt y CommandAlternatives.kt),
así la guía nunca dice algo distinto de lo que hace la app. No cambia ningún archivo del código.

Uso, desde la raíz del repositorio:
    python docs/herramientas/generar_comandos.py
"""

import datetime
import pathlib
import re
import subprocess

RAIZ = pathlib.Path(__file__).resolve().parents[2]
CATALOGO = RAIZ / "app/src/main/java/com/cecapi/app/feature/modulo1_aplicacionprincipal/CommandCatalog.kt"
ALTERNATIVAS = RAIZ / "app/src/main/java/com/cecapi/app/core/voice/CommandAlternatives.kt"
SALIDA = RAIZ / "docs/referencia/comandos.md"

# Nombre de cada lista del catálogo, en el orden en que la persona recorre la app.
PANTALLAS = [
    ("HOME", "Inicio, antes de iniciar sesión"),
    ("LOGIN", "Iniciar sesión"),
    ("REGISTER", "Crear cuenta"),
    ("DASHBOARD", "Menú principal"),
    ("CAMERA", "Cámara"),
    ("READER", "Lector de texto"),
    ("ENVIRONMENT", "Describir lo que hay enfrente"),
    ("DOCUMENTS", "Documentos"),
    ("ACTIVITIES", "Actividades"),
    ("CHATS", "Chats"),
    ("PERSONALIZATION", "Personalización"),
    ("SETTINGS", "Configuración"),
    ("GESTION", "Gestión (administrador, directivo y educador)"),
]

# Marca interna: la lista termina con la frase MORE del catálogo.
FIN = "<<MORE>>"

LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')


def literales(bloque: str) -> str:
    return "".join(LITERAL.findall(bloque)).replace('\\"', '"')


def constantes(codigo: str) -> dict:
    """Cada `const val NOMBRE = "..." + "..."` del archivo, ya unido en un solo texto."""
    patron = re.compile(r"const val (\w+)\s*=(.*?)(?=\n\s*(?:private |const val|val |fun |class |\}))", re.S)
    valores = {}
    for nombre, bloque in patron.findall(codigo):
        texto = literales(bloque)
        if re.search(r"\+\s*MORE\b", bloque):
            texto += FIN
        valores[nombre] = texto
    return valores


def frases(texto: str) -> list:
    partes = re.split(r"(?<=[.?!])\s+", texto.strip())
    return [p for p in partes if p]


def commit() -> str:
    try:
        rama = subprocess.run(["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=RAIZ, capture_output=True, text=True).stdout.strip()
        hash_ = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=RAIZ, capture_output=True, text=True).stdout.strip()
        return f"{rama} en {hash_}"
    except OSError:
        return "commit desconocido"


def main() -> None:
    catalogo = constantes(CATALOGO.read_text(encoding="utf-8"))
    mas = catalogo.pop("MORE", "")
    codigo_alt = ALTERNATIVAS.read_text(encoding="utf-8")
    general = constantes(codigo_alt)["GENERAL_LIST"]
    areas = re.findall(r'spokenName = "([^"]+)".*?text = (.*?)\n\s*\),', codigo_alt, re.S)

    hoy = datetime.date.today().isoformat()
    lineas = [
        "# Comandos de voz de CECAPI",
        "",
        f"> Vigente · Generado desde el código el {hoy}, {commit()} · No se edita a mano: se vuelve a generar con `python docs/herramientas/generar_comandos.py`",
        "",
        "Esta guía repite lo que la app dice cuando alguien pide **lista de comandos** en cada pantalla, y lo que dice con **otras formas de decirlo**. "
        "La app también acepta errores pequeños del reconocimiento de voz, como una letra de más o una palabra partida en dos.",
        "",
        "## Lo que sirve en cualquier pantalla",
        "",
    ]
    lineas += [f"- {f}" for f in frases(general)]
    sin_cierre = [t for c, t in PANTALLAS if c in catalogo and not catalogo[c].endswith(FIN)]
    lineas += ["", "## Lo que se puede decir en cada pantalla", ""]
    lineas += [f"Cada lista termina con: *{mas.strip()}*" + (f" Excepto: {', '.join(sin_cierre)}." if sin_cierre else ""), ""]
    for clave, titulo in PANTALLAS:
        texto = catalogo.get(clave)
        if texto is None:
            lineas += [f"### {titulo}", "", "Por confirmar: esta lista ya no está en el código.", ""]
            continue
        lineas += [f"### {titulo}", ""]
        lineas += [f"- {f}" for f in frases(texto.replace(FIN, ""))]
        lineas.append("")
    faltan = sorted(set(catalogo) - {c for c, _ in PANTALLAS})
    if faltan:
        lineas += ["Listas del código que esta guía todavía no nombra: " + ", ".join(faltan) + ".", ""]

    lineas += [
        "## Otras formas de decirlo",
        "",
        "Se pide diciendo **otras formas de decirlo** y el nombre del área, o **todas**. Cada línea es un comando y las maneras de decirlo.",
        "",
    ]
    for nombre, bloque in areas:
        oraciones = frases(literales(bloque))
        lineas += [f"### {nombre[0].upper() + nombre[1:]}", ""]
        lineas += [f"- {f}" for f in oraciones[1:]]  # la primera solo repite el nombre del área
        lineas.append("")

    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text("\n".join(lineas).rstrip() + "\n", encoding="utf-8")
    print(f"Escrito {SALIDA.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()

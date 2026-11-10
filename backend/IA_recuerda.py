"""
Versión de Owen del backend CECAPI (Módulo 3): main.py + la ruta /entender.

Es una copia de main.py con todo en un solo archivo, para probarla sin tocar
main.py. Arranca: uvicorn IA_recuerda:app --host 0.0.0.0 --port 8000
"""

import logging
import re
import unicodedata

from dotenv import load_dotenv

# Carga el .env ANTES de importar proveedores, que leen las claves al crearse.
load_dotenv()

from fastapi import FastAPI, HTTPException  # noqa: E402
from pydantic import BaseModel, Field  # noqa: E402

import proveedores  # noqa: E402
import uso  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("cecapi")

if not proveedores.configurados():
    raise SystemExit(
        "No hay ninguna clave de IA. Crea backend/.env con GEMINI_API_KEY y/o GROQ_API_KEY "
        "(copia .env.example)."
    )
log.info("Proveedores activos, en orden: %s", [p.nombre for p in proveedores.configurados()])

app = FastAPI(title="CECAPI backend")


# --- Comandos de navegación por voz ---
# Copiado de core/model/ModuloCecapi.kt (storageCode de cada módulo). Si ese
# archivo cambia del lado de la app, hay que actualizar esto también.
MODULOS = {
    "VOICE_ASSISTANT": ["asistente de voz"],
    "AI_ASSISTANT": ["asistente inteligente"],
    "DOCUMENT_READER": ["camara inteligente", "camara", "leer documentos", "abre la camara", "abrir camara"],
    "REQUESTS": ["documentos", "solicitudes"],
    "LEARNING": ["actividades", "centro de aprendizaje"],
    "ENVIRONMENT": ["asistente del entorno", "entorno", "que hay enfrente"],
}


def _normalizar(texto: str) -> str:
    """Minúsculas, sin acentos ni signos: "¿Abre la Cámara?" -> "abre la camara"."""
    sin_acentos = unicodedata.normalize("NFD", texto.lower())
    sin_acentos = "".join(c for c in sin_acentos if unicodedata.category(c) != "Mn")
    return " ".join(re.sub(r"[^a-z0-9 ]", " ", sin_acentos).split())


def detectar_comando(texto: str) -> str | None:
    """Regresa el storageCode del módulo si el texto lo menciona, si no None."""
    # Espacios alrededor para comparar palabras completas ("entornos" no es "entorno").
    texto = f" {_normalizar(texto)} "
    for codigo, variantes in MODULOS.items():
        if any(f" {variante} " in texto for variante in variantes):
            return codigo
    return None


# --- Rutas ---
class Pregunta(BaseModel):
    pregunta: str = Field(min_length=1, max_length=2000)
    contexto: list[str] = []


@app.get("/")
def raiz():
    return {"status": "CECAPI backend activo", "proveedores": [p.nombre for p in proveedores.configurados()]}


@app.get("/uso")
def ver_uso():
    """Peticiones de hoy por proveedor; `aviso` se vuelve true al pasar el 80% del límite."""
    return uso.resumen([p.nombre for p in proveedores.PROVEEDORES])


def _preguntar_a_la_ia(body: Pregunta) -> dict:
    try:
        respuesta, proveedor = proveedores.preguntar_con_respaldo(body.pregunta, body.contexto[-6:])
    except proveedores.ProveedorNoDisponible:
        # Mensaje genérico hacia la app; el detalle queda solo en el log del servidor.
        raise HTTPException(status_code=503, detail="El asistente no está disponible en este momento.")
    return {"respuesta": respuesta, "proveedor": proveedor}


# `def` y no `async def`: los SDK son bloqueantes y así FastAPI los corre en otro hilo
# sin congelar al servidor mientras espera a la IA.
@app.post("/preguntar")
def responder(body: Pregunta):
    return _preguntar_a_la_ia(body)


@app.post("/entender")
def entender(body: Pregunta):
    """
    Decide si lo que dijo la persona es un comando conocido de la app
    (ej. "abre la cámara") o una pregunta libre.

    Primero intenta el match de comando (rápido, gratis, sin llamar a ninguna
    IA). Si no reconoce ningún comando, cae al mismo flujo de /preguntar
    (mismo fallback Gemini → Groq, mismas respuestas cortas).
    """
    comando = detectar_comando(body.pregunta)
    if comando:
        return {"tipo": "comando", "comando": comando}
    return {"tipo": "respuesta", **_preguntar_a_la_ia(body)}

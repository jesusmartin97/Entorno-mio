"""Backend del asistente inteligente de CECAPI (Módulo 3). Ver README.md."""

import logging

from dotenv import load_dotenv

# Carga el .env ANTES de importar proveedores, que leen las claves al crearse.
load_dotenv()

from fastapi import FastAPI, HTTPException  # noqa: E402
from pydantic import BaseModel, Field  # noqa: E402

import comandos
import privacidad
import proveedores  # noqa: E402
import uso  # noqa: E402
import base64
import binascii

import imagenes  # Owen — rama modulo03/imagenes, mergear antes de probar esto
from prompts_vision import PROMPT_BILLETE

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("cecapi")

if not proveedores.configurados():
    raise SystemExit(
        "No hay ninguna clave de IA. Crea backend/.env con GEMINI_API_KEY y/o GROQ_API_KEY "
        "(copia .env.example)."
    )
log.info("Proveedores activos, en orden: %s", [p.nombre for p in proveedores.configurados()])

app = FastAPI(title="CECAPI backend")


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


# `def` y no `async def`: los SDK son bloqueantes y así FastAPI los corre en otro hilo
# sin congelar al servidor mientras espera a la IA.
@app.post("/preguntar")
def responder(body: Pregunta):
    if privacidad.contiene_dato_sensible(body.pregunta):
        return {"respuesta": privacidad.MENSAJE_BLOQUEO, "proveedor": None, "hayMas": False}
    try:
        respuesta, proveedor, hay_mas = proveedores.preguntar_con_respaldo(body.pregunta, body.contexto[-6:])
    except proveedores.ProveedorNoDisponible:
        # Mensaje genérico hacia la app; el detalle queda solo en el log del servidor.
        raise HTTPException(status_code=503, detail="El asistente no está disponible en este momento.")
    return {"respuesta": respuesta, "proveedor": proveedor, "hayMas": hay_mas}

@app.post("/entender")
def entender(body: Pregunta):
    """
    Decide si lo que dijo la persona es un comando conocido de la app
    (ej. "abre la cámara") o una pregunta libre.

    Primero intenta el match de comando (rápido, gratis, sin llamar a ninguna
    IA). Si no reconoce ningún comando, cae al mismo flujo de /preguntar
    (mismo fallback Gemini → Groq, mismas respuestas cortas).
    """
    comando = comandos.detectar(body.pregunta)
    if comando:
        return {"tipo": "comando", "comando": comando}

    if privacidad.contiene_dato_sensible(body.pregunta):
        return {"tipo": "respuesta", "respuesta": privacidad.MENSAJE_BLOQUEO, "proveedor": None, "hayMas": False}

    try:
        respuesta, proveedor, hay_mas = proveedores.preguntar_con_respaldo(body.pregunta, body.contexto[-6:])
    except proveedores.ProveedorNoDisponible:
        raise HTTPException(status_code=503, detail="El asistente no está disponible en este momento.")
    return {"tipo": "respuesta", "respuesta": respuesta, "proveedor": proveedor, "hayMas": hay_mas}

class Imagen(BaseModel):
    imagen: str = Field(min_length=1)  # foto en base64, sin prefijo data:...
    modo: str = Field(min_length=1)    # "billete" por ahora; "escena" responde pendiente


@app.post("/ver")
def ver(body: Imagen):
    if body.modo == "escena":
        raise HTTPException(status_code=400, detail="El modo escena todavía no está disponible.")
    if body.modo != "billete":
        raise HTTPException(status_code=400, detail="Modo no reconocido.")

    try:
        datos = base64.b64decode(body.imagen, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="La imagen no llegó en un formato válido.")

    try:
        foto, tipo_mime = imagenes.preparar(datos)
    except imagenes.ImagenInvalida:
        raise HTTPException(status_code=400, detail="La foto no es válida o es demasiado grande.")

    try:
        respuesta, proveedor = proveedores.ver_con_respaldo(foto, tipo_mime, PROMPT_BILLETE)
    except proveedores.ProveedorNoDisponible:
        raise HTTPException(status_code=503, detail="El asistente no está disponible en este momento.")

    return {"respuesta": respuesta, "proveedor": proveedor}
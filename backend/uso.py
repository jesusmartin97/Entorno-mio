"""
Contador de peticiones por día y por proveedor (Módulo 3).

- Guarda los conteos en uso_diario.json (junto a este archivo) para que no se
  pierdan si se reinicia el servidor. Ese archivo no se sube al repo.
- Al llegar al 80% del límite diario de un proveedor escribe un AVISO en el log
  (una sola vez por día y proveedor) y lo marca en GET /uso.
- Al llegar al 100% ese proveedor se salta y se usa directamente el siguiente.

Los límites se configuran en .env (GEMINI_LIMITE_DIARIO, GROQ_LIMITE_DIARIO).
El día es el de la computadora donde corre el backend.
"""

import json
import logging
import os
import threading
from datetime import date
from pathlib import Path

log = logging.getLogger("cecapi.uso")

ARCHIVO = Path(__file__).with_name("uso_diario.json")
UMBRAL_AVISO = 0.8

_candado = threading.Lock()


def limite(proveedor: str) -> int:
    """Límite diario del proveedor según .env; 0 significa 'sin límite conocido'."""
    try:
        return int(os.getenv(f"{proveedor.upper()}_LIMITE_DIARIO", "0"))
    except ValueError:
        return 0


def _leer() -> dict:
    hoy = date.today().isoformat()
    try:
        datos = json.loads(ARCHIVO.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        datos = {}
    if datos.get("fecha") != hoy:  # día nuevo: los contadores vuelven a cero
        datos = {"fecha": hoy, "conteo": {}, "avisados": []}
    return datos


def _guardar(datos: dict) -> None:
    temporal = ARCHIVO.with_suffix(".tmp")
    temporal.write_text(json.dumps(datos, indent=2), encoding="utf-8")
    temporal.replace(ARCHIVO)


def agotado(proveedor: str) -> bool:
    tope = limite(proveedor)
    if tope <= 0:
        return False
    with _candado:
        return _leer()["conteo"].get(proveedor, 0) >= tope


def registrar(proveedor: str) -> None:
    """Suma una petición al proveedor (cuenta aunque falle: la API también la cuenta)."""
    with _candado:
        datos = _leer()
        usadas = datos["conteo"].get(proveedor, 0) + 1
        datos["conteo"][proveedor] = usadas
        tope = limite(proveedor)
        if tope > 0 and usadas >= tope * UMBRAL_AVISO and proveedor not in datos["avisados"]:
            datos["avisados"].append(proveedor)
            log.warning(
                "AVISO: %s lleva %d de %d peticiones hoy (%d%%). Al llegar al 100%% se usará el respaldo.",
                proveedor, usadas, tope, round(usadas * 100 / tope),
            )
        _guardar(datos)


def resumen(proveedores: list[str]) -> dict:
    with _candado:
        datos = _leer()
    salida = {"fecha": datos["fecha"], "proveedores": {}}
    for nombre in proveedores:
        usadas = datos["conteo"].get(nombre, 0)
        tope = limite(nombre)
        porcentaje = round(usadas * 100 / tope) if tope > 0 else None
        salida["proveedores"][nombre] = {
            "usadas": usadas,
            "limite": tope or None,
            "porcentaje": porcentaje,
            "aviso": porcentaje is not None and porcentaje >= UMBRAL_AVISO * 100,
        }
    return salida

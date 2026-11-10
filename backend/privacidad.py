"""
Revisa el texto del usuario ANTES de mandarlo a cualquier proveedor de IA,
para no enviar contraseñas ni datos personales obvios.

Es un filtro simple por patrones, no perfecto — pero evita los casos más
claros sin tener que mandar nada a una IA externa para decidir.
"""

import re

_PATRONES = [
    re.compile(r"contrase[ñn]a", re.IGNORECASE),
    re.compile(r"\bpassword\b", re.IGNORECASE),
    re.compile(r"\bpin\b", re.IGNORECASE),
    re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+"),           # correo electrónico
    re.compile(r"\b\d{10}\b"),                          # teléfono (10 dígitos)
    re.compile(r"\b(?:\d[ -]?){13,16}\b"),               # tarjeta (13-16 dígitos)
    re.compile(r"\bcurp\b", re.IGNORECASE),
    re.compile(r"\brfc\b", re.IGNORECASE),
]

MENSAJE_BLOQUEO = (
    "Por tu seguridad, no puedo procesar mensajes con contraseñas, números de "
    "tarjeta o datos personales. ¿Puedes repetirlo sin esa información?"
)


def contiene_dato_sensible(texto: str) -> bool:
    return any(p.search(texto) for p in _PATRONES)
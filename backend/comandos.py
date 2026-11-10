"""
Catalogo de comandos de navegacion por voz (Modulo 3).

Esta lista esta copiada de core/model/ModuloCecapi.kt, que es la fuente real
de los modulos de la app (storageCode + title de cada uno). Si ese archivo
cambia del lado de la app, hay que actualizar esto tambien.
"""

# storageCode de ModuloCecapi -> frases que, si aparecen en lo que dijo
# la persona, significan "abre este modulo".
MODULOS = {
    "VOICE_ASSISTANT": ["asistente de voz"],
    "AI_ASSISTANT": ["asistente inteligente"],
    "DOCUMENT_READER": ["camara inteligente", "camara", "leer documentos", "abre la camara", "abrir camara"],
    "REQUESTS": ["documentos", "solicitudes"],
    "LEARNING": ["actividades", "centro de aprendizaje"],
    "ENVIRONMENT": ["asistente del entorno", "entorno", "que hay enfrente"],
}


def detectar(texto: str) -> str | None:
    """Regresa el storageCode del modulo si el texto lo menciona, si no None."""
    texto = texto.lower()
    for codigo, variantes in MODULOS.items():
        if any(variante in texto for variante in variantes):
            return codigo
    return None
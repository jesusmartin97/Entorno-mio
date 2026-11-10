"""
Prompts para el reconocimiento de imágenes (Módulo 3 · Fase 1, visión).

Se usan con proveedores.ver_con_respaldo(imagen, tipo_mime, prompt).
La persona ESCUCHA la respuesta, igual que con /preguntar: frases cortas,
sin markdown, español de México.
"""

PROMPT_BILLETE = (
    "Estás ayudando a una persona con discapacidad visual a identificar un "
    "billete mexicano a partir de una foto. "
    "Responde con EXACTAMENTE una de estas frases, sin agregar nada más:\n"
    '"Billete de 20 pesos"\n'
    '"Billete de 50 pesos"\n'
    '"Billete de 100 pesos"\n'
    '"Billete de 200 pesos"\n'
    '"Billete de 500 pesos"\n'
    '"Billete de 1000 pesos"\n'
    '"No se ve claro, acerca el billete o busca más luz"\n'
    '"No parece un billete"\n'
    "La foto puede mostrar el anverso o el reverso, y diseños viejos o nuevos "
    "de billetes mexicanos. "
    "Nunca adivines: si no estás seguro de la denominación exacta, responde "
    'que no se ve claro. Decir "200" cuando en realidad es un billete de 20 '
    "es un error grave, peor que admitir que no se ve bien. "
    "No uses markdown ni emojis."
)

# Fase 1: se escribe ahora. NO se integra todavía a /ver (ver guía, alcance
# de esta semana: solo el modo billete queda completo).
PROMPT_ESCENA = (
    "Estás ayudando a una persona con discapacidad visual a entender qué hay "
    "frente a ella a partir de una foto. "
    "Describe como máximo 3 objetos, cada uno con su posición aproximada: "
    "izquierda, centro o derecha. "
    "Responde en una o dos frases cortas, pensadas para escucharse en voz "
    "alta, no para leerse. "
    "Si la foto no muestra nada identificable con claridad, dilo así, sin "
    "inventar objetos. "
    "No uses markdown ni emojis."
)
"""
Calibra con fotos reales los porcentajes (umbrales) que usa MonedaMx.kt para decir "es una moneda de ...".

Hace lo mismo que la app con cada foto: la prepara en 3 variantes (completa, espejo y recorte central cuadrado),
las pasa por monedas.tflite, promedia, y prueba muchas combinaciones de umbrales. Imprime los valores listos
para pegar en MonedaMx.kt y las fotos donde el modelo se equivoca.

QUÉ FOTOS USAR (muy importante): fotos NUEVAS, que NO se usaron para entrenar. Con las de entrenamiento todo sale
mejor de lo que será en la vida real y los umbrales quedan demasiado flojos.

  prueba/1peso/  prueba/2pesos/  prueba/5pesos/  prueba/10pesos/  prueba/20pesos/    (mismas carpetas que al entrenar)
  prueba/negativas/   fotos SIN ninguna moneda: cuarto, mesa vacía, mano, laptop, billetes, objetos varios.
                      Sirven para fijar el umbral de las fotos normales ("qué hay enfrente"): el modelo solo conoce
                      monedas y siempre elige alguna, así que sin estas fotos no se puede saber cuántas veces
                      interrumpiría la descripción con una moneda que no existe. Con 50 o más sale mejor.

EN GOOGLE COLAB:
  1. Sube monedas.tflite, monedas_labels.txt, este archivo y la carpeta prueba/ (en zip: !unzip -q prueba.zip).
  2. !python calibrar_umbrales.py --modelo monedas.tflite --etiquetas monedas_labels.txt --fotos prueba
  3. Copia los valores que imprime al final en MonedaMx.kt (sección "Umbrales") y vuelve a compilar.

NOTA: la parte de umbrales está probada con datos simulados; la parte que corre el modelo no se ha ejecutado
todavía. Una diferencia pequeña: la app reduce las fotos con Android (Bitmap bilineal) y aquí se usa Pillow, así que
las probabilidades pueden diferir en el segundo decimal. Si algo falla, copia el mensaje de error.
"""
import argparse
import os
import re

import numpy as np

LADO = 224
LADO_MAXIMO = 1280  # la app reduce la foto a este lado máximo antes de clasificarla
PISO_SEGURA = 0.70  # nunca se recomienda decir una moneda como hecho con menos de esto, aunque las fotos lo permitan
VARIANTES = ("completa", "espejo", "centro")
ESPECIALES = ("sin_moneda", "varias")
EXTENSIONES = (".jpg", ".jpeg", ".png", ".webp", ".bmp")
FORMATO_ETIQUETA = re.compile(r"^\$?(\d+)[ _]?(pesos?)?$")

# Valores provisionales que hoy trae MonedaMx.kt (para comparar con los calibrados).
ACTUALES = dict(
    umbral_segura=0.85, acuerdo_minimo=2,
    umbral_auto=0.95, umbral_variante_auto=0.70,
    umbral_dudosa=0.60,
)


# ---------------------------------------------------------------- decisión (copia exacta de MonedaMx.decidir)
def valor_moneda(etiqueta):
    m = FORMATO_ETIQUETA.match(etiqueta.strip().lower())
    return int(m.group(1)) if m else None


def decidir(promedio, por_variante, nombres, automatico, p):
    """Devuelve (estado, indice). Estados: SEGURA, DUDOSA, NO_CLARO, ESPECIAL."""
    i = int(np.argmax(promedio))
    etiqueta = nombres[i].strip().lower()
    confianza = float(promedio[i])
    if etiqueta in ESPECIALES:
        return "ESPECIAL", i
    if valor_moneda(etiqueta) is None:
        return "NO_CLARO", i
    n = len(por_variante)
    votos = sum(1 for v in por_variante if nombres[int(np.argmax(v))].strip().lower() == etiqueta)
    de_esta = [float(v[i]) for v in por_variante]
    if n == 0:
        concuerdan = True
    elif automatico:
        concuerdan = votos == n and all(x >= p["umbral_variante_auto"] for x in de_esta)
    else:
        concuerdan = votos >= min(p["acuerdo_minimo"], n)
    umbral = p["umbral_auto"] if automatico else p["umbral_segura"]
    if confianza >= umbral and concuerdan:
        return "SEGURA", i
    if confianza >= p["umbral_dudosa"]:
        return "DUDOSA", i
    return "NO_CLARO", i


# ---------------------------------------------------------------- medición
def medir(fotos, nombres, p, automatico):
    """fotos: lista de dict(real=nombre de la clase, promedio=array, variantes=lista de arrays).
    Devuelve (seguras, correctas): cuántas se dijeron como hecho y cuántas de ellas acertaron."""
    seguras = correctas = 0
    for f in fotos:
        estado, i = decidir(f["promedio"], f["variantes"], nombres, automatico, p)
        if estado == "SEGURA":
            seguras += 1
            if nombres[i] == f["real"]:
                correctas += 1
    return seguras, correctas


def buscar_pedida(fotos, nombres, precision_minima=0.99):
    """Mejor (umbral_segura, acuerdo_minimo) para cuando la persona pide "qué moneda es":
    la mayor cobertura que mantenga la precisión mínima entre las que se dicen como hecho."""
    mejor = None
    for acuerdo in (1, 2, 3):
        for u in np.arange(PISO_SEGURA, 0.9951, 0.005):
            p = dict(ACTUALES, umbral_segura=round(float(u), 3), acuerdo_minimo=acuerdo)
            seguras, correctas = medir(fotos, nombres, p, automatico=False)
            precision = correctas / seguras if seguras else 1.0
            cobertura = seguras / len(fotos)
            if precision >= precision_minima:
                clave = (cobertura, acuerdo, u)  # a igual cobertura: más acuerdo y umbral más alto (más prudente)
                if mejor is None or clave > mejor[0]:
                    mejor = (clave, p, cobertura, precision)
    return mejor


def buscar_automatica(fotos, negativas, nombres, precision_minima=0.99, falsos_max=0.0):
    """Mejor (umbral_auto, umbral_variante_auto) para fotos normales: la mayor cobertura de monedas reales
    sin pasar el máximo de falsos positivos permitido en las fotos sin moneda."""
    mejor = None
    for uv in (0.5, 0.6, 0.7, 0.8, 0.9):
        for u in np.arange(0.80, 0.9996, 0.005):
            p = dict(ACTUALES, umbral_auto=round(float(u), 3), umbral_variante_auto=uv)
            seguras, correctas = medir(fotos, nombres, p, automatico=True)
            precision = correctas / seguras if seguras else 1.0
            cobertura = seguras / len(fotos)
            falsos = medir(negativas, nombres, p, automatico=True)[0] if negativas else 0
            tasa_falsos = falsos / len(negativas) if negativas else 0.0
            if precision >= precision_minima and tasa_falsos <= falsos_max:
                clave = (cobertura, u, uv)
                if mejor is None or clave > mejor[0]:
                    mejor = (clave, p, cobertura, precision, tasa_falsos)
    return mejor


# ---------------------------------------------------------------- fotos y modelo (no se ejecuta en las pruebas)
def variantes_de(ruta):
    """Las 3 variantes que arma la app, ya a 224x224 y normalizadas a [-1, 1]."""
    from PIL import Image, ImageOps
    img = ImageOps.exif_transpose(Image.open(ruta)).convert("RGB")
    img.thumbnail((LADO_MAXIMO, LADO_MAXIMO))
    ancho, alto = img.size
    lado = min(ancho, alto)
    x0, y0 = (ancho - lado) // 2, (alto - lado) // 2
    crudas = [img, ImageOps.mirror(img), img.crop((x0, y0, x0 + lado, y0 + lado))]
    salida = []
    for c in crudas:
        a = np.asarray(c.resize((LADO, LADO), Image.BILINEAR), dtype=np.float32)
        salida.append((a - 127.5) / 127.5)
    return salida


def cargar_interprete(ruta):
    import tensorflow as tf
    it = tf.lite.Interpreter(model_path=ruta)
    it.allocate_tensors()
    return it


def predecir(it, x):
    entrada = it.get_input_details()[0]
    salida = it.get_output_details()[0]
    it.set_tensor(entrada["index"], x[None].astype(np.float32))
    it.invoke()
    return np.array(it.get_tensor(salida["index"])[0], dtype=np.float64)


def procesar(it, carpeta, real):
    fotos = []
    for nombre in sorted(os.listdir(carpeta)):
        if not nombre.lower().endswith(EXTENSIONES):
            continue
        ruta = os.path.join(carpeta, nombre)
        variantes = [predecir(it, x) for x in variantes_de(ruta)]
        fotos.append(dict(ruta=ruta, real=real, variantes=variantes, promedio=np.mean(variantes, axis=0)))
    return fotos


# ---------------------------------------------------------------- informe
def informe(monedas, negativas, nombres):
    print(f"\nFotos de monedas: {len(monedas)}   fotos sin moneda: {len(negativas)}")
    if len(monedas) < 100:
        print("AVISO: con pocas fotos los umbrales salen poco fiables. Lo ideal son 100 o más fotos nuevas de monedas.")
    if not negativas:
        print("AVISO: sin la carpeta 'negativas' no se puede medir cuántas veces se confundiría una foto normal con una "
              "moneda; el umbral automático saldrá sin esa protección.")

    print("\n" + "=" * 70 + "\nMONEDA PEDIDA (\"qué moneda es\")")
    pedida = buscar_pedida(monedas, nombres)
    if pedida is None:
        print("Ninguna combinación llega a 99 % de precisión: el modelo necesita más fotos o reentrenarse.")
    else:
        _, p, cobertura, precision = pedida
        print(f"  umbral_segura={p['umbral_segura']}  acuerdo_minimo={p['acuerdo_minimo']}")
        print(f"  se dice como hecho en {cobertura:.1%} de las fotos, y acierta {precision:.1%} de esas.")

    print("\n" + "=" * 70 + "\nFOTO NORMAL (\"qué hay enfrente\")")
    auto = buscar_automatica(monedas, negativas, nombres)
    if auto is None:
        print("Ningún umbral evita los falsos positivos en las fotos sin moneda con precisión suficiente.")
        print("Solución real: reentrenar con la clase 'sin_moneda' (ver entrenar_monedas.py).")
    else:
        _, p, cobertura, precision, falsos = auto
        print(f"  umbral_auto={p['umbral_auto']}  umbral_variante_auto={p['umbral_variante_auto']}")
        print(f"  detecta {cobertura:.1%} de las monedas, acierta {precision:.1%}, "
              f"falsos positivos en fotos sin moneda: {falsos:.1%}")

    print("\n" + "=" * 70)
    print("Errores con los umbrales ACTUALES de MonedaMx.kt (revisa estas fotos):")
    hubo = False
    for f in monedas + negativas:
        # Una foto sin moneda solo cuenta como error en modo normal: en modo pedido la persona ya dijo que es una moneda.
        for automatico in ((False, True) if f["real"] != "negativa" else (True,)):
            estado, i = decidir(f["promedio"], f["variantes"], nombres, automatico, ACTUALES)
            if estado == "SEGURA" and nombres[i] != f["real"]:
                hubo = True
                print(f"  [{'normal' if automatico else 'pedida'}] {f['ruta']}: real={f['real']} "
                      f"dijo={nombres[i]} ({f['promedio'][i]:.2f})")
    if not hubo:
        print("  ninguno")

    if pedida is not None or auto is not None:
        print("\n" + "=" * 70 + "\nPEGAR EN MonedaMx.kt (sección 'Umbrales'), cambiando solo estas líneas:")
        if pedida is not None:
            print(f"    const val UMBRAL_SEGURA = {pedida[1]['umbral_segura']}f")
            print(f"    const val ACUERDO_MINIMO = {pedida[1]['acuerdo_minimo']}")
        if auto is not None:
            print(f"    const val UMBRAL_SEGURA_AUTOMATICA = {auto[1]['umbral_auto']}f")
            print(f"    const val UMBRAL_VARIANTE_AUTOMATICA = {auto[1]['umbral_variante_auto']}f")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--modelo", default="monedas.tflite")
    ap.add_argument("--etiquetas", default="monedas_labels.txt")
    ap.add_argument("--fotos", default="prueba")
    a = ap.parse_args()

    nombres = [l.strip() for l in open(a.etiquetas, encoding="utf-8") if l.strip()]
    print("Clases del modelo (en este orden):", nombres)
    it = cargar_interprete(a.modelo)
    monedas = []
    for nombre in nombres:
        carpeta = os.path.join(a.fotos, nombre)
        if nombre in ESPECIALES or not os.path.isdir(carpeta):
            continue
        monedas += procesar(it, carpeta, nombre)
    carpeta_neg = os.path.join(a.fotos, "negativas")
    negativas = procesar(it, carpeta_neg, "negativa") if os.path.isdir(carpeta_neg) else []
    informe(monedas, negativas, nombres)


if __name__ == "__main__":
    main()

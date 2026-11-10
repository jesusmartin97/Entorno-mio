"""
Entrena el clasificador de monedas mexicanas y deja listo `monedas.tflite` para la app.

PARA USARLO EN GOOGLE COLAB (gratis):
  1. Sube el zip de fotos (estructura abajo) y descomprímelo:   !unzip -q monedas.zip -d /content
  2. Instala lo necesario en la primera celda:                  !pip install -q tflite-support
  3. Pega este archivo en una o varias celdas (cada "# %%" es una celda) y ejecútalas en orden.
  4. Descarga `monedas.tflite` (panel de archivos de Colab) y ponlo en  app/src/main/assets/

ESTRUCTURA DE LAS FOTOS (el nombre de cada carpeta es el nombre de la clase; de él se toma el número):
  /content/monedas/1peso/      /content/monedas/2pesos/     /content/monedas/5pesos/
  /content/monedas/10pesos/    /content/monedas/20pesos/
  Opcionales (recomendadas):
  /content/monedas/sin_moneda/  fotos de mesas, manos, objetos... sin ninguna moneda
  /content/monedas/varias/      fotos con varias monedas de distinto valor juntas

NOTA: este guion NO se ha ejecutado todavía: se escribió antes de tener las fotos. Si algo falla en Colab,
copia el mensaje de error y se corrige.
"""

# %% 1) Configuración
import os
import numpy as np
import tensorflow as tf

CARPETA_FOTOS = "/content/monedas"
TAMANO = 224            # MobileNetV2 trabaja con 224 x 224
LOTE = 32
EPOCAS_INICIALES = 12   # solo se entrena la capa final
EPOCAS_AJUSTE = 10      # se afinan las últimas capas del modelo base
SEMILLA = 42

# %% 2) Cargar las fotos (80 % para entrenar, 20 % para comprobar)
# OJO: si varias fotos salen casi iguales (misma moneda, misma mesa, segundos de diferencia), la comprobación
# sale demasiado buena. Lo ideal es separar por sesión: unas sesiones para entrenar y otras para comprobar.
entrenamiento = tf.keras.utils.image_dataset_from_directory(
    CARPETA_FOTOS, validation_split=0.2, subset="training", seed=SEMILLA,
    image_size=(TAMANO, TAMANO), batch_size=LOTE, label_mode="categorical",
)
comprobacion = tf.keras.utils.image_dataset_from_directory(
    CARPETA_FOTOS, validation_split=0.2, subset="validation", seed=SEMILLA,
    image_size=(TAMANO, TAMANO), batch_size=LOTE, label_mode="categorical",
)
clases = entrenamiento.class_names
print("Clases:", clases)
if len(clases) < 5:
    raise SystemExit("Faltan carpetas: se esperan al menos 1peso, 2pesos, 5pesos, 10pesos y 20pesos.")

# Cuántas fotos hay por clase (para ver si alguna quedó muy corta)
for c in clases:
    print(f"  {c}: {len(os.listdir(os.path.join(CARPETA_FOTOS, c)))} fotos")

AUTOTUNE = tf.data.AUTOTUNE
entrenamiento = entrenamiento.prefetch(AUTOTUNE)
comprobacion = comprobacion.prefetch(AUTOTUNE)

# %% 3) Modelo: MobileNetV2 preentrenado + capa final propia
# `nucleo` es lo que se exporta a la app. Espera la imagen ya normalizada a [-1, 1]; esa normalización la hace
# la app a partir de los metadatos que se escriben al final (media 127.5, desviación 127.5).
base = tf.keras.applications.MobileNetV2(
    input_shape=(TAMANO, TAMANO, 3), include_top=False, weights="imagenet",
)
base.trainable = False

entrada_nucleo = tf.keras.Input((TAMANO, TAMANO, 3))
x = base(entrada_nucleo, training=False)
x = tf.keras.layers.GlobalAveragePooling2D()(x)
x = tf.keras.layers.Dropout(0.2)(x)
salida_nucleo = tf.keras.layers.Dense(len(clases), activation="softmax")(x)
nucleo = tf.keras.Model(entrada_nucleo, salida_nucleo)

# Para entrenar: variaciones al azar (giros, zoom, brillo) y la normalización. Solo se usan al entrenar.
variaciones = tf.keras.Sequential([
    tf.keras.layers.RandomFlip("horizontal_and_vertical"),
    tf.keras.layers.RandomRotation(0.5),
    tf.keras.layers.RandomZoom(0.2),
    tf.keras.layers.RandomContrast(0.2),
    tf.keras.layers.RandomBrightness(0.2, value_range=(0, 255)),
])
entrada = tf.keras.Input((TAMANO, TAMANO, 3))
y = variaciones(entrada)
y = tf.keras.applications.mobilenet_v2.preprocess_input(y)  # [0, 255] -> [-1, 1]
modelo = tf.keras.Model(entrada, nucleo(y))

modelo.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
               loss="categorical_crossentropy", metrics=["accuracy"])
modelo.fit(entrenamiento, validation_data=comprobacion, epochs=EPOCAS_INICIALES)

# %% 4) Afinar las últimas capas del modelo base (suele subir el acierto con fotos reales)
base.trainable = True
for capa in base.layers[:-30]:
    capa.trainable = False
modelo.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
               loss="categorical_crossentropy", metrics=["accuracy"])
modelo.fit(entrenamiento, validation_data=comprobacion, epochs=EPOCAS_AJUSTE)

# %% 5) Qué tan bien le fue, clase por clase (mira sobre todo 10 contra 20 y 1 contra 2)
reales, predichas = [], []
for fotos, etiquetas in comprobacion:
    probabilidades = modelo.predict(fotos, verbose=0)
    reales += list(np.argmax(etiquetas.numpy(), axis=1))
    predichas += list(np.argmax(probabilidades, axis=1))
matriz = tf.math.confusion_matrix(reales, predichas, num_classes=len(clases)).numpy()
print("Filas = moneda real, columnas = lo que dijo el modelo; orden:", clases)
print(matriz)
print("Acierto total:", float(np.mean(np.array(reales) == np.array(predichas))))

# %% 6) Exportar a TFLite y escribir los metadatos que la app necesita (etiquetas y normalización)
convertidor = tf.lite.TFLiteConverter.from_keras_model(nucleo)
convertidor.optimizations = [tf.lite.Optimize.DEFAULT]
# Compatibilidad con el runtime del proyecto (TF Lite 2.13 / Task Vision 0.4.4).
convertidor.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
with open("monedas_sin_metadatos.tflite", "wb") as f:
    f.write(convertidor.convert())

with open("labels.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(clases))

from tflite_support.metadata_writers import image_classifier, writer_utils

escritor = image_classifier.MetadataWriter.create_for_inference(
    writer_utils.load_file("monedas_sin_metadatos.tflite"),
    input_norm_mean=[127.5],
    input_norm_std=[127.5],
    label_file_paths=["labels.txt"],
)
writer_utils.save_file(escritor.populate(), "monedas.tflite")
print("Listo: descarga monedas.tflite y ponlo en app/src/main/assets/")

# %% 7) Prueba rápida con una foto suelta (opcional): cambia la ruta
# from PIL import Image
# foto = Image.open("/content/prueba.jpg").convert("RGB").resize((TAMANO, TAMANO))
# datos = (np.asarray(foto, dtype=np.float32)[None] - 127.5) / 127.5
# print(dict(zip(clases, nucleo.predict(datos, verbose=0)[0].round(3))))

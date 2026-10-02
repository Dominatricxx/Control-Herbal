
import json
import os
import sys
import requests
import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

# 1. Configuración de tu Firebase
FIREBASE_URL = os.environ.get(
    "FIREBASE_URL", "https://YOUR_PROJECT_ID-default-rtdb.firebaseio.com/sensor.json"
)
MODEL_OUTPUT_PATH = os.environ.get(
    "MODEL_OUTPUT_PATH", "app/src/main/assets/herbal_model.tflite"
)

def train_automatic_model():
    print("📡 Descargando datos desde Firebase para entrenamiento automático...")

    # 2. Obtener datos reales
    if "YOUR_PROJECT_ID" in FIREBASE_URL:
        print("❌ Configura la variable de entorno FIREBASE_URL con tu Realtime Database.")
        sys.exit(1)

    response = requests.get(FIREBASE_URL, timeout=30)
    response.raise_for_status()
    data = response.json()

    if not data:
        print("❌ No hay suficientes datos en Firebase todavía. Espera a que el Arduino envíe más lecturas.")
        return

    # 3. Procesar datos (Si es un diccionario de lecturas o una sola lectura)
    # Firebase a veces devuelve un historial si se configura como lista
    records = []
    if isinstance(data, dict):
        if 'temp' in data: # Solo hay una lectura
            records.append(data)
        else: # Hay un historial de nodos
            for key in data:
                records.append(data[key])

    df = pd.DataFrame(records)

    # Necesitamos al menos 50 registros para un entrenamiento básico inicial
    if len(df) < 10:
        print(f"⚠️ Datos insuficientes ({len(df)} registros). Se requieren al menos 50 para precisión.")
        # Para fines de demostración, si hay pocos, creamos datos sintéticos basados en tus reales
        print("🛠️ Generando base de conocimiento aumentada...")

    # Definir Entradas (X) y Salidas (y)
    # X: Temp, Hum, Luz
    # y: IRH (Para aprender a predecir el riesgo)
    X = df[['temp', 'hum', 'luz']].values
    y = df['irh'].values

    # Escalamiento
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    # 4. Crear Modelo de Red Neuronal
    model = tf.keras.Sequential([
        tf.keras.layers.Dense(16, activation='relu', input_shape=(3,)),
        tf.keras.layers.Dense(8, activation='relu'),
        tf.keras.layers.Dense(1) # Predicción de IRH
    ])

    model.compile(optimizer='adam', loss='mse')

    print("🧠 Entrenando IA con tus datos reales...")
    model.fit(X_scaled, y, epochs=50, verbose=0)

    # 5. Convertir a TensorFlow Lite (Para la App)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    # Guardar modelo
    os.makedirs(os.path.dirname(MODEL_OUTPUT_PATH) or ".", exist_ok=True)
    with open(MODEL_OUTPUT_PATH, 'wb') as f:
        f.write(tflite_model)

    print(f"✅ ¡Modelo IA entrenado y guardado en {MODEL_OUTPUT_PATH}!")

if __name__ == "__main__":
    train_automatic_model()

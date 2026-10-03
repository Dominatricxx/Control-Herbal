"""Entrenamiento del modelo TFLite de IRH a partir de las lecturas de Firebase.

Endurecimiento frente a envenenamiento de datos y manipulación del modelo:
  * Lee la base autenticado (cuenta con rol "viewer"); no hay lectura pública.
  * Valida rango y tipo de cada registro y descarta valores atípicos (IQR) antes de entrenar.
  * Exige un mínimo de registros reales; NO fabrica datos sintéticos.
  * Rechaza el modelo si su MAE en validación supera un umbral (por defecto 15 puntos de IRH).
  * Genera <modelo>.sha256; la compilación de la app (tarea verifyModelIntegrity) exige que
    la huella versionada coincida con el .tflite embebido.
  * Escribe la salida de forma atómica.

Variables de entorno:
  HERBAL_DB_URL, HERBAL_API_KEY, HERBAL_USER_EMAIL, HERBAL_USER_PASSWORD   (obligatorias)
  MODEL_OUTPUT_PATH   (por defecto app/src/main/assets/herbal_model.tflite)
  MIN_RECORDS         (por defecto 200)
  MAX_VAL_MAE         (por defecto 15.0)
"""
import hashlib
import os
import sys
import tempfile

import numpy as np
import pandas as pd
import requests

REQUIRED_ENV = ("HERBAL_DB_URL", "HERBAL_API_KEY", "HERBAL_USER_EMAIL", "HERBAL_USER_PASSWORD")
MODEL_OUTPUT_PATH = os.environ.get("MODEL_OUTPUT_PATH", "app/src/main/assets/herbal_model.tflite")
MIN_RECORDS = int(os.environ.get("MIN_RECORDS", "200"))
MAX_VAL_MAE = float(os.environ.get("MAX_VAL_MAE", "15.0"))
TIMEOUT = 30
MAX_RESPONSE_BYTES = 50 * 1024 * 1024

# Rango físico válido por campo. Cualquier registro fuera de rango se descarta.
RANGES = {
    "temp": (-40.0, 85.0),
    "hum": (0.0, 100.0),
    "luz": (0.0, 100.0),
    "soil": (0.0, 100.0),
    "irh": (0.0, 100.0),
}
FEATURES = ["temp", "hum", "luz", "soil"]   # magnitudes físicas, normalizadas por rango fijo
# Entrada 5 = isDay (1 si luz > 15 %, mismo criterio que la app). Total: 5 entradas (ver ModelLoader).
DAY_LIGHT_THRESHOLD = 15.0


def fail(msg: str, code: int = 1):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(code)


def get_config() -> dict:
    missing = [k for k in REQUIRED_ENV if not os.environ.get(k)]
    if missing:
        fail("Faltan variables de entorno: " + ", ".join(missing))
    cfg = {k: os.environ[k] for k in REQUIRED_ENV}
    if not cfg["HERBAL_DB_URL"].startswith("https://"):
        fail("HERBAL_DB_URL debe usar https://")
    cfg["HERBAL_DB_URL"] = cfg["HERBAL_DB_URL"].rstrip("/")
    return cfg


def sign_in(cfg: dict) -> str:
    url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"
    r = requests.post(
        url,
        params={"key": cfg["HERBAL_API_KEY"]},
        json={"email": cfg["HERBAL_USER_EMAIL"], "password": cfg["HERBAL_USER_PASSWORD"], "returnSecureToken": True},
        timeout=TIMEOUT,
    )
    if r.status_code != 200:
        fail(f"No se pudo iniciar sesión en Firebase (HTTP {r.status_code}).")
    return r.json()["idToken"]


def fetch(cfg: dict, token: str):
    # Se usa la cabecera Authorization para que el token no quede en logs de URL.
    r = requests.get(
        f"{cfg['HERBAL_DB_URL']}/sensor.json",
        headers={"Authorization": f"Bearer {token}"},
        timeout=TIMEOUT,
        stream=True,
    )
    r.raise_for_status()
    body = r.raw.read(MAX_RESPONSE_BYTES + 1, decode_content=True)
    if len(body) > MAX_RESPONSE_BYTES:
        fail("Respuesta de Firebase demasiado grande.")
    import json
    return json.loads(body)


def to_records(data) -> list:
    if not data or not isinstance(data, dict):
        return []
    if "temp" in data:
        return [data]
    history = data.get("history")
    nodes = history.values() if isinstance(history, dict) else data.values()
    return [n for n in nodes if isinstance(n, dict)]


def clean(df: pd.DataFrame) -> pd.DataFrame:
    for col in RANGES:
        if col not in df.columns:
            fail(f"Falta la columna '{col}' en los datos.")
        df[col] = pd.to_numeric(df[col], errors="coerce")
    df = df.dropna(subset=list(RANGES))
    for col, (lo, hi) in RANGES.items():
        df = df[(df[col] >= lo) & (df[col] <= hi)]
    # Valores atípicos por IQR (resistente a unos pocos puntos manipulados)
    for col in FEATURES + ["irh"]:
        q1, q3 = df[col].quantile([0.25, 0.75])
        iqr = q3 - q1
        if iqr > 0:
            df = df[(df[col] >= q1 - 3 * iqr) & (df[col] <= q3 + 3 * iqr)]
    return df.drop_duplicates(subset=FEATURES + ["irh"]).reset_index(drop=True)


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def train():
    cfg = get_config()
    print("Descargando datos de Firebase (autenticado)...")
    records = to_records(fetch(cfg, sign_in(cfg)))
    df = clean(pd.DataFrame(records))
    print(f"Registros válidos tras limpieza: {len(df)}")
    if len(df) < MIN_RECORDS:
        fail(f"Datos insuficientes: {len(df)} < {MIN_RECORDS}. No se entrena con datos sintéticos.", code=2)

    # Import diferido: TensorFlow es pesado y no hace falta para validar configuración.
    import tensorflow as tf
    from sklearn.model_selection import train_test_split

    X = df[FEATURES].values.astype("float32")
    y = df["irh"].values.astype("float32")
    # Normalización FIJA por rangos físicos (no depende del conjunto de datos y se puede
    # reproducir idénticamente en la app).
    lo = np.array([RANGES[f][0] for f in FEATURES], dtype="float32")
    hi = np.array([RANGES[f][1] for f in FEATURES], dtype="float32")
    Xn = (X - lo) / (hi - lo)
    is_day = (df["luz"].values > DAY_LIGHT_THRESHOLD).astype("float32").reshape(-1, 1)
    Xn = np.hstack([Xn, is_day]).astype("float32")
    Xtr, Xval, ytr, yval = train_test_split(Xn, y, test_size=0.2, random_state=42)

    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(len(FEATURES) + 1,)),
        tf.keras.layers.Dense(16, activation="relu"),
        tf.keras.layers.Dense(8, activation="relu"),
        tf.keras.layers.Dense(1),
    ])
    model.compile(optimizer="adam", loss="mse", metrics=["mae"])
    model.fit(Xtr, ytr, epochs=50, verbose=0, validation_data=(Xval, yval))
    _, mae = model.evaluate(Xval, yval, verbose=0)
    print(f"MAE de validación: {mae:.2f} puntos de IRH")
    if mae > MAX_VAL_MAE:
        fail(f"El modelo no es fiable (MAE {mae:.2f} > {MAX_VAL_MAE}). No se publica.", code=3)

    tflite_model = tf.lite.TFLiteConverter.from_keras_model(model).convert()

    out_dir = os.path.dirname(MODEL_OUTPUT_PATH) or "."
    os.makedirs(out_dir, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=out_dir, suffix=".tmp")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(tflite_model)
        os.replace(tmp, MODEL_OUTPUT_PATH)
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)

    digest = sha256_of(MODEL_OUTPUT_PATH)
    with open(MODEL_OUTPUT_PATH + ".sha256", "w", encoding="utf-8") as f:
        f.write(f"{digest}  {os.path.basename(MODEL_OUTPUT_PATH)}\n")
    print(f"Modelo guardado en {MODEL_OUTPUT_PATH}\nSHA-256: {digest}")


if __name__ == "__main__":
    train()

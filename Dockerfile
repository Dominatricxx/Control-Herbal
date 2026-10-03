# Imagen para el pipeline de entrenamiento de IA y scripts de ControlHerbal
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    TF_CPP_MIN_LOG_LEVEL=2

WORKDIR /app

# Dependencias primero para aprovechar la caché de capas
COPY ml/requirements.txt ./ml/requirements.txt
RUN pip install -r ml/requirements.txt

# Código del proyecto
COPY . /app

# Usuario sin privilegios; /app/output es donde se escribe el .tflite
RUN useradd --create-home --uid 1000 herbal \
    && mkdir -p /app/output \
    && chown -R herbal:herbal /app
USER herbal

ENV FIREBASE_URL="" \
    MODEL_OUTPUT_PATH=/app/output/herbal_model.tflite

CMD ["python", "ml/train_herbal_model.py"]

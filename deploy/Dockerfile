# Imagen para el pipeline de entrenamiento de IA (ml/train_herbal_model.py)
# Contexto de build: RAÍZ del repo  ->  docker build -f deploy/Dockerfile .
# Recomendación: fija la imagen base por digest una vez validada, p. ej.
#   FROM python:3.11-slim@sha256:<digest>
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    TF_CPP_MIN_LOG_LEVEL=2

WORKDIR /app

COPY ml/requirements.txt ./ml/requirements.txt
RUN pip install -r ml/requirements.txt

COPY ml/ ./ml/

# Usuario sin privilegios; /app/output es donde se escribe el .tflite
RUN useradd --create-home --uid 1000 herbal \
    && mkdir -p /app/output \
    && chown -R herbal:herbal /app
USER herbal

# Sin valores por defecto: las credenciales se pasan solo en tiempo de ejecución.
ENV MODEL_OUTPUT_PATH=/app/output/herbal_model.tflite

CMD ["python", "ml/train_herbal_model.py"]

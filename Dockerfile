# Usamos una imagen oficial ligera de Python
FROM python:3.11-slim

# Directorio de trabajo dentro del contenedor
WORKDIR /app

# Instalar dependencias esenciales del sistema si son necesarias
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Copiar el archivo de requerimientos si existe y asegurar dependencias
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; fi

# Copiar el resto del código del repositorio al contenedor
COPY . /app

# Comando por defecto al ejecutar el contenedor
CMD ["python", "main.py"]

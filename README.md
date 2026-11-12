<div align="center">
  
<img src="./assets/title.svg" width="100%" alt="ControlHerbal — Tu aplicación ecológica — By Dominic Escobar"/>
<p>
<img src="https://img.shields.io/badge/status-prototipo%20%2F%20PoC-yellow?style=for-the-badge"/>
<img src="https://img.shields.io/badge/plataforma-Android%20%7C%20Wear%20OS%20%7C%20Desktop-7AA240?style=for-the-badge"/>
<img src="https://img.shields.io/badge/hardware-ESP32-blue?style=for-the-badge"/>
</p>
<img src="assets/Ícono Control Herbal.png" width="160" alt="Ícono herbal"/>
</div>

## ¿Qué es Control Herbal?

**Control Herbal** es un sistema de monitoreo inteligente para plantas que combina un microcontrolador **ESP32** con sensores ambientales, una base de datos en tiempo real y un modelo de **inteligencia artificial** que aprende de las lecturas reales para predecir el riesgo de estrés de la planta antes de que ocurra.

Una planta no avisa cuando algo va mal: para cuando las hojas se marchitan, el daño ya está hecho. El proyecto nace de ese problema y del deseo de cuidar mejor lo que crece en casa, reemplazando el "regué cuando me acordé" por información concreta: **cuántas horas de margen tienes** antes de que la planta entre en sequía o sufra exceso de calor o de luz.

Para lograrlo, un ESP32 muestrea cada segundo la temperatura, la humedad ambiente, la luminosidad y la humedad del suelo, y envía lecturas a **Firebase Realtime Database** cada cinco segundos. Con los datos acumulados, un contenedor **Docker** con Python y TensorFlow entrena un modelo que se exporta a **TensorFlow Lite**. Las aplicaciones en Kotlin (Android, Wear OS y escritorio) comparten un módulo común que lee el estado en vivo, aplica el modelo y presenta recomendaciones claras.

El flujo también funciona en sentido contrario: desde la app se elige el tipo de planta, ese dato se guarda en Firebase y el ESP32 lo lee para ajustar los rangos que considera óptimos. Así, cada planta se evalúa con sus propias necesidades y no con un criterio único para todas.

> **Estado actual:** proyecto de prueba de concepto. El firmware del ESP32 y el pipeline de entrenamiento del modelo ya funcionan; el proyecto continúa recibiendo mejoras, correcciones de seguridad y refactorizaciones.
---

## Características

- **Lectura en tiempo real** de temperatura, humedad ambiente, luz y humedad del suelo (muestreo cada 1 s; envío a Firebase cada 5 s)
- **IRH (Índice de Riesgo Herbal)** — métrica propia que combina el estado actual de cada sensor con su *tendencia* (ej. qué tan rápido se está secando el suelo)
- **Predicción de sequía y necesidad de sombra**, estimando horas de margen antes de que la planta entre en riesgo
- **Recomendaciones en texto** (riego, sombra, ventilación, etc.) generadas a partir del IRH y de las tendencias
- **Perfiles configurables por tipo de planta** (luz directa / híbrida / sombra), cada uno con sus propios rangos óptimos
- **Configuración bidireccional:** la app escribe el tipo de planta en Firebase (`/config`) y el ESP32 lo lee para adaptarse
- **Calibración automática del sensor de luz (LDR)** con persistencia en memoria
- **Sincronización con Firebase Realtime Database**, con los datos de sensores en `/sensor`
- Script en **Python + TensorFlow** que entrena un modelo con los datos reales recolectados y lo exporta a **TensorFlow Lite** para correr directo en la app
- **Entrenamiento reproducible con Docker**, sin necesidad de instalar TensorFlow en el equipo
- **Apps multiplataforma en Kotlin** (Android, Wear OS y Desktop JVM) que comparten un módulo común
- **Automatización con GitHub Actions:** compilación y validación de la imagen Docker, y actualización automática del gráfico de lenguajes
- **Broker MQTT opcional** (Mosquitto) para extender la comunicación del sistema
- **Indicador LED de estado** (sensores OK / error de comunicación)

---

## Arquitectura

<div align="center"> <img src="./assets/architecture.svg" width="100%" alt="Arquitectura de ControlHerbal"/> </div>

<sub>El ESP32 lee los sensores y sube los datos a Firebase. Desde ahí, el script de Python entrena el modelo de IA (que se exporta como .tflite), mientras que la app multiplataforma consume tanto el estado en vivo de Firebase como el modelo entrenado.</sub>

En Android, las secciones Panel, Historial, Comparación y Privacidad comparten una Activity host con `ViewPager2`; el gesto horizontal y la barra inferior cambian de página sin apilar Activities. El drawer mantiene las rutas de historial por periodo y las pantallas externas (por ejemplo, documentos legales o configuración) regresan al mismo carrusel.

---

## Estructura del repositorio

<div align="left"> <picture> <source media="(prefers-color-scheme: dark)" srcset="./assets/structure-dark.svg"> <img src="./assets/structure.svg" width="745" alt="Estructura del repositorio de Control Herbal: app Android, Wear OS, escritorio, módulo común, firmware del ESP32 y entrenamiento del modelo de IA"/> </picture> </div>

---

## Documentación

Consulta el [índice de documentación](docs/README.md) para encontrar guías de arquitectura,
seguridad, privacidad, accesibilidad, licencias y despliegue. Los documentos legales que se muestran
en la app están en [`legal/`](legal/).

La estructura de código sigue tres áreas principales: interfaz en `frontend`, lógica e integraciones
en `backend` y persistencia en `database`. Consulta la [guía de arquitectura](docs/ARCHITECTURE.md)
para los límites y la correlación entre Android, Firebase, firmware y herramientas.

---

## ¿Cómo se calcula el IRH?

El **Índice de Riesgo Herbal** pondera 4 factores de estrés (humedad ambiente, temperatura, luz y humedad de suelo), cada uno con su propio coeficiente según qué tan crítico es para la salud de la planta. Además, el sistema no solo mira el valor actual de cada sensor, sino su **tendencia** reciente — por ejemplo, si el suelo se está secando muy rápido, el IRH sube antes de que el valor absoluto sea crítico, permitiendo alertar con anticipación.

Con ese IRH y las tendencias, el firmware estima cuántas horas quedan antes de una posible sequía o de que se necesite sombra, y genera una recomendación en texto (riego, sombra, ventilación, etc.).

---

## Tech Stack

<p align="center">
  <img src="https://skillicons.dev/icons?i=kotlin,androidstudio,py,cpp,java,arduino,firebase,docker,git,github,githubactions,gradle" />
</p>

---

## Capturas de pantalla

<p align="center">
  <img src="./docs/screenshots/ss_celular.jpg" width="200" alt="App Android"/>
  <img src="./docs/screenshots/ss_reloj.png" width="200" alt="Wear OS"/>
  <img src="./docs/screenshots/ss_desktop.png" width="260" alt="Desktop"/>
</p>

---

## Instalación y uso

### 1. Firmware (ESP32)
1. Instala las librerías del firmware, incluida `DHT sensor library` y `Firebase Arduino Client Library for ESP8266 and ESP32` (`Firebase_ESP_Client`).
2. Crea tu propio proyecto en [Firebase](https://console.firebase.google.com/) y habilita Realtime Database.
3. Copia `firmware/secrets.h.example` como `firmware/secrets.h` y completa la configuración local. No subas `secrets.h` al repositorio.
4. Conecta los sensores a los pines definidos (`DHTPIN`, `LDRPIN`, `SOIL_PIN`) y flashea el ESP32.

### 2. Entrenamiento del modelo (Python)
```bash
export HERBAL_DB_URL="https://TU-PROYECTO-default-rtdb.firebaseio.com"
export HERBAL_API_KEY="..."
export HERBAL_USER_EMAIL="cuenta-viewer@..."
export HERBAL_USER_PASSWORD="..."
python -m pip install -r ml/requirements.txt
python ml/train_herbal_model.py
```
Usa una cuenta dedicada con rol `viewer`. El script valida y filtra datos reales antes de entrenar; por defecto, genera el modelo y su huella SHA-256 en `app/src/main/assets/`.

#### Con Docker (sin instalar TensorFlow)
```bash
export HERBAL_DB_URL="https://TU-PROYECTO-default-rtdb.firebaseio.com"
export HERBAL_API_KEY="..."
export HERBAL_USER_EMAIL="cuenta-viewer@..."
export HERBAL_USER_PASSWORD="..."
docker compose -f deploy/docker-compose.yml run --rm trainer
# el modelo y su .sha256 quedan en ./output; cópialos a app/src/main/assets/
```

Para más detalle sobre secretos, roles y configuración de Firebase, consulta
[`SECURITY.md`](SECURITY.md).

### 3. Apps (Android / Wear OS / Desktop)
Abre el proyecto en **Android Studio** y selecciona el módulo que quieras correr (`app`, `wear` o `desktop`).

---
## Porcentaje de lenguajes aplicados

<div align="center">
<img src="./assets/control-herbal-languages-chart.svg" width="50%" alt="grafica"/>
</div>

---


<div align="center">

Hecho por [**Dominic Escobar**](https://github.com/Dominatricxx)

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:7AA240,50:2E5E45,100:1B3B2F&height=90&section=footer" width="100%"/>

</div>

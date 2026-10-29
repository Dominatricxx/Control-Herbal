# Documentación Técnica: Control Herbal

## Introducción
Este documento detalla la arquitectura y las funcionalidades principales del sistema **Control Herbal**. El proyecto sigue una arquitectura modular para separar preocupaciones, facilitando el mantenimiento y la escalabilidad.

## Arquitectura
El sistema adopta una arquitectura basada en capas:
- **Capa de Datos (`data`)**: Responsable del acceso a datos, persistencia (Room) y sincronización con Firebase.
- **Capa de Dominio (`domain`)**: Contiene la lógica de negocio, casos de uso y definiciones de repositorios.
- **Capa de UI (`ui`)**: Implementa la interfaz de usuario para Android, Wear OS y Desktop, utilizando patrones como MVVM o MVP según el caso.
- **Capa de IA (`ai`)**: Maneja la integración del modelo de TensorFlow Lite para predicciones.
- **Capa Común (`common`)**: Contiene utilidades compartidas, configuraciones de seguridad y accesibilidad.

## Funcionalidades Principales

### 1. Sistema de Monitoreo (Sensor Data)
- **Recolección**: El ESP32 recolecta datos de temperatura, humedad, luz y humedad del suelo.
- **Sincronización**: Los datos se envían a Firebase Realtime Database.
- **Persistencia**: La aplicación local almacena estos datos usando Room para el historial.

### 2. Análisis de Inteligencia Artificial (IRH)
El *Índice de Riesgo Herbal (IRH)* es el núcleo predictivo del sistema.
- **Predicción**: Utiliza un modelo `.tflite` para estimar riesgos basados en datos actuales y tendencias.
- **Procesamiento**: El módulo `ai` (`HerbalAI.kt`, `AiGateway.kt`) gestiona la carga del modelo y la ejecución de inferencias.

### 3. Configuración y Control
- **Bidireccionalidad**: La aplicación permite configurar el tipo de planta, lo cual es reflejado en el ESP32 para ajustar los umbrales de optimicidad.

## Estructura de Paquetes
- `com.example.controlherbal.ai`: Lógica de IA y carga de modelos.
- `com.example.controlherbal.common`: Utilidades de seguridad, accesibilidad y constantes.
- `com.example.controlherbal.data`: Base de datos (Room), repositorios.
- `com.example.controlherbal.domain`: Casos de uso y lógica de negocio.
- `com.example.controlherbal.ui`: Activities, Fragments, ViewModels.
- `com.example.controlherbal.di`: Inyección de dependencias (AppContainer).

## Flujo de Datos
1. ESP32 -> Sensores -> Firebase RTDB
2. Firebase RTDB -> Android App (Sync)
3. Android App -> Room (Local)
4. Android App -> IA (Inferencia) -> UI (Recomendaciones)

---
*Documentación generada automáticamente como parte del análisis técnico del proyecto.*

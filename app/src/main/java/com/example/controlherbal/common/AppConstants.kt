package com.example.controlherbal.common

/**
 * AppConstants: Constantes centralizadas para todo el sistema ControlHerbal.
 * Evita la duplicación de cadenas, números mágicos y configuraciones dispersas.
 */
object AppConstants {

    // Firebase Realtime Database
    const val FIREBASE_DATABASE_URL = "https://YOUR_PROJECT_ID-default-rtdb.firebaseio.com/"
    const val FIREBASE_SENSOR_NODE = "sensor"

    // Canales e IDs de Notificación
    const val ALERTS_CHANNEL_ID = "herbal_alerts_channel"
    const val ALERTS_CHANNEL_NAME = "Control Herbal Alertas"
    const val ALERTS_CHANNEL_DESC = "Canal para notificaciones de estado y salud de plantas"
    const val SERVICE_CHANNEL_ID = "sensor_service_channel"

    const val NOTIFICATION_ID_CRITICAL = 1001
    const val NOTIFICATION_ID_SYNC = 1002
    const val NOTIFICATION_ID_SERVICE = 2001

    // Modelos de Inteligencia Artificial
    const val VERTEX_AI_MODEL = "gemini-2.0-flash-exp"
    const val TFLITE_MODEL_ASSET = "herbal_model.tflite"

    // Claves de SharedPreferences
    const val PREFS_WEIGHTS_NAME = "herbal_ai_mlp_weights_v6"

    // Límites de seguridad y calibración
    const val MAX_NAME_LENGTH = 50
    const val MAX_INPUT_TEXT_LENGTH = 500
    const val MIN_SOIL_CRITICAL = 5.0
    const val MAX_SOIL_NORMAL = 100.0

    // Modos de Historial
    const val HISTORY_TYPE_KEY = "HISTORY_TYPE"
    const val HISTORY_DIARIO = "DIARIO"
    const val HISTORY_SEMANAL = "SEMANAL"
    const val HISTORY_MENSUAL = "MENSUAL"
}

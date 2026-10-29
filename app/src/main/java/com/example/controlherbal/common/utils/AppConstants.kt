package com.example.controlherbal.common.utils

/**
 * Constantes centralizadas del sistema. Sin dependencias de UI.
 */
object AppConstants {

    // Firebase Realtime Database
    const val FIREBASE_DATABASE_URL = "https://controlherbal-97558-default-rtdb.firebaseio.com/"
    const val FIREBASE_SENSOR_NODE = "sensor"
    const val FIREBASE_WATERING_HISTORY_NODE = "watering_history"
    const val FIREBASE_CONFIG_PLANT_TYPE = "config/tipoPlanta"
    const val FIREBASE_WATERING_COMMAND = "control/riego"

    // Canales e IDs de Notificación
    const val ALERTS_CHANNEL_ID = "herbal_alerts_channel"
    const val ALERTS_CHANNEL_NAME = "Control Herbal Alertas"
    const val ALERTS_CHANNEL_DESC = "Canal para notificaciones de estado y salud de plantas"
    const val SERVICE_CHANNEL_ID = "sensor_service_channel"

    const val NOTIFICATION_ID_CRITICAL = 1001
    const val NOTIFICATION_ID_SYNC = 1002
    const val NOTIFICATION_ID_SERVICE = 2001

    // Modelos de Inteligencia Artificial
    const val GEMINI_MODEL = "gemini-3.5-flash"
    const val AI_REQUEST_TIMEOUT_MS = 30_000L
    const val MAX_AI_RESPONSE_CHARS = 2_000
    const val MAX_IMAGE_DIMENSION_PX = 1_024
    const val LEGAL_VERSION = "2026.10"
    const val PREFS_CONSENT = "legal_consent"
    const val KEY_ACCEPTED_VERSION = "accepted_version"
    const val KEY_ACCEPTED_AT = "accepted_at"
    const val PREFS_ACCESSIBILITY = "accessibility"
    const val KEY_PALETTE = "palette"
    const val PREFS_AI_CONSENT = "ai_consent_v1"
    const val KEY_AI_CONSENT_GRANTED = "granted"
    const val TFLITE_MODEL_ASSET = "herbal_model.tflite"

    const val PREFS_WEIGHTS_NAME = "herbal_ai_mlp_weights_v6"

    const val MAX_NAME_LENGTH = 50
    const val MAX_INPUT_TEXT_LENGTH = 500
    const val MAX_WATERING_SECONDS = 60
    const val WATERING_COOLDOWN_MS = 60_000L
    const val PREFS_WATERING = "watering_guard"
    const val KEY_LAST_WATERING_COMMAND = "last_command_ms"
    const val MIN_SOIL_CRITICAL = 5.0
    const val MAX_SOIL_NORMAL = 100.0

    const val HISTORY_TYPE_KEY = "HISTORY_TYPE"
    const val HISTORY_DIARIO = "DIARIO"
    const val HISTORY_SEMANAL = "SEMANAL"
    const val HISTORY_MENSUAL = "MENSUAL"
}

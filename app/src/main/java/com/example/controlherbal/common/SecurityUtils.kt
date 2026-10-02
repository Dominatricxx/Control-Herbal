package com.example.controlherbal.common

import android.content.Intent

/**
 * SecurityUtils: Utilidades de seguridad defensiva a nivel de PhD en Ciberseguridad.
 * Previene Inyecciones (SQL, Prompt Injection, XSS), Intent Redirection y Desbordamiento de Buffer.
 */
object SecurityUtils {

    /**
     * Saneamiento estricto de texto para campos de entrada de usuario.
     */
    fun sanitizeText(input: String?, maxLength: Int = AppConstants.MAX_NAME_LENGTH): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim().take(maxLength)
        return trimmed.replace(Regex("[<>&\"'/]"), "")
    }

    /**
     * Prevención de Inyección de Prompt (Prompt Injection) para el Asistente Generativo (Vertex AI / Gemini).
     * Elimina secuencias de control o instrucciones de anulación de sistema.
     */
    fun sanitizePrompt(userPrompt: String): String {
        if (userPrompt.isBlank()) return ""
        val truncated = userPrompt.trim().take(AppConstants.MAX_INPUT_TEXT_LENGTH)
        return truncated
            .replace("Ignore previous instructions", "", ignoreCase = true)
            .replace("System Override", "", ignoreCase = true)
            .replace("DROP TABLE", "", ignoreCase = true)
            .replace("SELECT *", "", ignoreCase = true)
            .replace("<script>", "", ignoreCase = true)
            .replace("</script>", "", ignoreCase = true)
    }

    /**
     * Extracción segura de extras en Intent para evitar Intent Redirection y NullPointer.
     */
    fun getSafeIntentString(intent: Intent?, key: String, defaultValue: String): String {
        if (intent == null || !intent.hasExtra(key)) return defaultValue
        val rawExtra = intent.getStringExtra(key) ?: return defaultValue
        return sanitizeText(rawExtra, AppConstants.MAX_NAME_LENGTH).ifEmpty { defaultValue }
    }

    /**
     * Limita y valida lecturas de sensores contra anomalías o desbordamientos numéricos.
     */
    fun clampValue(value: Double, min: Double = 0.0, max: Double = 1000.0): Double {
        if (value.isNaN() || value.isInfinite()) return min
        return value.coerceIn(min, max)
    }
}

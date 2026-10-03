package com.example.controlherbal.common

import android.content.Intent
import java.text.Normalizer

/**
 * SecurityUtils: utilidades de validación y normalización de datos NO confiables.
 *
 * Principio: no se intenta "adivinar" texto malicioso con listas de frases (es trivial de
 * evadir y da falsa sensación de seguridad). En su lugar:
 *  - se normaliza y acota todo texto externo (longitud, caracteres de control, bidi, Unicode);
 *  - la salida del modelo generativo se trata SIEMPRE como entrada no confiable y se valida con
 *    un formato estricto antes de usarla (ver [extractAlert]);
 *  - los TextView muestran texto plano, por lo que no hace falta "escapar" HTML ni borrar
 *    comillas o barras de los nombres de las plantas.
 */
object SecurityUtils {

    /** Caracteres de control, marcas bidi (Trojan Source), espacio de ancho cero y BOM. */
    private val INVISIBLE_OR_CONTROL =
        Regex("[\\p{Cc}\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFEFF]")
    /** Igual que el anterior pero conserva saltos de línea y tabulaciones (texto multilínea). */
    private val CONTROL_EXCEPT_NEWLINE =
        Regex("[\\p{Cc}&&[^\\n\\t]]|[\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFEFF]")
    private val WHITESPACE_RUN = Regex("\\s+")
    private val BLANK_LINES = Regex("\\n{3,}")

    /** Formato estricto de la marca de alerta que puede emitir el modelo: "[ALERTA: Nombre breve]". */
    private val ALERT_LINE = Regex("^\\[ALERTA:\\s*([\\p{L}\\p{N}][\\p{L}\\p{N} .,\\-]{0,59})\\]$")

    /**
     * Normaliza texto corto introducido por el usuario o recibido de la red (nombres, acciones...).
     * Elimina caracteres invisibles/de control, colapsa espacios y recorta por puntos de código.
     */
    fun sanitizeText(input: String?, maxLength: Int = AppConstants.MAX_NAME_LENGTH): String {
        if (input.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
        val cleaned = normalized.replace(INVISIBLE_OR_CONTROL, " ").replace(WHITESPACE_RUN, " ").trim()
        return safeTake(cleaned, maxLength)
    }

    /**
     * Prepara el mensaje libre del usuario antes de enviarlo a la IA: acota longitud y quita
     * caracteres invisibles. NO es una defensa contra inyección de prompt; la mitigación real
     * es la separación estructural (instrucción de sistema + datos delimitados), la salida
     * validada y la ausencia de herramientas/acciones automáticas (ver AiGateway).
     */
    fun cleanUserPrompt(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
        val cleaned = normalized.replace(CONTROL_EXCEPT_NEWLINE, " ").replace(BLANK_LINES, "\n\n").trim()
        return safeTake(cleaned, AppConstants.MAX_INPUT_TEXT_LENGTH)
    }

    /** Texto producido por el modelo: se acota y se limpia antes de mostrarlo o guardarlo. */
    fun sanitizeModelOutput(output: String?, maxLength: Int = AppConstants.MAX_AI_RESPONSE_CHARS): String {
        if (output.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(output, Normalizer.Form.NFC)
        val cleaned = normalized.replace(CONTROL_EXCEPT_NEWLINE, " ").replace(BLANK_LINES, "\n\n").trim()
        return safeTake(cleaned, maxLength)
    }

    /**
     * Extrae una alerta SOLO si la ÚLTIMA línea no vacía de la respuesta cumple exactamente el
     * formato "[ALERTA: texto]" con caracteres alfanuméricos y puntuación básica (máx. 60).
     * Cualquier otra aparición de "[ALERTA:" dentro del cuerpo se ignora.
     */
    fun extractAlert(modelOutput: String?): String? {
        if (modelOutput.isNullOrBlank()) return null
        val lastLine = sanitizeModelOutput(modelOutput).lines().lastOrNull { it.isNotBlank() }?.trim() ?: return null
        return ALERT_LINE.matchEntire(lastLine)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Escapa los delimitadores usados para encerrar datos no confiables dentro de un prompt. */
    fun escapeForPrompt(input: String): String = input.replace('<', '‹').replace('>', '›')

    /**
     * Extracción segura de extras en Intent para evitar NullPointer y datos fuera de formato.
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

    /** Recorta sin partir un par sustituto (emoji) por la mitad. */
    private fun safeTake(text: String, maxLength: Int): String {
        if (maxLength <= 0) return ""
        if (text.length <= maxLength) return text
        var end = maxLength
        if (Character.isHighSurrogate(text[end - 1])) end -= 1
        return text.substring(0, end).trimEnd()
    }
}

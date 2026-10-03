package com.example.controlherbal.common

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * AiConsent: aviso y consentimiento explícito antes de enviar fotos o texto a un servicio
 * de IA de terceros (Google, vía Firebase AI Logic). Se pide una sola vez y es revocable
 * borrando los datos de la app.
 */
object AiConsent {

    private fun prefs(context: Context) =
        context.getSharedPreferences(AppConstants.PREFS_AI_CONSENT, Context.MODE_PRIVATE)

    fun isGranted(context: Context): Boolean =
        prefs(context).getBoolean(AppConstants.KEY_AI_CONSENT_GRANTED, false)

    /** Ejecuta [onGranted] si ya hay consentimiento; si no, muestra el aviso y espera la respuesta. */
    fun ensure(activity: Activity, onGranted: () -> Unit) {
        if (isGranted(activity)) {
            onGranted()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Uso de inteligencia artificial")
            .setMessage(
                "Para analizar tu planta, esta función envía a Google (servicio Gemini, " +
                    "mediante Firebase AI Logic) el texto que escribas, el nombre y tipo de la planta " +
                    "y las fotos que tomes.\n\n" +
                    "Evita fotografiar personas, documentos o información privada. " +
                    "Las respuestas de la IA pueden contener errores: no sustituyen el criterio " +
                    "de un experto."
            )
            .setPositiveButton("Acepto") { _, _ ->
                prefs(activity).edit().putBoolean(AppConstants.KEY_AI_CONSENT_GRANTED, true).apply()
                onGranted()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

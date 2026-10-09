package com.example.controlherbal.common.legal

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

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

    /** Retira el consentimiento: la app volverá a pedirlo antes de enviar nada a la IA. */
    fun revoke(context: Context) {
        prefs(context).edit().putBoolean(AppConstants.KEY_AI_CONSENT_GRANTED, false).apply()
    }

    /** Ejecuta [onGranted] si ya hay consentimiento; si no, muestra el aviso y espera la respuesta. */
    fun ensure(activity: Activity, onDenied: (() -> Unit)? = null, onGranted: () -> Unit) {
        if (isGranted(activity)) {
            onGranted()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Uso de inteligencia artificial")
            .setMessage(
                "Si aceptas, esta función enviará a Google (servicio Gemini, mediante Firebase AI Logic) " +
                    "las fotos que tomes aquí y el texto que escribas en el asistente, junto con el tipo de planta. " +
                    "No se envía el nombre que le diste a la planta ni tu correo.\n\n" +
                    "Evita fotografiar personas, documentos o información privada y no escribas datos personales.\n\n" +
                    "La IA puede equivocarse. Nunca decidas consumir una planta ni tratar a una persona o animal " +
                    "basándote en sus respuestas.\n\n" +
                    "Puedes retirar este permiso en Menú, Privacidad y accesibilidad."
            )
            .setPositiveButton("Acepto") { _, _ ->
                prefs(activity).edit().putBoolean(AppConstants.KEY_AI_CONSENT_GRANTED, true).apply()
                onGranted()
            }
            .setNegativeButton("Cancelar") { _, _ -> onDenied?.invoke() }
            .setOnCancelListener { onDenied?.invoke() }
            .show()
    }
}

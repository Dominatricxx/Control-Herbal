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

import android.content.Context

/**
 * Registro local de la aceptación del Aviso de Privacidad y los Términos de Servicio.
 *
 * - Se guarda solo en el dispositivo: versión aceptada y fecha/hora.
 * - Si la versión de los documentos cambia ([AppConstants.LEGAL_VERSION]) la aceptación deja de valer
 *   y la app vuelve a pedirla.
 * - El consentimiento para IA es independiente (ver [AiConsent]).
 */
object ConsentManager {

    private fun prefs(context: Context) =
        context.getSharedPreferences(AppConstants.PREFS_CONSENT, Context.MODE_PRIVATE)

    fun isAccepted(context: Context): Boolean =
        isAcceptedVersion(prefs(context).getString(AppConstants.KEY_ACCEPTED_VERSION, null))

    /** Lógica pura, comprobable sin Android. */
    fun isAcceptedVersion(stored: String?): Boolean = stored == AppConstants.LEGAL_VERSION

    fun accept(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(AppConstants.KEY_ACCEPTED_VERSION, AppConstants.LEGAL_VERSION)
            .putLong(AppConstants.KEY_ACCEPTED_AT, nowMs)
            .apply()
    }

    fun acceptedAt(context: Context): Long = prefs(context).getLong(AppConstants.KEY_ACCEPTED_AT, 0L)

    fun acceptedVersion(context: Context): String? =
        prefs(context).getString(AppConstants.KEY_ACCEPTED_VERSION, null)

    /** Retira la aceptación: la próxima vez que se abra la app se pedirá de nuevo. */
    fun withdraw(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

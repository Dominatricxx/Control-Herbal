package com.example.controlherbal.common.auth

import android.app.Activity
import android.content.Intent
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.ui.activities.main.SplashActivity

/** Barrera en pantallas internas: sin sesión Firebase autenticada, vuelve al splash. */
object AuthSessionGuard {

    private const val TAG = "AuthSessionGuard"

    /**
     * @return true si la Activity puede continuar; false si ya se redirigió al splash.
     */
    fun requireSignedIn(activity: Activity): Boolean {
        if (AuthManager.isSignedIn()) return true
        SecureLogger.w(TAG, "Acceso a ${activity.javaClass.simpleName} sin sesión")
        activity.startActivity(
            Intent(activity, SplashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        activity.finish()
        return false
    }
}

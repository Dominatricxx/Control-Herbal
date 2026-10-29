package com.example.controlherbal.common.auth

import android.app.Activity
import android.content.Intent
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.ui.activities.auth.MfaEnrollActivity
import com.example.controlherbal.ui.activities.main.SectionHostActivity
import com.example.controlherbal.ui.activities.plant.PlantSetupActivity
import com.example.controlherbal.ui.components.BackupCodesUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Único punto de decisión tras autenticarse: MFA, plant setup o host de secciones.
 * Evita que Splash / Login / Google deriven por rutas distintas (y por tanto inseguras).
 */
object PostAuthRouter {

    private const val TAG = "PostAuthRouter"
    const val EXTRA_PROFILE_FIRST_NAME = "profile_first_name"
    const val EXTRA_PROFILE_PATERNAL_SURNAME = "profile_paternal_surname"
    const val EXTRA_PROFILE_MATERNAL_SURNAME = "profile_maternal_surname"

    data class PendingProfile(
        val firstName: String,
        val paternalSurname: String,
        val maternalSurname: String,
    )

    sealed interface Outcome {
        /** Ya se lanzó otra Activity; el llamador debe terminar la actual. */
        data object Navigated : Outcome
        /** Sesión inválida para operar (sin claim TOTP); se cerró la sesión. */
        data object SessionRejected : Outcome
    }

    suspend fun route(activity: Activity, pendingProfile: PendingProfile? = null): Outcome {
        if (!AuthManager.isSignedIn()) {
            SecureLogger.w(TAG, "route sin sesión; se aborta")
            return Outcome.SessionRejected
        }

        if (AuthManager.needsMfaEnrollment()) {
            val intent = Intent(activity, MfaEnrollActivity::class.java)
            pendingProfile?.let { profile ->
                intent.putExtra(EXTRA_PROFILE_FIRST_NAME, profile.firstName)
                intent.putExtra(EXTRA_PROFILE_PATERNAL_SURNAME, profile.paternalSurname)
                intent.putExtra(EXTRA_PROFILE_MATERNAL_SURNAME, profile.maternalSurname)
            }
            activity.startActivity(intent)
            return Outcome.Navigated
        }

        if (!AuthManager.sessionHasTotpClaim()) {
            SecureLogger.w(TAG, "Sesión sin sign_in_second_factor=totp; se cierra")
            AuthManager.signOut()
            return Outcome.SessionRejected
        }

        BackupCodesUi.offerIfNone(activity)
        navigateToApp(activity)
        return Outcome.Navigated
    }

    private suspend fun navigateToApp(activity: Activity) {
        val selected = withContext(Dispatchers.IO) {
            SensorDatabase.getInstance(activity).plantDao().getSelectedPlant()
        }
        val next = if (selected == null) {
            Intent(activity, PlantSetupActivity::class.java)
        } else {
            SectionHostActivity.intent(activity)
        }
        activity.startActivity(next)
    }
}

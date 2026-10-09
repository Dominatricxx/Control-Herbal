package com.example.controlherbal.ui.activities.auth

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.auth.BackupCodesClient
import com.example.controlherbal.common.security.LoginThrottle
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.ui.components.BackupCodesUi
import com.example.controlherbal.ui.components.CodePrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Pantalla de acceso: correo + contraseña + código TOTP (obligatorio). La autorización real la imponen las
 * reglas de Realtime Database; aquí solo se decide qué pantalla mostrar.
 *
 * Defensas de la interfaz (complementan, no sustituyen, las del servidor):
 *  - Bloqueo y retroceso exponencial PERSISTENTES (sobreviven a cerrar la app), ver [LoginThrottle].
 *  - Todos los fallos se registran con una huella del correo, nunca con el correo ni la contraseña.
 *  - Mensaje de error genérico (no revela si la cuenta existe).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var pbLogin: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvForgot: TextView
    private lateinit var throttle: LoginThrottle
    private var countdownJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Evita capturas de pantalla / vista previa en "recientes" de la pantalla con credenciales.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        pbLogin = findViewById(R.id.pbLogin)
        tvError = findViewById(R.id.tvLoginError)
        tvForgot = findViewById(R.id.tvForgotPassword)
        throttle = LoginThrottle.forContext(this, LoginThrottle.GUARD_LOGIN)

        btnLogin.setOnClickListener { attemptLogin() }
        tvForgot.setOnClickListener { forgotPassword() }
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); true } else false
        }

        if (AuthManager.isSignedIn()) routeAfterLogin()
        else if (!throttle.check().allowed) showBlocked() // el bloqueo sobrevive a reiniciar la app
    }

    // ------------------------------------------------------------------ acceso

    private fun attemptLogin() {
        if (!throttle.check().allowed) { showBlocked(); return }
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_empty))
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            when (val r = AuthManager.signIn(email, password)) {
                AuthManager.SignInResult.Success -> onAuthenticated()
                is AuthManager.SignInResult.SecondFactorRequired -> { setBusy(false); promptSecondFactor(r, email, password, null) }
                AuthManager.SignInResult.Throttled -> { setBusy(false); showError(getString(R.string.login_error_throttled)) }
                AuthManager.SignInResult.Unavailable -> { setBusy(false); showError(getString(R.string.login_error_network)) }
                AuthManager.SignInResult.Failed -> onFailure(email, "password")
            }
        }
    }

    private fun onAuthenticated() {
        throttle.recordSuccess()
        etPassword.text.clear()
        routeAfterLogin()
    }

    private fun onFailure(email: String, stage: String) {
        val decision = throttle.recordFailure()
        // Auditoría: se registra cada fallo con una huella no reversible del correo.
        SecureLogger.w(
            TAG,
            "Acceso fallido etapa=$stage acct=${LoginThrottle.accountTag(email)} consecutivos=${throttle.consecutiveFailures} bloqueado=${!decision.allowed}",
        )
        setBusy(false)
        if (decision.allowed) showError(getString(R.string.login_error_generic)) else showBlocked()
    }

    // ------------------------------------------------------------------ segundo factor

    private fun promptSecondFactor(r: AuthManager.SignInResult.SecondFactorRequired, email: String, password: String, error: String?) {
        CodePrompt.show(
            this,
            CodePrompt.Config(
                title = getString(R.string.mfa_prompt_title),
                message = error ?: getString(R.string.mfa_prompt_message),
                hint = getString(R.string.mfa_code_hint),
                numeric = true,
                maxLength = 6,
                positive = getString(R.string.mfa_verify),
                neutral = getString(R.string.mfa_use_backup),
            ),
            onSubmit = { code -> verifyCode(r, email, password, code) },
            onNeutral = { promptBackupCode(email, password) },
            onCancel = { setBusy(false) },
        )
    }

    private fun verifyCode(r: AuthManager.SignInResult.SecondFactorRequired, email: String, password: String, code: String) {
        if (!throttle.check().allowed) { showBlocked(); return }
        setBusy(true)
        lifecycleScope.launch {
            if (AuthManager.completeSecondFactor(r.resolver, r.enrollmentId, code)) {
                onAuthenticated()
            } else {
                val d = throttle.recordFailure()
                SecureLogger.w(TAG, "Acceso fallido etapa=totp acct=${LoginThrottle.accountTag(email)} bloqueado=${!d.allowed}")
                setBusy(false)
                if (d.allowed) promptSecondFactor(r, email, password, getString(R.string.mfa_code_wrong)) else showBlocked()
            }
        }
    }

    private fun promptBackupCode(email: String, password: String) {
        CodePrompt.show(
            this,
            CodePrompt.Config(
                title = getString(R.string.backup_prompt_title),
                message = getString(R.string.backup_prompt_message),
                hint = "XXXXX-XXXXX",
                numeric = false,
                maxLength = 11,
                positive = getString(R.string.backup_use),
            ),
            onSubmit = { code -> redeemBackup(email, password, code) },
            onCancel = { setBusy(false) },
        )
    }

    private fun redeemBackup(email: String, password: String, code: String) {
        if (!throttle.check().allowed) { showBlocked(); return }
        setBusy(true)
        lifecycleScope.launch {
            when (val r = BackupCodesClient.redeem(email, password, code)) {
                BackupCodesClient.RedeemResult.Ok -> {
                    throttle.recordSuccess()
                    Toast.makeText(this@LoginActivity, R.string.backup_reset_done, Toast.LENGTH_LONG).show()
                    // El servidor quitó el 2FA perdido: ahora entra solo con contraseña y se obliga a enrolar uno nuevo.
                    if (AuthManager.signIn(email, password) == AuthManager.SignInResult.Success) {
                        etPassword.text.clear()
                        routeAfterLogin()
                    } else {
                        setBusy(false)
                        showError(getString(R.string.login_error_generic))
                    }
                }
                BackupCodesClient.RedeemResult.Wrong -> onFailure(email, "backup")
                is BackupCodesClient.RedeemResult.Throttled -> {
                    setBusy(false)
                    val minutes = ((r.retryAfterSeconds + 59) / 60).coerceAtLeast(1)
                    showError(getString(R.string.backup_throttled, minutes))
                }
                BackupCodesClient.RedeemResult.Unavailable -> { setBusy(false); showError(getString(R.string.login_error_network)) }
            }
        }
    }

    // ------------------------------------------------------------------ recuperación de contraseña

    private fun forgotPassword() {
        val guard = LoginThrottle.forContext(this, LoginThrottle.GUARD_RESET)
        if (!guard.check().allowed) {
            Toast.makeText(this, R.string.reset_wait, Toast.LENGTH_LONG).show()
            return
        }
        val email = etEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(getString(R.string.reset_need_email))
            return
        }
        guard.recordFailure() // cada solicitud cuenta: a partir de la 3.ª seguida hay espera creciente
        SecureLogger.w(TAG, "Solicitud de restablecimiento acct=${LoginThrottle.accountTag(email)}")
        lifecycleScope.launch {
            AuthManager.sendPasswordReset(email)
            // Mismo mensaje exista o no la cuenta.
            Toast.makeText(this@LoginActivity, R.string.reset_sent, Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------ navegación y UI

    private fun routeAfterLogin() {
        if (AuthManager.needsMfaEnrollment()) {
            startActivity(Intent(this, MfaEnrollActivity::class.java))
            finish()
            return
        }
        val db = SensorDatabase.getInstance(this)
        lifecycleScope.launch {
            if (!AuthManager.sessionHasTotpClaim()) {
                // Sin el segundo factor en el token, las reglas del servidor denegarían todo: se explica y se sale.
                SecureLogger.w(TAG, "Sesión sin segundo factor en el token; se cierra la sesión")
                AuthManager.signOut()
                setBusy(false)
                showError(getString(R.string.mfa_session_without_factor))
                return@launch
            }
            BackupCodesUi.offerIfNone(this@LoginActivity)
            val selected = withContext(Dispatchers.IO) { db.plantDao().getSelectedPlant() }
            val next = if (selected == null) PlantSetupActivity::class.java else MainActivity::class.java
            startActivity(Intent(this@LoginActivity, next))
            finish()
        }
    }

    private fun showBlocked() {
        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            btnLogin.isEnabled = false
            pbLogin.visibility = View.GONE
            while (true) {
                val d = throttle.check()
                if (d.allowed) break
                val res = if (d.reason == LoginThrottle.Reason.LOCKED) R.string.login_locked else R.string.login_wait
                showError(getString(res, formatDuration(d.retryAfterMs)))
                delay(1000)
            }
            tvError.visibility = View.GONE
            btnLogin.isEnabled = true
        }
    }

    private fun formatDuration(ms: Long): String {
        val s = (ms + 999) / 1000
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
    }

    private fun setBusy(busy: Boolean) {
        btnLogin.isEnabled = !busy
        pbLogin.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) tvError.visibility = View.GONE
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private companion object {
        const val TAG = "LoginActivity"
    }
}

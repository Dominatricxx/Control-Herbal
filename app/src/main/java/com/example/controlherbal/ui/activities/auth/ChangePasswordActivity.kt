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
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.security.LoginThrottle
import com.example.controlherbal.common.security.PasswordPolicy
import com.example.controlherbal.common.security.PasswordPolicy.Rule
import com.example.controlherbal.common.security.PwnedPasswords
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.sync.SensorForegroundService
import com.example.controlherbal.ui.components.CodePrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cambio de contraseña con medidor de fortaleza en tiempo real (zxcvbn + reglas), comprobación contra
 * HaveIBeenPwned (k-anonimato) y reautenticación (con segundo factor) antes de aplicar el cambio.
 * El servidor vuelve a imponer longitud y clases de caracteres (política de contraseñas de Firebase Auth).
 */
class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var etCurrent: EditText
    private lateinit var etNew: EditText
    private lateinit var etConfirm: EditText
    private lateinit var pbStrength: ProgressBar
    private lateinit var tvStrength: TextView
    private lateinit var tvChecklist: TextView
    private lateinit var tvError: TextView
    private lateinit var btnChange: Button
    private lateinit var pbBusy: ProgressBar
    private lateinit var guard: LoginThrottle

    @Volatile private var policy: PasswordPolicy? = null
    private var evalJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (!AuthManager.isSignedIn()) { finish(); return }
        setContentView(R.layout.activity_change_password)

        etCurrent = findViewById(R.id.etCurrentPassword)
        etNew = findViewById(R.id.etNewPassword)
        etConfirm = findViewById(R.id.etConfirmPassword)
        pbStrength = findViewById(R.id.pbStrength)
        tvStrength = findViewById(R.id.tvStrengthLabel)
        tvChecklist = findViewById(R.id.tvChecklist)
        tvError = findViewById(R.id.tvChangeError)
        btnChange = findViewById(R.id.btnChangePassword)
        pbBusy = findViewById(R.id.pbChange)
        guard = LoginThrottle.forContext(this, LoginThrottle.GUARD_PWCHANGE)

        // La carga de zxcvbn y de la lista de contraseñas comunes va fuera del hilo principal.
        lifecycleScope.launch(Dispatchers.Default) {
            val text = assets.open("common_passwords.txt").bufferedReader().use { it.readText() }
            PasswordPolicy.warmUp()
            policy = PasswordPolicy(PasswordPolicy.parseCores(text))
            withContext(Dispatchers.Main) { scheduleEvaluation(0) }
        }

        etNew.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = scheduleEvaluation(120)
        })
        btnChange.setOnClickListener { submit() }
    }

    private fun userInputs(): List<String> = listOfNotNull(AuthManager.email())

    /** Análisis en tiempo real con un pequeño retardo para no recalcular en cada pulsación. */
    private fun scheduleEvaluation(delayMs: Long) {
        evalJob?.cancel()
        evalJob = lifecycleScope.launch {
            delay(delayMs)
            val pw = etNew.text.toString()
            val p = policy
            if (pw.isEmpty() || p == null) { renderEmpty(); return@launch }
            val res = withContext(Dispatchers.Default) { p.evaluate(pw, userInputs()) }
            render(res)
        }
    }

    private fun renderEmpty() {
        pbStrength.progress = 0
        tvStrength.text = ""
        tvChecklist.text = checklist(emptySet(), allUnmet = true)
    }

    private fun render(res: PasswordPolicy.Result) {
        val level = res.level.coerceIn(0, 4)
        val colors = listOf("#C62828", "#EF6C00", "#F9A825", "#7CB342", "#2E7D32").map { Color.parseColor(it) }
        pbStrength.progress = (level + 1) * 20
        pbStrength.progressTintList = ColorStateList.valueOf(colors[level])
        tvStrength.text = resources.getStringArray(R.array.pw_levels)[level]
        tvStrength.setTextColor(colors[level])
        tvChecklist.text = checklist(res.failures, allUnmet = false)
    }

    private fun checklist(failures: Set<Rule>, allUnmet: Boolean): String {
        fun line(rule: Rule, text: Int): String {
            val ok = !allUnmet && rule !in failures
            return (if (ok) "✓ " else "✗ ") + getString(text)
        }
        val sb = StringBuilder()
        sb.appendLine(line(Rule.LENGTH, R.string.pw_rule_length))
        sb.appendLine(line(Rule.UPPER, R.string.pw_rule_upper))
        sb.appendLine(line(Rule.LOWER, R.string.pw_rule_lower))
        sb.appendLine(line(Rule.DIGIT, R.string.pw_rule_digit))
        sb.appendLine(line(Rule.SYMBOL, R.string.pw_rule_symbol))
        if (!allUnmet) {
            if (Rule.MAX_LENGTH in failures) sb.appendLine("✗ " + getString(R.string.pw_rule_max))
            if (Rule.PERSONAL in failures) sb.appendLine("✗ " + getString(R.string.pw_rule_personal))
            if (Rule.REPEAT in failures) sb.appendLine("✗ " + getString(R.string.pw_rule_repeat))
            if (Rule.SEQUENCE in failures) sb.appendLine("✗ " + getString(R.string.pw_rule_sequence))
            if (Rule.COMMON in failures) sb.appendLine("✗ " + getString(R.string.pw_rule_common))
            if (Rule.WEAK in failures && failures.none { it != Rule.WEAK }) sb.appendLine("✗ " + getString(R.string.pw_rule_weak))
        }
        return sb.toString().trimEnd()
    }

    private fun submit() {
        if (!guard.check().allowed) { showError(getString(R.string.login_error_throttled)); return }
        val current = etCurrent.text.toString()
        val new = etNew.text.toString()
        when {
            current.isEmpty() -> { showError(getString(R.string.pw_need_current)); return }
            new != etConfirm.text.toString() -> { showError(getString(R.string.pw_mismatch)); return }
            new == current -> { showError(getString(R.string.pw_same_as_old)); return }
        }
        val p = policy
        if (p == null) { showError(getString(R.string.pw_loading)); return }
        setBusy(true)
        lifecycleScope.launch {
            val res = withContext(Dispatchers.Default) { p.evaluate(new, userInputs()) }
            if (!res.ok) { render(res); showError(getString(R.string.pw_policy_fail)); setBusy(false); return@launch }

            val pwned = PwnedPasswords.count(new)
            if (pwned != null && pwned > 0) { showError(getString(R.string.pw_pwned)); setBusy(false); return@launch }
            if (pwned == null) SecureLogger.w(TAG, "HaveIBeenPwned no disponible; se aplican solo las reglas locales")

            when (val r = AuthManager.reauthenticate(current)) {
                AuthManager.ReauthResult.Success -> applyChange(new)
                is AuthManager.ReauthResult.SecondFactorRequired -> { setBusy(false); promptOtp(r, new, null) }
                AuthManager.ReauthResult.WrongPassword -> onWrong()
                AuthManager.ReauthResult.Throttled -> { setBusy(false); showError(getString(R.string.login_error_throttled)) }
                AuthManager.ReauthResult.Unavailable -> { setBusy(false); showError(getString(R.string.login_error_network)) }
            }
        }
    }

    private fun promptOtp(r: AuthManager.ReauthResult.SecondFactorRequired, new: String, error: String?) {
        CodePrompt.show(
            this,
            CodePrompt.Config(
                title = getString(R.string.mfa_prompt_title),
                message = error ?: getString(R.string.mfa_prompt_message),
                hint = getString(R.string.mfa_code_hint),
                numeric = true,
                maxLength = 6,
                positive = getString(R.string.mfa_verify),
            ),
            onSubmit = { code ->
                if (!guard.check().allowed) { showError(getString(R.string.login_error_throttled)) }
                else {
                    setBusy(true)
                    lifecycleScope.launch {
                        if (AuthManager.completeSecondFactor(r.resolver, r.enrollmentId, code)) applyChange(new)
                        else {
                            val d = guard.recordFailure()
                            SecureLogger.w(TAG, "Cambio de contraseña: segundo factor fallido bloqueado=${!d.allowed}")
                            setBusy(false)
                            if (d.allowed) promptOtp(r, new, getString(R.string.mfa_code_wrong))
                            else showError(getString(R.string.login_error_throttled))
                        }
                    }
                }
            },
            onCancel = { setBusy(false) },
        )
    }

    private fun onWrong() {
        val d = guard.recordFailure()
        SecureLogger.w(TAG, "Cambio de contraseña: contraseña actual incorrecta bloqueado=${!d.allowed}")
        setBusy(false)
        showError(getString(if (d.allowed) R.string.pw_current_wrong else R.string.login_error_throttled))
    }

    private suspend fun applyChange(new: String) {
        if (AuthManager.updatePassword(new)) {
            guard.recordSuccess()
            Toast.makeText(this, R.string.pw_changed, Toast.LENGTH_LONG).show()
            // Sesión limpia con la contraseña nueva.
            stopService(Intent(this, SensorForegroundService::class.java))
            AuthManager.signOut()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        } else {
            setBusy(false)
            showError(getString(R.string.pw_server_rejected))
        }
    }

    private fun setBusy(busy: Boolean) {
        btnChange.isEnabled = !busy
        pbBusy.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) tvError.visibility = View.GONE
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private companion object {
        const val TAG = "ChangePassword"
    }
}

package com.example.controlherbal.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.AuthManager
import com.example.controlherbal.data.database.SensorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de acceso. Las reglas de Realtime Database solo atienden a cuentas con rol asignado,
 * por lo que sin sesión la app no puede leer sensores ni enviar órdenes.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var pbLogin: ProgressBar
    private lateinit var tvError: TextView

    private var failedAttempts = 0

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

        btnLogin.setOnClickListener { attemptLogin() }
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); true } else false
        }

        if (AuthManager.isSignedIn()) routeAfterLogin()
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_empty))
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            val ok = AuthManager.signIn(email, password)
            if (ok) {
                etPassword.text.clear()
                routeAfterLogin()
            } else {
                failedAttempts++
                showError(getString(R.string.login_error_generic))
                // Retroceso exponencial en el cliente (2, 4, 8... hasta 60 s); Firebase aplica además sus propios límites.
                val backoffMs = (1000L shl failedAttempts.coerceAtMost(6)).coerceAtMost(60_000L)
                delay(backoffMs)
                setBusy(false)
            }
        }
    }

    private fun routeAfterLogin() {
        val db = SensorDatabase.getInstance(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val selected = db.plantDao().getSelectedPlant()
            withContext(Dispatchers.Main) {
                val next = if (selected == null) PlantSetupActivity::class.java else MainActivity::class.java
                startActivity(Intent(this@LoginActivity, next))
                finish()
            }
        }
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
}

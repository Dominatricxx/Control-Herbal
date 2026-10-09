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
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.security.LoginThrottle
import com.example.controlherbal.common.security.SecureLogger
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enrolamiento OBLIGATORIO del segundo factor (TOTP). No se puede omitir: salir cierra la sesión.
 * Pasos: verificar correo -> escanear/abrir en la app autenticadora -> confirmar con un código.
 * Al terminar se cierra la sesión y se pide entrar de nuevo con el código (así queda probado que funciona
 * y el token de la nueva sesión lleva el segundo factor que exigen las reglas del servidor).
 */
class MfaEnrollActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var panelVerify: View
    private lateinit var panelEnroll: View
    private lateinit var ivQr: ImageView
    private lateinit var tvKey: TextView
    private lateinit var etCode: EditText
    private lateinit var btnConfirm: Button
    private lateinit var pb: ProgressBar
    private lateinit var throttle: LoginThrottle
    private var enrollment: AuthManager.TotpEnrollment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_mfa_enroll)

        tvStatus = findViewById(R.id.tvMfaStatus)
        panelVerify = findViewById(R.id.panelVerifyEmail)
        panelEnroll = findViewById(R.id.panelEnroll)
        ivQr = findViewById(R.id.ivQr)
        tvKey = findViewById(R.id.tvSharedKey)
        etCode = findViewById(R.id.etMfaCode)
        btnConfirm = findViewById(R.id.btnMfaConfirm)
        pb = findViewById(R.id.pbMfa)
        throttle = LoginThrottle.forContext(this, LoginThrottle.GUARD_MFA)

        findViewById<Button>(R.id.btnSendVerification).setOnClickListener { sendVerification() }
        findViewById<Button>(R.id.btnEmailVerified).setOnClickListener { checkVerified() }
        findViewById<Button>(R.id.btnOpenAuthenticator).setOnClickListener { openAuthenticator() }
        findViewById<Button>(R.id.btnMfaCancel).setOnClickListener { signOutToLogin() }
        btnConfirm.setOnClickListener { confirm() }
        // Atrás = salir: no hay forma de usar la app sin segundo factor.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = signOutToLogin()
        })

        lifecycleScope.launch {
            setBusy(true)
            AuthManager.refreshUser()
            setBusy(false)
            proceed()
        }
    }

    private fun proceed() {
        when {
            !AuthManager.isSignedIn() -> signOutToLogin()
            // Ya tiene 2FA (p. ej. caché antigua): vuelve al flujo normal de acceso.
            !AuthManager.needsMfaEnrollment() -> {
                startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
            !AuthManager.isEmailVerified() -> {
                panelVerify.visibility = View.VISIBLE
                panelEnroll.visibility = View.GONE
                tvStatus.setText(R.string.mfa_need_verified_email)
            }
            else -> startEnrollment()
        }
    }

    private fun sendVerification() {
        lifecycleScope.launch {
            setBusy(true)
            val ok = AuthManager.sendEmailVerification()
            setBusy(false)
            Toast.makeText(this@MfaEnrollActivity, if (ok) R.string.mfa_verification_sent else R.string.mfa_error_generic, Toast.LENGTH_LONG).show()
        }
    }

    private fun checkVerified() {
        lifecycleScope.launch {
            setBusy(true)
            AuthManager.refreshUser()
            setBusy(false)
            if (AuthManager.isEmailVerified()) proceed()
            else Toast.makeText(this@MfaEnrollActivity, R.string.mfa_email_not_verified_yet, Toast.LENGTH_LONG).show()
        }
    }

    private fun startEnrollment() {
        panelVerify.visibility = View.GONE
        lifecycleScope.launch {
            setBusy(true)
            val r = AuthManager.beginTotpEnrollment()
            val e = r.getOrNull()
            if (e == null) {
                setBusy(false)
                if (r.exceptionOrNull() is FirebaseAuthRecentLoginRequiredException) {
                    Toast.makeText(this@MfaEnrollActivity, R.string.mfa_relogin, Toast.LENGTH_LONG).show()
                    signOutToLogin()
                } else {
                    SecureLogger.w(TAG, "No se pudo iniciar el enrolamiento: ${r.exceptionOrNull()?.javaClass?.simpleName}")
                    tvStatus.setText(R.string.mfa_error_generic)
                }
                return@launch
            }
            enrollment = e
            tvKey.text = e.sharedKey.chunked(4).joinToString(" ")
            val qr = withContext(Dispatchers.Default) { runCatching { qrBitmap(e.otpAuthUrl, 640) }.getOrNull() }
            if (qr != null) ivQr.setImageBitmap(qr)
            tvStatus.setText(R.string.mfa_scan_instructions)
            panelEnroll.visibility = View.VISIBLE
            setBusy(false)
        }
    }

    private fun openAuthenticator() {
        val e = enrollment ?: return
        try {
            e.secret.openInOtpApp(e.otpAuthUrl)
        } catch (ex: Exception) {
            Toast.makeText(this, R.string.mfa_no_authenticator_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirm() {
        val e = enrollment ?: return
        if (!throttle.check().allowed) {
            Toast.makeText(this, R.string.login_error_throttled, Toast.LENGTH_LONG).show()
            return
        }
        val code = etCode.text.toString().trim()
        setBusy(true)
        lifecycleScope.launch {
            val ok = AuthManager.finishTotpEnrollment(e.secret, code)
            if (ok) {
                throttle.recordSuccess()
                Toast.makeText(this@MfaEnrollActivity, R.string.mfa_enabled, Toast.LENGTH_LONG).show()
                signOutToLogin()
            } else {
                throttle.recordFailure()
                SecureLogger.w(TAG, "Código de enrolamiento rechazado")
                setBusy(false)
                etCode.error = getString(R.string.mfa_code_wrong)
            }
        }
    }

    private fun signOutToLogin() {
        AuthManager.signOut()
        startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun setBusy(busy: Boolean) {
        pb.visibility = if (busy) View.VISIBLE else View.GONE
        btnConfirm.isEnabled = !busy
    }

    private fun qrBitmap(content: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { i -> if (m.get(i % size, i / size)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val TAG = "MfaEnroll"
    }
}

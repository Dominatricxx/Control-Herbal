package com.example.controlherbal.ui.activities.main

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.security.LoginThrottle
import com.example.controlherbal.common.security.PasswordPolicy
import com.example.controlherbal.common.security.PwnedPasswords
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.ui.activities.main.MainActivity
import com.example.controlherbal.ui.activities.plant.PlantSetupActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SplashActivity: Pantalla de bienvenida con video animado de fondo y Glassmorphism Login flotante.
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
    }

    private lateinit var videoView: VideoView
    private lateinit var webViewGlassLogin: WebView
    private lateinit var tvAppTitle: TextView
    private lateinit var tvTapToContinue: TextView
    private lateinit var throttle: LoginThrottle
    private var isLoginVisible = false
    private var hasNavigated = false

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    lifecycleScope.launch {
                        val res = AuthManager.signInWithCredential(credential)
                        if (res is AuthManager.SignInResult.Success) {
                            routeAfterLogin()
                        } else {
                            notifyWebLoginError("No se pudo iniciar sesión con Google.", false)
                        }
                    }
                } else {
                    notifyWebLoginError("Error obteniendo token de Google.", false)
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error Google Sign-In: ${e.message}")
                notifyWebLoginError("Falló la autenticación con Google.", false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Renderizado pantalla completa (Edge-to-Edge) sin barras negras
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContentView(R.layout.activity_splash)

        videoView = findViewById(R.id.videoViewBackground)
        webViewGlassLogin = findViewById(R.id.webViewGlassLogin)
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvTapToContinue = findViewById(R.id.tvTapToContinue)
        throttle = LoginThrottle.forContext(this, LoginThrottle.GUARD_LOGIN)

        // Configurar video animado de fondo recortado en CenterCrop para cubrir 100% de la pantalla
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.fondo_animado_ch}")
        videoView.setVideoURI(videoUri)
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0f, 0f)

            val videoWidth = mediaPlayer.videoWidth.toFloat()
            val videoHeight = mediaPlayer.videoHeight.toFloat()
            if (videoWidth > 0 && videoHeight > 0) {
                videoView.post {
                    val screenWidth = videoView.width.toFloat()
                    val screenHeight = videoView.height.toFloat()
                    if (screenWidth > 0 && screenHeight > 0) {
                        val videoAspect = videoWidth / videoHeight
                        val screenAspect = screenWidth / screenHeight
                        val params = videoView.layoutParams
                        if (screenAspect > videoAspect) {
                            params.width = screenWidth.toInt()
                            params.height = (screenWidth / videoAspect).toInt()
                        } else {
                            params.width = (screenHeight * videoAspect).toInt()
                            params.height = screenHeight.toInt()
                        }
                        videoView.layoutParams = params
                    }
                }
            }
            videoView.start()
        }

        // Tocar la pantalla despliega suavemente la ventana emergente de Glass Login
        val revealLoginListener = View.OnClickListener {
            if (!isLoginVisible) {
                showGlassLoginWindow()
            }
        }
        findViewById<View>(R.id.splashRoot).setOnClickListener(revealLoginListener)
        videoView.setOnClickListener(revealLoginListener)

        setupGlassLoginWebView()
    }

    private fun setupGlassLoginWebView() {
        webViewGlassLogin.apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(AndroidAuthBridge(), "AndroidAuthBridge")
            loadUrl("file:///android_asset/glass-login.html")
        }
    }

    private fun showGlassLoginWindow() {
        isLoginVisible = true
        tvTapToContinue.animate().alpha(0f).setDuration(300).withEndAction {
            tvTapToContinue.visibility = View.GONE
        }.start()

        // Aplicar desenfoque de hardware nativo al video de fondo (RenderEffect) para efecto cristal profundo
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            videoView.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(32f, 32f, android.graphics.Shader.TileMode.CLAMP)
            )
        }

        webViewGlassLogin.apply {
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(500).start()
        }
    }

    private fun notifyWebLoginError(message: String, showForgot: Boolean) {
        runOnUiThread {
            val safeMsg = message.replace("'", "\\'")
            webViewGlassLogin.evaluateJavascript("showLoginError('$safeMsg', $showForgot)", null)
        }
    }

    private fun routeAfterLogin() {
        if (hasNavigated) return
        hasNavigated = true

        val databaseLocal = SensorDatabase.getInstance(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val selectedPlant = databaseLocal.plantDao().getSelectedPlant()
            withContext(Dispatchers.Main) {
                val nextIntent = if (selectedPlant == null) {
                    Intent(this@SplashActivity, PlantSetupActivity::class.java)
                } else {
                    Intent(this@SplashActivity, MainActivity::class.java)
                }
                startActivity(nextIntent)
                finish()
            }
        }
    }

    inner class AndroidAuthBridge {

        @JavascriptInterface
        fun login(email: String, pass: String) {
            lifecycleScope.launch {
                val decision = throttle.check()
                if (!decision.allowed) {
                    notifyWebLoginError("Demasiados intentos. Espera unos minutos.", false)
                    return@launch
                }

                when (AuthManager.signIn(email, pass)) {
                    AuthManager.SignInResult.Success -> {
                        throttle.recordSuccess()
                        routeAfterLogin()
                    }
                    else -> {
                        val d = throttle.recordFailure()
                        notifyWebLoginError("Credenciales incorrectas.", throttle.consecutiveFailures >= 3)
                    }
                }
            }
        }

        @JavascriptInterface
        fun register(name: String, email: String, pass: String, confirm: String) {
            lifecycleScope.launch {
                if (pass != confirm) {
                    notifyWebLoginError("Las contraseñas no coinciden.", false)
                    return@launch
                }
                val commonCores = try {
                    assets.open("common_passwords.txt").bufferedReader().useLines { lines ->
                        lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                    }
                } catch (e: Exception) {
                    emptySet()
                }
                val policy = PasswordPolicy(commonCores)
                val eval = policy.evaluate(pass, listOf(name, email))
                if (!eval.ok) {
                    notifyWebLoginError("La contraseña debe tener al menos 12 caracteres, mayúscula, número y símbolo.", false)
                    return@launch
                }

                val pwnedCount = PwnedPasswords.count(pass) ?: 0
                if (pwnedCount > 0) {
                    notifyWebLoginError("Esa contraseña ha sido filtrada en internet. Elige otra.", false)
                    return@launch
                }

                when (AuthManager.signUp(email, pass)) {
                    AuthManager.SignInResult.Success -> routeAfterLogin()
                    else -> notifyWebLoginError("No se pudo registrar la cuenta.", false)
                }
            }
        }

        @JavascriptInterface
        fun loginWithGoogle() {
            runOnUiThread {
                try {
                    val clientId = try { getString(R.string.default_web_client_id) } catch (e: Exception) { "YOUR_WEB_CLIENT_ID" }
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(clientId)
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(this@SplashActivity, gso)
                    googleSignInLauncher.launch(client.signInIntent)
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Error iniciando Google Sign-In: ${e.message}")
                    Toast.makeText(this@SplashActivity, "Inicia sesión con Google desde la consola de Google Play", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun forgotPassword(email: String) {
            lifecycleScope.launch {
                if (email.isNotBlank()) {
                    AuthManager.sendPasswordReset(email)
                    Toast.makeText(this@SplashActivity, "Si la cuenta existe, se envió el correo de restablecimiento.", Toast.LENGTH_SHORT).show()
                } else {
                    notifyWebLoginError("Ingresa tu correo en el campo correspondiente.", false)
                }
            }
        }

        @JavascriptInterface
        fun bypassLogin() {
            runOnUiThread {
                Toast.makeText(this@SplashActivity, "Modo Pruebas Activado ⚡", Toast.LENGTH_SHORT).show()
                routeAfterLogin()
            }
        }
    }
}

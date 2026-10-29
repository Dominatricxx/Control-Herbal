package com.example.controlherbal.ui.activities.main

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.BuildConfig
import com.example.controlherbal.R
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.auth.BackupCodesClient
import com.example.controlherbal.common.auth.PostAuthRouter
import com.example.controlherbal.common.security.LoginThrottle
import com.example.controlherbal.common.security.PasswordPolicy
import com.example.controlherbal.common.security.PwnedPasswords
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.repository.UserRepositoryImpl
import com.example.controlherbal.ui.components.CodePrompt
import com.example.controlherbal.ui.helper.ParallaxVideoHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Bienvenida con acceso de correo/contraseña, registro y Google; Firebase valida las credenciales.
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val ASSET_LOGIN = "file:///android_asset/glass-login.html"
    }

    private lateinit var videoView: VideoView
    private lateinit var webViewGlassLogin: WebView
    private lateinit var tvTapToContinue: TextView
    private lateinit var throttle: LoginThrottle
    private lateinit var parallaxHelper: ParallaxVideoHelper
    private var isLoginVisible = false
    private var hasNavigated = false
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleGoogleSignInResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        setContentView(R.layout.activity_splash)

        videoView = findViewById(R.id.videoViewBackground)
        parallaxHelper = ParallaxVideoHelper(getSystemService(SENSOR_SERVICE) as SensorManager, videoView)
        webViewGlassLogin = findViewById(R.id.webViewGlassLogin)
        tvTapToContinue = findViewById(R.id.tvTapToContinue)
        throttle = LoginThrottle.forContext(this, LoginThrottle.GUARD_LOGIN)

        setupBackgroundVideo()
        val reveal = View.OnClickListener { if (!isLoginVisible) showGlassLoginWindow() }
        findViewById<View>(R.id.splashRoot).setOnClickListener(reveal)
        videoView.setOnClickListener(reveal)
        setupGlassLoginWebView()
    }

    private fun setupBackgroundVideo() {
        videoView.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.fondo_animado_ch}"))
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            val vw = mp.videoWidth.toFloat()
            val vh = mp.videoHeight.toFloat()
            if (vw > 0 && vh > 0) {
                videoView.post {
                    val sw = videoView.width.toFloat()
                    val sh = videoView.height.toFloat()
                    if (sw > 0 && sh > 0) {
                        val videoAspect = vw / vh
                        val screenAspect = sw / sh
                        val params = videoView.layoutParams
                        if (screenAspect > videoAspect) {
                            params.width = sw.toInt()
                            params.height = (sw / videoAspect).toInt()
                        } else {
                            params.width = (sh * videoAspect).toInt()
                            params.height = sh.toInt()
                        }
                        videoView.layoutParams = params
                    }
                }
            }
            videoView.start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGlassLoginWebView() {
        webViewGlassLogin.apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = true
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url?.toString().orEmpty()
                    return !url.startsWith("file:///android_asset/")
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    !url.startsWith("file:///android_asset/")
            }
            addJavascriptInterface(AndroidAuthBridge(), "AndroidAuthBridge")
            loadUrl(ASSET_LOGIN)
        }
    }

    private fun showGlassLoginWindow() {
        isLoginVisible = true
        tvTapToContinue.animate().alpha(0f).setDuration(300).withEndAction {
            tvTapToContinue.visibility = View.GONE
        }.start()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            videoView.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(32f, 32f, android.graphics.Shader.TileMode.CLAMP),
            )
        }
        webViewGlassLogin.apply {
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(500).start()
        }
    }

    private fun notifyWebLoginError(message: String, showForgot: Boolean) {
        runOnUiThread {
            val payload = JSONObject()
                .put("message", message)
                .put("showForgot", showForgot)
                .toString()
            webViewGlassLogin.evaluateJavascript("showLoginErrorPayload($payload)", null)
        }
    }

    private fun finishWithRoute(
        pendingProfile: PostAuthRouter.PendingProfile? = null,
    ) {
        if (hasNavigated) return
        lifecycleScope.launch {
            when (val outcome = PostAuthRouter.route(this@SplashActivity, pendingProfile)) {
                PostAuthRouter.Outcome.Navigated -> {
                    hasNavigated = true
                    finish()
                }
                PostAuthRouter.Outcome.SessionRejected -> {
                    notifyWebLoginError(getString(R.string.mfa_session_without_factor), false)
                }
            }
        }
    }

    private fun promptSecondFactor(
        r: AuthManager.SignInResult.SecondFactorRequired,
        email: String,
        password: String,
        error: String?,
        allowBackupCode: Boolean = true,
    ) {
        CodePrompt.show(
            this,
            CodePrompt.Config(
                title = getString(R.string.mfa_prompt_title),
                message = error ?: getString(R.string.mfa_prompt_message),
                hint = getString(R.string.mfa_code_hint),
                numeric = true,
                maxLength = 6,
                positive = getString(R.string.mfa_verify),
                neutral = if (allowBackupCode) getString(R.string.mfa_use_backup) else null,
            ),
            onSubmit = { code -> verifyTotp(r, email, password, code) },
            onNeutral = if (allowBackupCode) ({ promptBackupCode(email, password) }) else null,
            onCancel = { },
        )
    }

    private fun verifyTotp(
        r: AuthManager.SignInResult.SecondFactorRequired,
        email: String,
        password: String,
        code: String,
    ) {
        if (!throttle.check().allowed) {
            notifyWebLoginError("Demasiados intentos. Espera unos minutos.", false)
            return
        }
        lifecycleScope.launch {
            if (AuthManager.completeSecondFactor(r.resolver, r.enrollmentId, code)) {
                throttle.recordSuccess()
                if (!persistLocalProfileFromSession()) showProfileSyncWarning()
                finishWithRoute()
            } else {
                val d = throttle.recordFailure()
                SecureLogger.w(TAG, "TOTP rechazado acct=${LoginThrottle.accountTag(email)} bloqueado=${!d.allowed}")
                if (d.allowed) {
                    promptSecondFactor(r, email, password, getString(R.string.mfa_code_wrong))
                } else {
                    notifyWebLoginError("Demasiados intentos. Espera unos minutos.", false)
                }
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
            onSubmit = { code ->
                lifecycleScope.launch {
                    when (val r = BackupCodesClient.redeem(email, password, code)) {
                        BackupCodesClient.RedeemResult.Ok -> {
                            throttle.recordSuccess()
                            Toast.makeText(this@SplashActivity, R.string.backup_reset_done, Toast.LENGTH_LONG).show()
                            if (AuthManager.signIn(email, password) == AuthManager.SignInResult.Success) {
                                finishWithRoute()
                            } else {
                                notifyWebLoginError(getString(R.string.login_error_generic), false)
                            }
                        }
                        BackupCodesClient.RedeemResult.Wrong -> {
                            throttle.recordFailure()
                            notifyWebLoginError(getString(R.string.login_error_generic), true)
                        }
                        is BackupCodesClient.RedeemResult.Throttled ->
                            notifyWebLoginError(getString(R.string.backup_throttled, ((r.retryAfterSeconds + 59) / 60).coerceAtLeast(1)), false)
                        BackupCodesClient.RedeemResult.Unavailable ->
                            notifyWebLoginError(getString(R.string.login_error_network), false)
                    }
                }
            },
            onCancel = { },
        )
    }

    /** Perfil local sin contraseña: Firebase Auth es la única fuente de credenciales. */
    private suspend fun persistLocalProfileFromSession(
        firstName: String? = null,
        paternalSurname: String? = null,
        maternalSurname: String? = null,
        fallbackDisplayName: String? = null,
    ): Boolean {
        if (!AuthManager.isSignedIn() || !AuthManager.isEmailVerified()) return true
        val db = SensorDatabase.getInstance(this)
        return UserRepositoryImpl(this, db.userDao()).saveVerifiedProfile(
            firstName = firstName,
            paternalSurname = paternalSurname,
            maternalSurname = maternalSurname,
            fallbackDisplayName = fallbackDisplayName,
        ).cloudSynced
    }

    private fun showProfileSyncWarning() {
        Toast.makeText(this, R.string.auth_profile_sync_failed, Toast.LENGTH_LONG).show()
    }

    private fun googleWebClientId(): String? {
        val id = resources.getIdentifier("default_web_client_id", "string", packageName)
        val configuredId = if (id != 0) getString(id) else BuildConfig.GOOGLE_WEB_CLIENT_ID
        return configuredId.takeIf { it.endsWith(".apps.googleusercontent.com") }
    }

    private fun startGoogleSignIn() {
        val clientId = googleWebClientId()
        if (clientId == null) {
            notifyWebLoginError(getString(R.string.auth_google_not_configured), false)
            return
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        googleSignInLauncher.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    private fun handleGoogleSignInResult(resultCode: Int, data: android.content.Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            if (resultCode != Activity.RESULT_CANCELED) {
                notifyWebLoginError(getString(R.string.auth_google_failed), false)
            }
            return
        }
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            SecureLogger.w(TAG, "Google Sign-In rechazado status=${e.statusCode}")
            notifyWebLoginError(getString(R.string.auth_google_failed), false)
            return
        }
        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            notifyWebLoginError(getString(R.string.auth_google_not_configured), false)
            return
        }
        lifecycleScope.launch {
            when (val authResult = AuthManager.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))) {
                AuthManager.SignInResult.Success -> {
                    throttle.recordSuccess()
                    if (!persistLocalProfileFromSession(
                            firstName = account.givenName,
                            paternalSurname = account.familyName,
                            fallbackDisplayName = account.displayName,
                        )
                    ) showProfileSyncWarning()
                    finishWithRoute()
                }
                is AuthManager.SignInResult.SecondFactorRequired ->
                    promptSecondFactor(authResult, account.email.orEmpty(), "", null, allowBackupCode = false)
                AuthManager.SignInResult.Throttled ->
                    notifyWebLoginError(getString(R.string.login_error_throttled), false)
                AuthManager.SignInResult.Unavailable ->
                    notifyWebLoginError(getString(R.string.login_error_network), false)
                AuthManager.SignInResult.Failed ->
                    notifyWebLoginError(getString(R.string.auth_google_failed), false)
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
                when (val r = AuthManager.signIn(email, pass)) {
                    AuthManager.SignInResult.Success -> {
                        throttle.recordSuccess()
                        if (!persistLocalProfileFromSession()) showProfileSyncWarning()
                        finishWithRoute()
                    }
                    is AuthManager.SignInResult.SecondFactorRequired ->
                        promptSecondFactor(r, email, pass, null)
                    AuthManager.SignInResult.Throttled ->
                        notifyWebLoginError(getString(R.string.login_error_throttled), false)
                    AuthManager.SignInResult.Unavailable ->
                        notifyWebLoginError(getString(R.string.login_error_network), false)
                    AuthManager.SignInResult.Failed -> {
                        throttle.recordFailure()
                        SecureLogger.w(TAG, "Login fallido acct=${LoginThrottle.accountTag(email)}")
                        notifyWebLoginError(getString(R.string.login_error_generic), throttle.consecutiveFailures >= 3)
                    }
                }
            }
        }

        @JavascriptInterface
        fun register(
            firstName: String,
            paternalSurname: String,
            maternalSurname: String,
            email: String,
            pass: String,
            confirm: String,
        ) {
            lifecycleScope.launch {
                if (!throttle.check().allowed) {
                    notifyWebLoginError(getString(R.string.login_error_throttled), false)
                    return@launch
                }
                if (firstName.length > 80 || paternalSurname.length > 80 || maternalSurname.length > 80 ||
                    email.length > 254 || pass.length > 256 || confirm.length > 256
                ) {
                    notifyWebLoginError(getString(R.string.auth_register_failed), false)
                    return@launch
                }
                val cleanFirstName = SecurityUtils.sanitizeText(firstName, 80)
                val cleanPaternalSurname = SecurityUtils.sanitizeText(paternalSurname, 80)
                val cleanMaternalSurname = SecurityUtils.sanitizeText(maternalSurname, 80)
                val cleanEmail = email.trim()
                if (cleanFirstName.length < 2) {
                    notifyWebLoginError(getString(R.string.auth_register_invalid_name), false)
                    return@launch
                }
                if (cleanPaternalSurname.length < 2) {
                    notifyWebLoginError(getString(R.string.auth_register_invalid_surname), false)
                    return@launch
                }
                if (!SecurityUtils.isValidEmailDomain(cleanEmail)) {
                    notifyWebLoginError(getString(R.string.auth_register_invalid_email), false)
                    return@launch
                }
                if (pass != confirm) {
                    notifyWebLoginError(getString(R.string.auth_register_password_mismatch), false)
                    return@launch
                }

                val passwordPolicy = withContext(Dispatchers.Default) {
                    PasswordPolicy.warmUp()
                    val common = assets.open("common_passwords.txt").bufferedReader().use { it.readText() }
                    PasswordPolicy(PasswordPolicy.parseCores(common)).evaluate(
                        pass,
                        listOf(cleanFirstName, cleanPaternalSurname, cleanMaternalSurname, cleanEmail),
                    )
                }
                if (!passwordPolicy.ok) {
                    notifyWebLoginError(getString(R.string.auth_register_password_policy), false)
                    return@launch
                }
                val pwnedCount = PwnedPasswords.count(pass)
                if (pwnedCount != null && pwnedCount > 0) {
                    notifyWebLoginError(getString(R.string.auth_register_password_pwned), false)
                    return@launch
                }
                if (pwnedCount == null) {
                    SecureLogger.w(TAG, "No se pudo consultar HaveIBeenPwned durante el registro")
                }

                val displayName = listOf(cleanFirstName, cleanPaternalSurname, cleanMaternalSurname)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                when (AuthManager.register(cleanEmail, pass, displayName)) {
                    AuthManager.RegistrationResult.Success -> {
                        throttle.recordSuccess()
                        if (!persistLocalProfileFromSession(
                                firstName = cleanFirstName,
                                paternalSurname = cleanPaternalSurname,
                                maternalSurname = cleanMaternalSurname,
                            )
                        ) showProfileSyncWarning()
                        finishWithRoute(
                            PostAuthRouter.PendingProfile(
                                cleanFirstName,
                                cleanPaternalSurname,
                                cleanMaternalSurname,
                            ),
                        )
                    }
                    AuthManager.RegistrationResult.Throttled ->
                        notifyWebLoginError(getString(R.string.login_error_throttled), false)
                    AuthManager.RegistrationResult.Unavailable ->
                        notifyWebLoginError(getString(R.string.login_error_network), false)
                    AuthManager.RegistrationResult.ProviderDisabled ->
                        notifyWebLoginError(getString(R.string.auth_register_disabled), false)
                    else -> {
                        throttle.recordFailure()
                        notifyWebLoginError(getString(R.string.auth_register_failed), false)
                    }
                }
            }
        }

        @JavascriptInterface
        fun loginWithGoogle() {
            runOnUiThread { startGoogleSignIn() }
        }

        @JavascriptInterface
        fun forgotPassword(email: String) {
            lifecycleScope.launch {
                val guard = LoginThrottle.forContext(this@SplashActivity, LoginThrottle.GUARD_RESET)
                if (!guard.check().allowed) {
                    notifyWebLoginError(getString(R.string.reset_wait), false)
                    return@launch
                }
                if (email.isBlank()) {
                    notifyWebLoginError("Ingresa tu correo en el campo correspondiente.", false)
                    return@launch
                }
                guard.recordFailure()
                AuthManager.sendPasswordReset(email)
                Toast.makeText(
                    this@SplashActivity,
                    R.string.reset_sent,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        if (::parallaxHelper.isInitialized) parallaxHelper.registerSensors()
    }

    override fun onPause() {
        super.onPause()
        if (::parallaxHelper.isInitialized) parallaxHelper.unregisterSensors()
    }
}

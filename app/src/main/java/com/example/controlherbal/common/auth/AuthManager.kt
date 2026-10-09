package com.example.controlherbal.common.auth

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.google.firebase.auth.TotpSecret
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * AuthManager: único punto de acceso a la identidad del usuario.
 *
 * Modelo de seguridad:
 *  - No hay sesión propia ni tokens en el almacenamiento de la app: Firebase Auth guarda su credencial en el
 *    almacenamiento privado de la app (excluido de copias de seguridad: allowBackup=false + data_extraction_rules)
 *    y las pantallas nunca la leen ni la registran.
 *  - La AUTORIZACIÓN no se decide aquí: las reglas de Realtime Database (firebase/database.rules.json) exigen,
 *    para el rol owner, un token con segundo factor TOTP y correo verificado. Cualquier comprobación de esta
 *    clase solo sirve para decidir qué pantalla mostrar.
 *  - Segundo factor (TOTP) obligatorio en esta app: sin él se obliga a enrolarlo antes de continuar.
 */
object AuthManager {

    private const val TAG = "AuthManager"
    private const val TOTP = "totp"
    private const val ISSUER = "Control Herbal"

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    fun isSignedIn(): Boolean = auth.currentUser != null
    fun uid(): String? = auth.currentUser?.uid
    fun email(): String? = auth.currentUser?.email

    /** ¿Hay sesión pero sin segundo factor TOTP enrolado? Entonces solo puede ir a la pantalla de enrolamiento. */
    fun needsMfaEnrollment(): Boolean {
        val user = auth.currentUser ?: return false
        return user.multiFactor.enrolledFactors.none { it.factorId == TOTP }
    }

    /**
     * ¿El token de la sesión lleva el segundo factor TOTP? Es lo que exigen las reglas del servidor al owner.
     * Si faltara, el usuario vería fallos silenciosos de permisos; así se detecta y se explica.
     */
    suspend fun sessionHasTotpClaim(): Boolean {
        val user = auth.currentUser ?: return false
        val token = user.getIdToken(false).awaitResult().getOrNull() ?: return false
        val firebase = token.claims["firebase"] as? Map<*, *>
        return firebase?.get("sign_in_second_factor") == TOTP
    }

    fun signOut() {
        auth.signOut()
    }

    // ---------------------------------------------------------------------------------- inicio de sesión

    sealed interface SignInResult {
        data object Success : SignInResult
        /** Contraseña correcta; falta el código de la app autenticadora. */
        data class SecondFactorRequired(val resolver: MultiFactorResolver, val enrollmentId: String) : SignInResult
        /** Credenciales no válidas. Deliberadamente sin distinguir "no existe" de "clave errónea". */
        data object Failed : SignInResult
        /** Firebase aplicó su propia limitación (too-many-requests). */
        data object Throttled : SignInResult
        data object Unavailable : SignInResult
    }

    suspend fun signIn(email: String, password: String): SignInResult =
        suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                val result: SignInResult = if (task.isSuccessful) {
                    SignInResult.Success
                } else {
                    when (val e = task.exception) {
                        is FirebaseAuthMultiFactorException -> {
                            val resolver = e.resolver
                            val hint = resolver.hints.firstOrNull { it.factorId == TOTP }
                            if (hint != null) SignInResult.SecondFactorRequired(resolver, hint.uid) else SignInResult.Failed
                        }
                        is FirebaseTooManyRequestsException -> SignInResult.Throttled
                        is FirebaseNetworkException -> SignInResult.Unavailable
                        else -> SignInResult.Failed
                    }
                }
                if (result is SignInResult.Failed || result is SignInResult.Throttled) {
                    SecureLogger.w(TAG, "Inicio de sesión rechazado (${result::class.simpleName})")
                }
                if (cont.isActive) cont.resume(result)
            }
        }

    /** Completa un inicio de sesión (o una reautenticación) con el código TOTP de 6 dígitos. */
    suspend fun completeSecondFactor(resolver: MultiFactorResolver, enrollmentId: String, code: String): Boolean {
        if (!code.matches(Regex("\\d{6}"))) return false
        val assertion = TotpMultiFactorGenerator.getAssertionForSignIn(enrollmentId, code)
        val ok = resolver.resolveSignIn(assertion).awaitResult().isSuccess
        if (!ok) SecureLogger.w(TAG, "Código de segundo factor rechazado")
        return ok
    }

    // ---------------------------------------------------------------------------------- recuperación

    /** Siempre se informa al usuario con el mismo mensaje, exista o no la cuenta (anti-enumeración). */
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).awaitResult()
    }

    // ---------------------------------------------------------------------------------- enrolamiento TOTP

    suspend fun refreshUser(): Boolean {
        val user = auth.currentUser ?: return false
        return user.reload().awaitResult().isSuccess
    }

    fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    suspend fun sendEmailVerification(): Boolean {
        val user = auth.currentUser ?: return false
        return user.sendEmailVerification().awaitResult().isSuccess
    }

    class TotpEnrollment(val secret: TotpSecret, val sharedKey: String, val otpAuthUrl: String)

    suspend fun beginTotpEnrollment(): Result<TotpEnrollment> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Sin sesión"))
        val session = user.multiFactor.session.awaitResult().getOrElse { return Result.failure(it) }
        val secret = TotpMultiFactorGenerator.generateSecret(session).awaitResult().getOrElse { return Result.failure(it) }
        val url = secret.generateQrCodeUrl(user.email ?: "owner", ISSUER)
        return Result.success(TotpEnrollment(secret, secret.sharedSecretKey, url))
    }

    suspend fun finishTotpEnrollment(secret: TotpSecret, code: String): Boolean {
        val user = auth.currentUser ?: return false
        if (!code.matches(Regex("\\d{6}"))) return false
        val assertion = TotpMultiFactorGenerator.getAssertionForEnrollment(secret, code)
        return user.multiFactor.enroll(assertion, "Authenticator").awaitResult().isSuccess
    }

    // ---------------------------------------------------------------------------------- cambio de contraseña

    sealed interface ReauthResult {
        data object Success : ReauthResult
        data class SecondFactorRequired(val resolver: MultiFactorResolver, val enrollmentId: String) : ReauthResult
        data object WrongPassword : ReauthResult
        data object Throttled : ReauthResult
        data object Unavailable : ReauthResult
    }

    suspend fun reauthenticate(currentPassword: String): ReauthResult {
        val user = auth.currentUser
        val email = user?.email ?: return ReauthResult.WrongPassword
        val r = user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).awaitResult()
        if (r.isSuccess) return ReauthResult.Success
        return when (val e = r.exceptionOrNull()) {
            is FirebaseAuthMultiFactorException -> {
                val hint = e.resolver.hints.firstOrNull { it.factorId == TOTP }
                if (hint != null) ReauthResult.SecondFactorRequired(e.resolver, hint.uid) else ReauthResult.WrongPassword
            }
            is FirebaseTooManyRequestsException -> ReauthResult.Throttled
            is FirebaseNetworkException -> ReauthResult.Unavailable
            else -> ReauthResult.WrongPassword
        }
    }

    /** El servidor (política de contraseñas de Firebase Auth) puede rechazarla aunque el cliente la acepte. */
    suspend fun updatePassword(newPassword: String): Boolean {
        val user = auth.currentUser ?: return false
        val ok = user.updatePassword(newPassword).awaitResult().isSuccess
        if (!ok) SecureLogger.w(TAG, "Cambio de contraseña rechazado por el servidor")
        return ok
    }

    enum class DeleteResult { SUCCESS, REQUIRES_RECENT_LOGIN, FAILED }

    /** Elimina la cuenta de Firebase Authentication de la persona (derecho de cancelación). */
    suspend fun deleteCurrentUser(): DeleteResult =
        suspendCancellableCoroutine { cont ->
            val user = auth.currentUser
            if (user == null) {
                cont.resume(DeleteResult.FAILED)
                return@suspendCancellableCoroutine
            }
            user.delete().addOnCompleteListener { task ->
                val result = when {
                    task.isSuccessful -> DeleteResult.SUCCESS
                    task.exception is FirebaseAuthRecentLoginRequiredException -> DeleteResult.REQUIRES_RECENT_LOGIN
                    else -> DeleteResult.FAILED
                }
                if (cont.isActive) cont.resume(result)
            }
        }
}

/** Convierte un Task de Play Services en un Result suspendible, sin añadir dependencias. */
internal suspend fun <T> Task<T>.awaitResult(): Result<T> = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { t ->
        if (cont.isActive) {
            val e = t.exception
            cont.resume(if (t.isSuccessful) Result.success(t.result) else Result.failure(e ?: IllegalStateException("Fallo desconocido")))
        }
    }
}

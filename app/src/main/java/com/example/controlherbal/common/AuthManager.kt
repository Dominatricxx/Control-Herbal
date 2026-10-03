package com.example.controlherbal.common

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * AuthManager: único punto de acceso a la identidad del usuario.
 *
 * Las reglas de Realtime Database (firebase/database.rules.json) solo aceptan usuarios con un
 * rol asignado en /roles/{uid}. Una sesión anónima o sin rol no puede leer ni escribir nada.
 */
object AuthManager {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    fun isSignedIn(): Boolean = auth.currentUser != null

    fun uid(): String? = auth.currentUser?.uid

    /** Devuelve éxito/fracaso sin exponer detalles que distingan "usuario inexistente" de "clave errónea". */
    suspend fun signIn(email: String, password: String): Boolean =
        suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        SecureLogger.w("AuthManager", "Inicio de sesión rechazado")
                    }
                    if (cont.isActive) cont.resume(task.isSuccessful)
                }
        }

    fun signOut() {
        auth.signOut()
    }
}

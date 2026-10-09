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

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

/**
 * Cliente de las Cloud Functions de códigos de respaldo de 2FA (firebase/functions/index.js).
 * Toda la validación, el límite de intentos y el registro están en el servidor.
 */
object BackupCodesClient {

    private const val TAG = "BackupCodes"
    private val functions: FirebaseFunctions get() = FirebaseFunctions.getInstance("us-central1")

    /** Genera 10 códigos nuevos (invalidan los anteriores). Requiere sesión con 2FA. Se muestran una sola vez. */
    suspend fun generate(): Result<List<String>> {
        val res = functions.getHttpsCallable("generateBackupCodes").call().awaitResult()
            .getOrElse { SecureLogger.w(TAG, "generate falló: ${it.javaClass.simpleName}"); return Result.failure(it) }
        val codes = ((res.data as? Map<*, *>)?.get("codes") as? List<*>)?.mapNotNull { it as? String }
        return if (codes.isNullOrEmpty()) Result.failure(IllegalStateException("Respuesta inesperada")) else Result.success(codes)
    }

    /** Cuántos códigos quedan sin usar. */
    suspend fun remaining(): Result<Int> {
        val res = functions.getHttpsCallable("backupCodesStatus").call().awaitResult()
            .getOrElse { return Result.failure(it) }
        val n = ((res.data as? Map<*, *>)?.get("remaining") as? Number)?.toInt()
        return if (n == null) Result.failure(IllegalStateException("Respuesta inesperada")) else Result.success(n)
    }

    sealed interface RedeemResult {
        /** Código aceptado: el 2FA perdido se eliminó y todas las sesiones se cerraron. */
        data object Ok : RedeemResult
        data object Wrong : RedeemResult
        data class Throttled(val retryAfterSeconds: Long) : RedeemResult
        data object Unavailable : RedeemResult
    }

    /**
     * Canjea un código de respaldo. Si el servidor exige el reto de prueba de trabajo (a partir del 3.er fallo de
     * la cuenta), responde FAILED_PRECONDITION con un reto: se resuelve aquí y se reintenta UNA vez.
     */
    suspend fun redeem(email: String, password: String, code: String): RedeemResult {
        var extra: Map<String, Any> = emptyMap()
        for (attempt in 0..1) {
            val data = hashMapOf<String, Any>("email" to email.trim(), "password" to password, "code" to code)
            data.putAll(extra)
            val r = functions.getHttpsCallable("redeemBackupCode").call(data).awaitResult()
            if (r.isSuccess) return RedeemResult.Ok

            val e = r.exceptionOrNull()
            if (attempt == 0 && e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                extra = solveChallenge(e) ?: return RedeemResult.Unavailable
                continue
            }
            return mapError(e)
        }
        return RedeemResult.Unavailable
    }

    private suspend fun solveChallenge(e: FirebaseFunctionsException): Map<String, Any>? {
        val ch = (e.details as? Map<*, *>)?.get("challenge") as? Map<*, *> ?: return null
        val salt = ch["salt"] as? String ?: return null
        val sig = ch["sig"] as? String ?: return null
        val exp = (ch["exp"] as? Number)?.toLong() ?: return null
        val bits = (ch["bits"] as? Number)?.toInt() ?: return null
        val nonce = try { ProofOfWork.solve(salt, bits) } catch (ex: IllegalArgumentException) { return null }
        return mapOf("challenge" to mapOf("salt" to salt, "exp" to exp, "bits" to bits, "sig" to sig), "nonce" to nonce)
    }

    private fun mapError(e: Throwable?): RedeemResult {
        SecureLogger.w(TAG, "Canje de código rechazado")
        if (e is FirebaseFunctionsException) {
            return when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    val secs = ((e.details as? Map<*, *>)?.get("retryAfterSeconds") as? Number)?.toLong() ?: 900L
                    RedeemResult.Throttled(secs)
                }
                FirebaseFunctionsException.Code.PERMISSION_DENIED,
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> RedeemResult.Wrong
                else -> RedeemResult.Unavailable
            }
        }
        return RedeemResult.Unavailable
    }
}

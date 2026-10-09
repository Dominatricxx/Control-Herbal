package com.example.controlherbal.common.security

import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Context

/**
 * Limitación PERSISTENTE de intentos de acceso en el dispositivo (sobrevive a cerrar la app).
 *
 *  - Retroceso exponencial desde el 3.er fallo consecutivo: 5 s, 10 s, 20 s… hasta 5 min.
 *  - Bloqueo al 5.º fallo consecutivo: 15 min, que se duplica en cada bloqueo sucesivo (tope 24 h).
 *  - Un acceso correcto lo reinicia todo.
 *
 * IMPORTANTE: es una defensa de la interfaz, no una frontera de seguridad: quien manipule el APK o borre los
 * datos de la app puede saltársela. Lo que de verdad detiene la fuerza bruta contra la cuenta es lo que impone
 * el servidor (limitación de Firebase Auth, reCAPTCHA y 2FA obligatorio; ver docs/SEGURIDAD.md).
 *
 * Mismos parámetros que firebase/functions/lib/limiter.js.
 */
class LoginThrottle(
    private val store: Store,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    interface Store {
        fun get(key: String): Long
        fun putAll(values: Map<String, Long>)
        fun clear()
    }

    enum class Reason { BACKOFF, LOCKED }

    data class Decision(val allowed: Boolean, val retryAfterMs: Long = 0L, val reason: Reason? = null)

    /** Fallos consecutivos desde el último bloqueo o acierto (para el registro de auditoría). */
    val consecutiveFailures: Long get() = store.get(FAILS)

    fun check(): Decision {
        val now = clock()
        val lockUntil = store.get(LOCK_UNTIL)
        val retryAt = store.get(RETRY_AT)
        val until = maxOf(lockUntil, retryAt)
        if (until <= now) return Decision(true)
        return Decision(false, until - now, if (lockUntil > now) Reason.LOCKED else Reason.BACKOFF)
    }

    /** Registra un fallo y devuelve si el siguiente intento está permitido. */
    fun recordFailure(): Decision {
        val now = clock()
        var fails = store.get(FAILS) + 1
        var lockouts = store.get(LOCKOUTS)
        var lockUntil = store.get(LOCK_UNTIL)
        var retryAt = 0L
        if (fails >= LOCK_AFTER) {
            lockouts += 1
            val shift = (lockouts - 1).coerceIn(0, 20).toInt()
            lockUntil = now + minOf(LOCK_MAX_MS, LOCK_BASE_MS shl shift)
            fails = 0
        } else if (fails >= BACKOFF_AFTER) {
            val shift = (fails - BACKOFF_AFTER).coerceIn(0, 20).toInt()
            retryAt = now + minOf(BACKOFF_MAX_MS, BACKOFF_BASE_MS shl shift)
        }
        store.putAll(mapOf(FAILS to fails, LOCKOUTS to lockouts, LOCK_UNTIL to lockUntil, RETRY_AT to retryAt))
        return check()
    }

    fun recordSuccess() = store.clear()

    /** Almacén en SharedPreferences privadas (excluidas de las copias de seguridad por data_extraction_rules). */
    class PrefsStore(context: Context, name: String) : Store {
        private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        override fun get(key: String): Long = prefs.getLong(key, 0L)
        override fun putAll(values: Map<String, Long>) {
            prefs.edit().apply { values.forEach { (k, v) -> putLong(k, v) } }.apply()
        }
        override fun clear() { prefs.edit().clear().apply() }
    }

    companion object {
        const val GUARD_LOGIN = "login_guard"
        const val GUARD_RESET = "reset_guard"
        const val GUARD_PWCHANGE = "pwchange_guard"
        const val GUARD_MFA = "mfa_guard"

        private const val FAILS = "fails"
        private const val LOCKOUTS = "lockouts"
        private const val LOCK_UNTIL = "lock_until"
        private const val RETRY_AT = "retry_at"

        private const val BACKOFF_AFTER = 3L
        private const val BACKOFF_BASE_MS = 5_000L
        private const val BACKOFF_MAX_MS = 5 * 60 * 1000L
        private const val LOCK_AFTER = 5L
        private const val LOCK_BASE_MS = 15 * 60 * 1000L
        private const val LOCK_MAX_MS = 24 * 60 * 60 * 1000L

        fun forContext(context: Context, name: String) = LoginThrottle(PrefsStore(context, name))

        /** Huella corta y no reversible del correo para los registros (nunca el correo en claro). */
        fun accountTag(email: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
                .take(4).joinToString("") { "%02x".format(it) }
    }
}

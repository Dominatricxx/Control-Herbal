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

import android.util.Log
import com.example.controlherbal.BuildConfig

/**
 * SecureLogger: Registro seguro de auditoría.
 * Previene la fuga de información sensible (PII, credenciales, tokens) en compilaciones de producción.
 */
object SecureLogger {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, sanitizeLog(message))
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, sanitizeLog(message))
        }
    }

    fun w(tag: String, message: String) {
        Log.w(tag, sanitizeLog(message))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, sanitizeLog(message), throwable)
        } else {
            Log.e(tag, sanitizeLog(message))
        }
    }

    private fun sanitizeLog(message: String): String {
        return message
            .replace(Regex("(?i)bearer\\s+[a-zA-Z0-9._-]+"), "Bearer [PROTECTED]")
            .replace(Regex("(?i)password\\s*=\\s*[^&]+"), "password=[PROTECTED]")
    }
}

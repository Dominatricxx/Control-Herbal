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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Comprobación en HaveIBeenPwned con k-anonimato: solo se envían los 5 primeros caracteres del SHA-1;
 * la contraseña (y su hash completo) nunca salen del dispositivo. Solo HTTPS.
 */
object PwnedPasswords {

    private const val MAX_BYTES = 2 * 1024 * 1024

    /** @return nº de apariciones conocidas (0 = no aparece), o null si no se pudo consultar. */
    suspend fun count(password: String): Int? = withContext(Dispatchers.IO) {
        val hash = sha1Upper(password)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("https://api.pwnedpasswords.com/range/${hash.substring(0, 5)}").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Add-Padding", "true")
                setRequestProperty("User-Agent", "ControlHerbal-Android")
            }
            if (conn.responseCode != 200) return@withContext null
            parseRange(readLimited(conn.inputStream), hash.substring(5))
        } catch (e: Exception) {
            SecureLogger.w("PwnedPasswords", "Consulta no disponible: ${e.javaClass.simpleName}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun sha1Upper(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }

    internal fun parseRange(body: String, suffix: String): Int {
        for (line in body.lineSequence()) {
            val parts = line.trim().split(":")
            if (parts.size == 2 && parts[0] == suffix) return parts[1].toIntOrNull() ?: 0
        }
        return 0
    }

    private fun readLimited(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        input.use { s ->
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw IllegalStateException("Respuesta demasiado grande")
                out.write(buf, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

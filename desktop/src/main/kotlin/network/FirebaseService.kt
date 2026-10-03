package network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Cliente de solo lectura de Realtime Database para el escritorio.
 *
 * Seguridad:
 *  - Autenticación con Firebase Auth (REST). Las reglas de la base solo atienden a usuarios con
 *    rol en /roles/{uid}; usa una cuenta con rol "viewer" (solo lectura).
 *  - La configuración sale de variables de entorno (no hay URL, clave ni contraseña en el código):
 *      HERBAL_DB_URL         https://<proyecto>-default-rtdb.firebaseio.com
 *      HERBAL_API_KEY        Web API key del proyecto de Firebase
 *      HERBAL_USER_EMAIL     correo de la cuenta viewer
 *      HERBAL_USER_PASSWORD  contraseña de la cuenta viewer
 *  - Solo HTTPS, con tiempos de espera y tope de tamaño de respuesta.
 *  - Se validan rango y tipo de cada valor recibido antes de usarlo.
 *
 * Nota: si activas el cumplimiento (enforcement) de App Check en Realtime Database, este cliente
 * REST de escritorio no podrá atestiguar y sería rechazado; mantén App Check en modo "monitor"
 * para la base y en modo "enforced" para Firebase AI Logic.
 */
class FirebaseService(
    private val dbBaseUrl: String = System.getenv("HERBAL_DB_URL").orEmpty(),
    private val apiKey: String = System.getenv("HERBAL_API_KEY").orEmpty(),
    private val email: String = System.getenv("HERBAL_USER_EMAIL").orEmpty(),
    private val password: String = System.getenv("HERBAL_USER_PASSWORD").orEmpty()
) {
    private val log = Logger.getLogger("ControlHerbal.FirebaseService")

    data class SensorData(val temp: Double, val hum: Double, val luz: Int, val soil: Double)

    private var idToken: String? = null
    private var refreshToken: String? = null
    private var tokenExpiresAtMs: Long = 0L

    val isConfigured: Boolean
        get() = dbBaseUrl.startsWith("https://") && apiKey.isNotBlank() && email.isNotBlank() && password.isNotBlank()

    suspend fun fetchSensorData(): SensorData? = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            log.warning("Configuración incompleta: define HERBAL_DB_URL, HERBAL_API_KEY, HERBAL_USER_EMAIL y HERBAL_USER_PASSWORD")
            return@withContext null
        }
        try {
            val token = validToken() ?: return@withContext null
            val url = URL("${dbBaseUrl.trimEnd('/')}/sensor.json?auth=${URLEncoder.encode(token, "UTF-8")}")
            val conn = open(url, "GET")
            try {
                if (conn.responseCode != 200) {
                    log.warning("Lectura rechazada (HTTP ${conn.responseCode})")
                    if (conn.responseCode == 401 || conn.responseCode == 403) invalidateToken()
                    return@withContext null
                }
                parseSensor(JSONObject(readLimited(conn.inputStream)))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            log.log(Level.WARNING, "Error leyendo sensores: ${e.javaClass.simpleName}")
            null
        }
    }

    // ----------------------------------------------------------------- autenticación

    private fun validToken(): String? {
        val now = System.currentTimeMillis()
        val current = idToken
        if (current != null && now < tokenExpiresAtMs - 60_000) return current
        return refreshOrSignIn()
    }

    private fun invalidateToken() { idToken = null; tokenExpiresAtMs = 0L }

    private fun refreshOrSignIn(): String? {
        refreshToken?.let { rt ->
            val body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(rt, "UTF-8")}"
            val json = postForm("https://securetoken.googleapis.com/v1/token?key=${URLEncoder.encode(apiKey, "UTF-8")}", body)
            if (json != null) {
                storeToken(json.optString("id_token"), json.optString("refresh_token"), json.optString("expires_in"))
                if (idToken != null) return idToken
            }
        }
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("returnSecureToken", true)
            .toString()
        val json = postJson("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${URLEncoder.encode(apiKey, "UTF-8")}", payload)
        if (json == null) {
            log.warning("No se pudo iniciar sesión en Firebase")
            return null
        }
        storeToken(json.optString("idToken"), json.optString("refreshToken"), json.optString("expiresIn"))
        return idToken
    }

    private fun storeToken(id: String, refresh: String, expiresInSec: String) {
        if (id.isBlank()) return
        idToken = id
        if (refresh.isNotBlank()) refreshToken = refresh
        val ttl = expiresInSec.toLongOrNull() ?: 3600L
        tokenExpiresAtMs = System.currentTimeMillis() + ttl * 1000
    }

    private fun postJson(url: String, body: String): JSONObject? = post(url, body, "application/json")
    private fun postForm(url: String, body: String): JSONObject? = post(url, body, "application/x-www-form-urlencoded")

    private fun post(url: String, body: String, contentType: String): JSONObject? {
        return try {
            val conn = open(URL(url), "POST")
            try {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode != 200) null else JSONObject(readLimited(conn.inputStream))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            log.log(Level.WARNING, "Fallo en petición de autenticación: ${e.javaClass.simpleName}")
            null
        }
    }

    // ----------------------------------------------------------------- utilidades

    private fun open(url: URL, method: String): HttpURLConnection {
        require(url.protocol == "https") { "Solo se permite HTTPS" }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Int = MAX_RESPONSE_BYTES): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        input.use { stream ->
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw IllegalStateException("Respuesta demasiado grande")
                out.write(buf, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    internal fun parseSensor(json: JSONObject): SensorData? {
        fun num(key: String, min: Double, max: Double): Double? {
            if (!json.has(key) || json.isNull(key)) return null
            val v = json.optDouble(key, Double.NaN)
            return if (v.isNaN() || v.isInfinite() || v < min || v > max) null else v
        }
        val temp = num("temp", -40.0, 85.0) ?: return null
        val hum = num("hum", 0.0, 100.0) ?: return null
        val luz = num("luz", 0.0, 100.0) ?: return null
        val soil = num("soil", 0.0, 100.0) ?: return null
        return SensorData(temp, hum, luz.toInt(), soil)
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}

package com.example.controlherbal.common.legal

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Context
import com.example.controlherbal.data.database.SensorDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Derechos de la persona usuaria sobre sus datos: acceso/portabilidad (exportar), cancelación
 * (borrar datos locales y eliminar la cuenta). Todo ocurre desde la propia app.
 */
object DataControl {

    private const val TAG = "DataControl"
    private const val MAX_EXPORT_CHARS = 150_000   // evita superar el límite de tamaño de un Intent

    private fun iso(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    /** Devuelve un JSON legible con los datos locales, o null si no hay nada que exportar. */
    suspend fun exportLocalJson(context: Context): String? = withContext(Dispatchers.IO) {
        val db = SensorDatabase.getInstance(context)
        val plants = db.plantDao().getAll()
        if (plants.isEmpty()) {
            null
        } else {
            var resultText: String? = null
            var perPlant = 200
            while (resultText == null) {
                val root = JSONObject()
                root.put("exportadoEn", iso(System.currentTimeMillis()))
                root.put("versionDocumentosAceptada", ConsentManager.acceptedVersion(context) ?: JSONObject.NULL)
                ConsentManager.acceptedAt(context).takeIf { it > 0 }?.let { root.put("aceptadoEn", iso(it)) }
                root.put("consentimientoIA", AiConsent.isGranted(context))
                val arr = JSONArray()
                for (p in plants) {
                    val o = JSONObject()
                    o.put("nombre", p.name); o.put("tipo", p.type); o.put("ambiente", p.environment)
                    o.put("ultimoRiego", if (p.lastWateringTime > 0) iso(p.lastWateringTime) else JSONObject.NULL)
                    o.put("diagnosticoIA", p.aiDiagnosis ?: JSONObject.NULL)
                    o.put("recomendacionIA", p.aiRecommendation ?: JSONObject.NULL)
                    val readings = JSONArray()
                    db.sensorDao().getAllOrderByTimestampDesc(p.id).take(perPlant).forEach { r ->
                        readings.put(JSONObject()
                            .put("fecha", iso(r.timestamp)).put("temperatura", r.temperature).put("humedad", r.humidity)
                            .put("luz", r.light).put("humedadSuelo", r.soilMoisture).put("irh", r.irh))
                    }
                    o.put("lecturasRecientes", readings)
                    arr.put(o)
                }
                root.put("plantas", arr)
                val text = root.toString(2)
                if (text.length <= MAX_EXPORT_CHARS || perPlant == 0) {
                    resultText = text
                } else {
                    perPlant /= 2
                }
            }
            resultText
        }
    }

    /** Borra la base local, preferencias de datos y fotos temporales. Conserva solo la paleta de color. */
    suspend fun wipeLocal(context: Context) = withContext(Dispatchers.IO) {
        try {
            SensorDatabase.getInstance(context).clearAllTables()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "No se pudo vaciar la base local: ${e.javaClass.simpleName}")
        }
        listOf(AppConstants.PREFS_WATERING, AppConstants.PREFS_WEIGHTS_NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply()
        }
        ImageUtils.purgeCameraCache(File(context.cacheDir, "camera"), maxAgeMs = 0L)
    }

    /** Elimina los datos remotos del usuario autenticado antes de borrar su cuenta. */
    suspend fun deleteCloudData(): Boolean {
        val uid = AuthManager.uid() ?: return false
        return try {
            val database = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            val historyDeleted = database
                .getReference(AppConstants.FIREBASE_WATERING_HISTORY_NODE)
                .removeValue()
                .awaitResult()
                .isSuccess
            val profileDeleted = database
                .getReference("users")
                .child(uid)
                .removeValue()
                .awaitResult()
                .isSuccess
            if (!historyDeleted) SecureLogger.w(TAG, "No se pudo borrar el historial remoto del usuario")
            if (!profileDeleted) SecureLogger.w(TAG, "No se pudo borrar el perfil remoto del usuario")
            profileDeleted
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error al borrar datos en la nube: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Elimina perfil remoto, cuenta y datos locales. El nodo /roles/{uid} (solo UID y rol)
     * solo puede eliminarse desde la consola por el operador; ver Aviso de Privacidad.
     */
    suspend fun deleteAccountAndData(context: Context): AuthManager.DeleteResult {
        val cloudOk = deleteCloudData()
        if (!cloudOk) {
            SecureLogger.w(TAG, "Se cancela la eliminación de la cuenta porque no se pudo borrar su perfil remoto")
            return AuthManager.DeleteResult.FAILED
        }
        val result = AuthManager.deleteCurrentUser()
        if (result == AuthManager.DeleteResult.SUCCESS) {
            wipeLocal(context)
            ConsentManager.withdraw(context)
            AiConsent.revoke(context)
        }
        return result
    }
}

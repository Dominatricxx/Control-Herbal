package com.example.controlherbal.backend.repository


import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.database.SensorDao
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.backend.domain.repository.SensorDataRepository
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

/**
 * SensorDataRepositoryImpl: Implementación de SensorDataRepository unificando Room y Firebase RTDB.
 */
class SensorDataRepositoryImpl(
    private val sensorDao: SensorDao
) : SensorDataRepository {

    companion object {
        private const val TAG = "SensorDataRepositoryImpl"
    }

    override suspend fun getRecentReadings(plantId: Int, limit: Int): List<SensorReading> = withContext(Dispatchers.IO) {
        sensorDao.getLast2000Asc(plantId)
    }

    override suspend fun sendWateringCommand(seconds: Int, issuerUid: String?): Boolean = suspendCancellableCoroutine { cont ->
        val ref = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference(AppConstants.FIREBASE_WATERING_COMMAND)

        val commandData = mapOf(
            "duracionSegundos" to seconds,
            "timestamp" to ServerValue.TIMESTAMP,
            "nonce" to UUID.randomUUID().toString(),
            "emisor" to (issuerUid ?: "")
        )

        ref.setValue(commandData).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                SecureLogger.e(TAG, "Error al enviar comando de riego: ${task.exception?.javaClass?.simpleName}")
            }
            if (cont.isActive) {
                cont.resume(task.isSuccessful)
            }
        }
    }

    override suspend fun updatePlantTypeInCloud(tipoInt: Int): Boolean = suspendCancellableCoroutine { cont ->
        val ref = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference(AppConstants.FIREBASE_CONFIG_PLANT_TYPE)

        ref.setValue(tipoInt).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                SecureLogger.e(TAG, "Error al actualizar tipo de planta en Firebase: ${task.exception?.javaClass?.simpleName}")
            }
            if (cont.isActive) {
                cont.resume(task.isSuccessful)
            }
        }
    }
}

package com.example.controlherbal.data.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.controlherbal.common.AppConstants
import com.example.controlherbal.common.SecureLogger
import com.example.controlherbal.data.database.SensorDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.runBlocking

class WateringSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val TAG = "WateringSyncWorker"
    }

    override fun doWork(): Result {
        val databaseLocal = SensorDatabase.getInstance(applicationContext)
        val plantDao = databaseLocal.plantDao()
        val pendingPlants = runBlocking { plantDao.getPendingSyncPlants() }

        if (pendingPlants.isEmpty()) return Result.success()

        val firebaseRef = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference(AppConstants.FIREBASE_SENSOR_NODE)

        for (plant in pendingPlants) {
            val wateringData = mapOf(
                "lastWateringTime" to plant.lastWateringTime,
                "syncTimestamp" to System.currentTimeMillis()
            )
            
            try {
                firebaseRef.child("watering_history").child(plant.id.toString()).setValue(wateringData)
                
                runBlocking {
                    plantDao.update(plant.copy(pendingSync = false))
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error al sincronizar riego: ${e.message}")
                return Result.retry()
            }
        }

        return Result.success()
    }
}

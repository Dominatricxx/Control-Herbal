package com.example.controlherbal.data.sync

import com.example.controlherbal.common.security.*
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
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.SensorDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.runBlocking

class WateringSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val TAG = "WateringSyncWorker"
    }

    override fun doWork(): Result {
        if (!AuthManager.isSignedIn()) return Result.retry()   // se reintenta cuando haya sesión
        val databaseLocal = SensorDatabase.getInstance(applicationContext)
        val plantDao = databaseLocal.plantDao()
        val pendingPlants = runBlocking { plantDao.getPendingSyncPlants() }

        if (pendingPlants.isEmpty()) return Result.success()

        // Nodo propio (solo el rol "owner" puede escribirlo; ver reglas). Antes colgaba de /sensor.
        val firebaseRef = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference(AppConstants.FIREBASE_WATERING_HISTORY_NODE)

        for (plant in pendingPlants) {
            val wateringData = mapOf(
                "lastWateringTime" to plant.lastWateringTime,
                "syncTimestamp" to System.currentTimeMillis()
            )
            
            try {
                firebaseRef.child(plant.id.toString()).setValue(wateringData)
                
                runBlocking {
                    plantDao.update(plant.copy(pendingSync = false))
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error al sincronizar riego: ${e.javaClass.simpleName}")
                return Result.retry()
            }
        }

        return Result.success()
    }
}

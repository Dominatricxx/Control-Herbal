package com.example.controlherbal.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.controlherbal.database.SensorDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.runBlocking

class WateringSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val databaseLocal = SensorDatabase.getInstance(applicationContext)
        val plantDao = databaseLocal.plantDao()
        val pendingPlants = runBlocking { plantDao.getPendingSyncPlants() }

        if (pendingPlants.isEmpty()) return Result.success()

        val firebaseRef = FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/")
            .getReference("sensor")

        for (plant in pendingPlants) {
            val wateringData = mapOf(
                "lastWateringTime" to plant.lastWateringTime,
                "syncTimestamp" to System.currentTimeMillis()
            )
            
            try {
                // Firebase funciona offline, pero para asegurar la confirmación podrías añadir una espera aquí
                firebaseRef.child("watering_history").child(plant.id.toString()).setValue(wateringData)
                
                runBlocking {
                    plantDao.update(plant.copy(pendingSync = false))
                }
            } catch (e: Exception) {
                return Result.retry()
            }
        }

        return Result.success()
    }
}

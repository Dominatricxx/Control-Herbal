package com.example.controlherbal.sync

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.controlherbal.MainActivity
import com.example.controlherbal.R
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.logic.PredictiveTheorem
import com.google.firebase.database.*
import kotlinx.coroutines.*
import java.util.*

class SensorForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var databaseFirebase: DatabaseReference
    private lateinit var databaseLocal: SensorDatabase
    private var firebaseListener: ValueEventListener? = null
    
    private var lastBootCount: Int = -1
    private var startTimeWithoutSun: Long = 0

    companion object {
        private const val TAG = "SensorService"
        private const val CHANNEL_ID = "sensor_service_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        databaseLocal = SensorDatabase.getInstance(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Sincronizando datos en segundo plano..."))
        setupFirebase()
    }

    private fun setupFirebase() {
        try {
            val dbInstance = FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/")
            databaseFirebase = dbInstance.getReference("sensor")
            databaseFirebase.keepSynced(true)
            startFirebaseListener()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}")
        }
    }

    private fun startFirebaseListener() {
        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                
                serviceScope.launch {
                    try {
                        val boot = (snapshot.child("boot").value as? Number)?.toInt() ?: -1
                        val temp = (snapshot.child("temp").value as? Number)?.toDouble() ?: 0.0
                        val hum = (snapshot.child("hum").value as? Number)?.toDouble() ?: 0.0
                        val luz = (snapshot.child("luz").value as? Number)?.toInt() ?: 0
                        val soil = (snapshot.child("soil").value as? Number)?.toDouble() ?: 0.0
                        
                        if (temp == 0.0 && hum == 0.0 && luz == 0 && soil == 0.0) return@launch

                        val plant = databaseLocal.plantDao().getSelectedPlant() ?: return@launch
                        val lastReading = databaseLocal.sensorDao().getAllOrderByTimestampDesc(plant.id).firstOrNull()

                        // --- DETECCIÓN AUTOMÁTICA DE RIEGO ---
                        // Si la humedad del suelo sube drásticamente (ej. +12%) entre lecturas
                        if (lastReading != null && soil > lastReading.soilMoisture + 12.0) {
                            val now = System.currentTimeMillis()
                            // Solo registrar si han pasado al menos 30 min desde el último registro de riego 
                            // para evitar duplicados por fluctuaciones de un mismo riego
                            if (now - plant.lastWateringTime > 1800000) {
                                Log.d(TAG, "Riego detectado automáticamente por aumento de humedad (+${soil - lastReading.soilMoisture}%)")
                                val updatedPlant = plant.copy(lastWateringTime = now, pendingSync = true)
                                databaseLocal.plantDao().update(updatedPlant)
                                sendCriticalAlert("💧 Riego detectado", "Se ha detectado un aumento drástico de humedad. Predicciones reajustadas.")
                            }
                        }

                        var deltaTemp = 0.0
                        var deltaHum = 0.0
                        var deltaLuz = 0.0
                        var deltaSoil = 0.0

                        lastReading?.let { prev ->
                            val dt = (System.currentTimeMillis() - prev.timestamp) / 3600000.0
                            if (dt > 0.001) {
                                deltaTemp = (temp - prev.temperature) / dt
                                deltaHum = (hum - prev.humidity) / dt
                                deltaLuz = (luz - prev.light.toDouble()) / dt
                                deltaSoil = (soil - prev.soilMoisture) / dt
                            }
                        }

                        if (luz < 20) {
                            if (startTimeWithoutSun == 0L) startTimeWithoutSun = System.currentTimeMillis()
                        } else {
                            startTimeWithoutSun = 0L
                        }

                        val hoursWithoutSun = if (startTimeWithoutSun > 0) {
                            (System.currentTimeMillis() - startTimeWithoutSun) / 3600000.0
                        } else 0.0

                        val result = PredictiveTheorem.analyze(
                            temp, hum, luz, soil,
                            deltaTemp, deltaHum, deltaLuz, deltaSoil,
                            plant.lastWateringTime,
                            plant.type,
                            hoursWithoutSun
                        )

                        val reading = SensorReading(
                            timestamp = System.currentTimeMillis(),
                            plantId = plant.id,
                            temperature = temp,
                            humidity = result.adjustedHum, // Guardar humedad compensada por IA
                            light = luz,
                            soilMoisture = soil,
                            irh = result.irh,
                            seq = result.seq,
                            somb = result.somb,
                            action = result.recommendation
                        )
                        
                        databaseLocal.sensorDao().insert(reading)
                        databaseLocal.sensorDao().pruneData(plant.id)

                        // Update notification with latest data
                        updateNotification("Temp: ${String.format("%.1f", temp)}°C | Suelo: ${String.format("%.1f", soil)}%")
                        
                        // Handle critical alerts if necessary (could reuse MainActivity's notification logic here)
                        if (result.irh >= PredictiveTheorem.IRH_RIESGO) {
                             sendCriticalAlert("¡RIESGO CRÍTICO!", result.recommendation)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing data: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase cancelled: ${error.message}")
            }
        }
        firebaseListener?.let { databaseFirebase.addValueEventListener(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo de Sensores",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Control Herbal")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendCriticalAlert(title: String, message: String) {
        // Reuse main alert channel or create a high importance one
        val alertId = 3001
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(alertId, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        firebaseListener?.let { databaseFirebase.removeEventListener(it) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

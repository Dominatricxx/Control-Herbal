package com.example.controlherbal.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.controlherbal.R
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.common.AppConstants
import com.example.controlherbal.common.AuthManager
import com.example.controlherbal.common.SecureLogger
import com.example.controlherbal.common.SecurityUtils
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import com.example.controlherbal.ui.activities.MainActivity
import com.example.controlherbal.ui.widget.HerbalWidgetManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar

class SensorForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var databaseFirebase: DatabaseReference
    private lateinit var databaseLocal: SensorDatabase
    private lateinit var herbalAI: HerbalAI
    private var firebaseListener: ValueEventListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var startTimeWithoutSun: Long = 0
    private var lastIrh: Double = -1.0
    private var lastSeq: Double = -1.0
    
    private var lastTemp = 0.0
    private var lastHum = 0.0
    private var lastLuz = 0.0
    private var lastSoil = 0.0

    companion object {
        private const val TAG = "SensorService"
    }

    override fun onCreate() {
        super.onCreate()
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ControlHerbal:SensorSync")
        wakeLock?.acquire(10 * 60 * 1000L)

        databaseLocal = SensorDatabase.getInstance(this)
        herbalAI = HerbalAI(this)
        createNotificationChannel()
        startForeground(AppConstants.NOTIFICATION_ID_SERVICE, createNotification("Sincronizando datos en segundo plano..."))
        setupFirebase()
    }

    private fun setupFirebase() {
        if (!AuthManager.isSignedIn()) {
            SecureLogger.w(TAG, "Sin sesión: el servicio se detiene")
            stopSelf()
            return
        }
        try {
            val dbInstance = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            databaseFirebase = dbInstance.getReference(AppConstants.FIREBASE_SENSOR_NODE)
            databaseFirebase.keepSynced(true)
            startFirebaseListener()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error al inicializar Firebase: ${e.message}")
        }
    }

    private fun startFirebaseListener() {
        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                
                if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L)

                serviceScope.launch {
                    try {
                        val tempRaw = SecurityUtils.clampValue((snapshot.child("temp").value as? Number)?.toDouble() ?: 0.0, 0.0, 100.0)
                        val humRaw = SecurityUtils.clampValue((snapshot.child("hum").value as? Number)?.toDouble() ?: 0.0, 0.0, 100.0)
                        val luzRaw = SecurityUtils.clampValue((snapshot.child("luz_raw").value as? Number)?.toDouble() ?: (snapshot.child("luz").value as? Number)?.toDouble() ?: 0.0, 0.0, 4095.0)
                        val soilRaw = SecurityUtils.clampValue((snapshot.child("soil").value as? Number)?.toDouble() ?: 0.0, 0.0, 4095.0)

                        if (tempRaw == 0.0 && humRaw == 0.0 && luzRaw == 0.0 && soilRaw == 0.0) return@launch

                        val plant = databaseLocal.plantDao().getSelectedPlant() ?: return@launch
                        
                        val humFiltrada = PredictiveTheorem.filtrarSensibilidadHumedad(humRaw)
                        val luzFiltrada = PredictiveTheorem.filtrarSensibilidadLuz(luzRaw)
                        val soilFiltrada = if (soilRaw > 100.0) PredictiveTheorem.filtrarSensibilidadSuelo(soilRaw) else soilRaw

                        fun stabilize(current: Double, target: Double, threshold: Double): Double {
                            if (current <= 0.1) return target
                            return if (Math.abs(current - target) > threshold) {
                                current * 0.8 + target * 0.2
                            } else target
                        }

                        val sTemp = stabilize(lastTemp, tempRaw, 5.0)
                        val sHum = stabilize(lastHum, humFiltrada, 15.0)
                        val sLuz = stabilize(lastLuz, luzFiltrada, 25.0)
                        val sSoil = stabilize(lastSoil, soilFiltrada, 15.0)

                        lastTemp = sTemp; lastHum = sHum; lastLuz = sLuz; lastSoil = sSoil

                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val isDayByTime = currentHour in 7..19
                        val isDayByLight = sLuz > 10.0
                        val isDayBySensor = (snapshot.child("is_day").value as? Number)?.toInt() == 1 || 
                                           (snapshot.child("dia").value as? Number)?.toInt() == 1
                        
                        val isDay = isDayByTime || isDayBySensor || isDayByLight

                        val lastReading = databaseLocal.sensorDao().getAllOrderByTimestampDesc(plant.id).firstOrNull()

                        if (lastReading != null && sSoil > lastReading.soilMoisture + 12.0) {
                            val now = System.currentTimeMillis()
                            if (now - plant.lastWateringTime > 1800000) {
                                val updatedPlant = plant.copy(lastWateringTime = now, pendingSync = true)
                                databaseLocal.plantDao().update(updatedPlant)
                            }
                        }

                        var deltaTemp = 0.0; var deltaHum = 0.0; var deltaLuz = 0.0; var deltaSoil = 0.0

                        lastReading?.let { prev ->
                            val dt = (System.currentTimeMillis() - prev.timestamp) / 3600000.0
                            if (dt > 0.001) {
                                deltaTemp = (sTemp - prev.temperature) / dt
                                deltaHum = (sHum - prev.humidity) / dt
                                deltaLuz = (sLuz - prev.light) / dt
                                deltaSoil = (sSoil - prev.soilMoisture) / dt
                            }
                        }

                        if (sLuz < 20.0) {
                            if (startTimeWithoutSun == 0L) startTimeWithoutSun = System.currentTimeMillis()
                        } else {
                            startTimeWithoutSun = 0L
                        }

                        val hoursWithoutSun = if (startTimeWithoutSun > 0) {
                            (System.currentTimeMillis() - startTimeWithoutSun) / 3600000.0
                        } else 0.0

                        val historySize = databaseLocal.sensorDao().getCountByPlantId(plant.id)

                        val aiDehydrationFactor = herbalAI.predictDehydrationFactor(sTemp, sHum, sLuz, sSoil, plant.type, isDay)

                        val result = PredictiveTheorem.analyze(
                            sTemp, humRaw, luzRaw, soilRaw,
                            deltaTemp, deltaHum, deltaLuz, deltaSoil,
                            plant.lastWateringTime,
                            plant.type,
                            hoursWithoutSun,
                            isDay,
                            aiFactor = aiDehydrationFactor,
                            historySize = historySize
                        )

                        val irhAI = herbalAI.predictRefinedIRH(sTemp, sHum, sLuz, sSoil, plant.type, isDay)
                        var finalIrh = (result.irh + irhAI) / 2.0

                        if (lastIrh >= 0) {
                            if (finalIrh < 1.0 && lastIrh > 10.0) {
                                finalIrh = lastIrh * 0.9
                            } else {
                                finalIrh = finalIrh * 0.2 + lastIrh * 0.8
                            }
                        }
                        lastIrh = finalIrh

                        val finalSeq = if (lastSeq < 0) result.seq else (result.seq * 0.15 + lastSeq * 0.85)
                        lastSeq = finalSeq

                        val reading = SensorReading(
                            timestamp = System.currentTimeMillis(),
                            plantId = plant.id,
                            temperature = sTemp,
                            humidity = sHum,
                            light = sLuz,
                            soilMoisture = sSoil,
                            irh = finalIrh,
                            seq = finalSeq,
                            somb = result.somb,
                            action = result.recommendation
                        )
                        
                        databaseLocal.sensorDao().insert(reading)
                        databaseLocal.sensorDao().pruneData(plant.id)

                        HerbalWidgetManager.updateWidgets(this@SensorForegroundService)

                        updateNotification("Temp: ${String.format("%.1f", sTemp)}°C | Suelo: ${String.format("%.1f", sSoil)}%")
                        
                        if (finalIrh >= PredictiveTheorem.IRH_RIESGO) {
                             sendCriticalAlert("¡RIESGO CRÍTICO!", result.recommendation)
                        }

                    } catch (e: Exception) {
                        SecureLogger.e(TAG, "Error procesando datos: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                SecureLogger.e(TAG, "Firebase cancelado: ${error.message}")
            }
        }
        firebaseListener?.let { databaseFirebase.addValueEventListener(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.SERVICE_CHANNEL_ID,
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

        return NotificationCompat.Builder(this, AppConstants.SERVICE_CHANNEL_ID)
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
        manager.notify(AppConstants.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun sendCriticalAlert(title: String, message: String) {
        val alertId = 3001
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, AppConstants.ALERTS_CHANNEL_ID)
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
        if (wakeLock?.isHeld == true) wakeLock?.release()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

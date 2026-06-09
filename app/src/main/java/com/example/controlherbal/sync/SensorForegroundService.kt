package com.example.controlherbal.sync

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.controlherbal.MainActivity
import com.example.controlherbal.R
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.logic.PredictiveTheorem
import com.example.controlherbal.widget.HerbalWidgetManager
import com.google.firebase.database.*
import kotlinx.coroutines.*
import java.util.*

class SensorForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var databaseFirebase: DatabaseReference
    private lateinit var databaseLocal: SensorDatabase
    private lateinit var herbalAI: HerbalAI
    private var firebaseListener: ValueEventListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var lastBootCount: Int = -1
    private var startTimeWithoutSun: Long = 0
    private var lastIrh: Double = -1.0
    private var lastSeq: Double = -1.0
    
    private var lastTemp = 0.0
    private var lastHum = 0.0
    private var lastLuz = 0.0
    private var lastSoil = 0.0

    companion object {
        private const val TAG = "SensorService"
        private const val CHANNEL_ID = "sensor_service_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        
        // Solicitar WakeLock para evitar que el sistema mate la conexión al apagar pantalla
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ControlHerbal:SensorSync")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutos de gracia si no hay updates*/)

        databaseLocal = SensorDatabase.getInstance(this)
        herbalAI = HerbalAI(this)
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
                
                // Refrescar WakeLock cada que llegan datos
                if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L)

                serviceScope.launch {
                    try {
                        val boot = (snapshot.child("boot").value as? Number)?.toInt() ?: -1
                        val tempRaw = (snapshot.child("temp").value as? Number)?.toDouble() ?: 0.0
                        val humRaw = (snapshot.child("hum").value as? Number)?.toDouble() ?: 0.0
                        val luzRaw = (snapshot.child("luz_raw").value as? Number)?.toDouble() ?: (snapshot.child("luz").value as? Number)?.toDouble() ?: 0.0
                        val soilRaw = (snapshot.child("soil").value as? Number)?.toDouble() ?: 0.0
                        
                        // DEBUG para el problema de los 25°C y 50%
                        if (tempRaw == 25.0 && humRaw == 50.0) {
                            Log.w(TAG, "AVISO: Recibidos valores estáticos (25/50). ¿Sensor desconectado o valor por defecto?")
                        }

                        if (tempRaw == 0.0 && humRaw == 0.0 && luzRaw == 0.0 && soilRaw == 0.0) return@launch

                        val plant = databaseLocal.plantDao().getSelectedPlant() ?: return@launch
                        
                        // FILTROS DE ESTABILIDAD (Noise Reduction)
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

                        // --- LÓGICA DE DETECCIÓN DÍA/NOCHE REAFIRMADA ---
                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val isDayByTime = currentHour in 7..19
                        val isDayByLight = sLuz > 10.0
                        val isDayBySensor = (snapshot.child("is_day").value as? Number)?.toInt() == 1 || 
                                           (snapshot.child("dia").value as? Number)?.toInt() == 1
                        
                        // Prioridad absoluta al horario y luz: Si el reloj marca día o hay luz detectada, es DÍA.
                        // Esto evita que fallos en el sensor de luz o nubes marquen "Noche" erróneamente.
                        val isDay = isDayByTime || isDayBySensor || isDayByLight

                        val lastReading = databaseLocal.sensorDao().getAllOrderByTimestampDesc(plant.id).firstOrNull()

                        // --- DETECCIÓN AUTOMÁTICA DE RIEGO ---
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

                        // Calculamos el Factor IA de deshidratación
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

                        // REFINAMIENTO IA DEL IRH
                        val irhAI = herbalAI.predictRefinedIRH(sTemp, sHum, sLuz, sSoil, plant.type, isDay)
                        var finalIrh = (result.irh + irhAI) / 2.0

                        // Estabilización final del IRH (Anti-parpadeo 0)
                        if (lastIrh >= 0) {
                            if (finalIrh < 1.0 && lastIrh > 10.0) {
                                finalIrh = lastIrh * 0.9
                            } else {
                                finalIrh = finalIrh * 0.2 + lastIrh * 0.8
                            }
                        }
                        lastIrh = finalIrh

                        // Estabilización de SEQ (Inercia)
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

                        // Actualizar Widgets sincronizadamente con los datos
                        HerbalWidgetManager.updateWidgets(this@SensorForegroundService)

                        updateNotification("Temp: ${String.format("%.1f", sTemp)}°C | Suelo: ${String.format("%.1f", sSoil)}%")
                        
                        if (finalIrh >= PredictiveTheorem.IRH_RIESGO) {
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
        if (wakeLock?.isHeld == true) wakeLock?.release()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

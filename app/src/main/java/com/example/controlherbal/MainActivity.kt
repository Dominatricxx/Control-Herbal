package com.example.controlherbal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.logic.PredictiveTheorem
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "Control Herbal"
        private const val LEARNING_THRESHOLD = 20
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvConnectionState: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvLuz: TextView
    private lateinit var tvIRH: TextView
    private lateinit var tvSeq: TextView
    private lateinit var tvSomb: TextView
    private lateinit var tvAccion: TextView
    private lateinit var tvCausa: TextView
    private lateinit var tvAlerta: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var lineChart: LineChart
    private lateinit var btnConnect: Button
    private var tvPlantNameAndEmoji: TextView? = null
    private var tvEnvironmentEmoji: TextView? = null
    private var layoutPlantInfo: View? = null

    private lateinit var databaseFirebase: DatabaseReference
    private val CHANNEL_ID = "herbal_alerts_channel"
    private val NOTIFICATION_ID = 1001
    private val SYNC_NOTIFICATION_ID = 1002
    private var lastAlertState = 0 
    private var isDisconnected = false
    private var lastBootCount: Int = -1
    private val handler = Handler(Looper.getMainLooper())
    private val syncTimeoutRunnable = Runnable { handleDisconnection() }

    private lateinit var databaseLocal: SensorDatabase
    private lateinit var herbalAI: HerbalAI
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var lastChartUpdate: Long = 0
    private var tflite: Interpreter? = null
    private var lastReading: SensorReading? = null
    private var readingsSinceLastLearning = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("PlantPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("setup_complete", false)) {
            val intent = Intent(this, PlantSetupActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main_drawer)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.menu.findItem(R.id.nav_main).isVisible = false

        tvConnectionState = findViewById(R.id.tvConnectionState)
        tvTemp = findViewById(R.id.tvTemp)
        tvHum = findViewById(R.id.tvHum)
        tvLuz = findViewById(R.id.tvLuz)
        tvIRH = findViewById(R.id.tvIRH)
        tvSeq = findViewById(R.id.tvSeq)
        tvSomb = findViewById(R.id.tvSomb)
        tvAccion = findViewById(R.id.tvAccion)
        tvCausa = findViewById(R.id.tvCausa)
        tvAlerta = findViewById(R.id.tvAlerta)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        lineChart = findViewById(R.id.lineChart)
        btnConnect = findViewById(R.id.btnConnect)
        tvPlantNameAndEmoji = findViewById(R.id.tvPlantNameAndEmoji)
        tvEnvironmentEmoji = findViewById(R.id.tvEnvironmentEmoji)
        layoutPlantInfo = findViewById(R.id.layoutPlantInfo)

        showPlantInfo()

        btnConnect.setOnClickListener { startFirebaseListener() }

        databaseLocal = SensorDatabase.getInstance(this)
        herbalAI = HerbalAI(this)

        try {
            databaseFirebase = FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/").getReference("sensor")
            databaseFirebase.keepSynced(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}")
        }

        ioScope.launch {
            loadModel()
            val readings = databaseLocal.sensorDao().getLast2000Asc()
            if (readings.isNotEmpty()) {
                lastReading = readings.last()
            }
            withContext(Dispatchers.Main) {
                startFirebaseListener()
                loadDataAndDrawChart()
            }
        }
        createNotificationChannel()
        requestNotificationPermission()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = assets.openFd("herbal_model.tflite")
            val inputStream = java.io.FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
            tflite = Interpreter(modelBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "TFLite model not found.")
        }
    }

    private fun performAnalysis(temp: Double, hum: Double, luz: Int): PredictiveTheorem.AnalysisResult {
        var deltaTemp = 0.0
        var deltaHum = 0.0
        var deltaLuz = 0.0

        lastReading?.let { prev ->
            val dt = (System.currentTimeMillis() - prev.timestamp) / 3600000.0
            if (dt > 0.001) {
                deltaTemp = (temp - prev.temperature) / dt
                deltaHum = (hum - prev.humidity) / dt
                deltaLuz = (luz - prev.light) / dt
            }
        }

        val result = PredictiveTheorem.analyze(temp, hum, luz, deltaTemp, deltaHum, deltaLuz)
        val irhAI = herbalAI.predictRefinedIRH(temp, hum, luz)
        var finalIrh = (result.irh + irhAI) / 2.0
        
        tflite?.let { interpreter ->
            val input = arrayOf(floatArrayOf(temp.toFloat(), hum.toFloat(), luz.toFloat()))
            val output = arrayOf(floatArrayOf(0f))
            interpreter.run(input, output)
            finalIrh = (finalIrh + output[0][0].toDouble()) / 2.0
        }
        
        return result.copy(irh = finalIrh)
    }

    private fun showPlantInfo() {
        val prefs = getSharedPreferences("PlantPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("plant_name", "")
        val type = prefs.getString("plant_type", "")
        val environment = prefs.getString("plant_environment", "")

        if (!name.isNullOrEmpty()) {
            layoutPlantInfo?.visibility = View.VISIBLE
            val typeEmoji = type?.split(" ")?.lastOrNull() ?: ""
            val envEmoji = when (environment) {
                "Luz" -> "☀️"
                "Sombra" -> "🌥️"
                "Híbrido" -> "⛅"
                else -> ""
            }
            tvPlantNameAndEmoji?.text = String.format("%s %s", name, typeEmoji)
            tvEnvironmentEmoji?.text = String.format(" - %s %s", environment, envEmoji)
        }
    }

    private fun startFirebaseListener() {
        tvConnectionState.text = "Conectando..."
        tvConnectionState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        btnConnect.text = "Vinculando..."
        btnConnect.isEnabled = false

        databaseFirebase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                handler.removeCallbacks(syncTimeoutRunnable)
                handler.postDelayed(syncTimeoutRunnable, 5000)
                
                if (isDisconnected) {
                    isDisconnected = false
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(SYNC_NOTIFICATION_ID)
                }

                if (snapshot.exists()) {
                    val deviceName = snapshot.child("deviceName").getValue(String::class.java) ?: "ESP-32"
                    val bootCount = snapshot.child("boot").getValue(Int::class.java) ?: 0
                    
                    if (lastBootCount != -1 && bootCount < lastBootCount) {
                        Log.d(TAG, "ESP-32 Reboot detected")
                    }
                    lastBootCount = bootCount

                    btnConnect.text = String.format("Vinculado a %s", deviceName)
                    btnConnect.isEnabled = false
                    
                    tvConnectionState.text = "Conectado a Firebase"
                    tvConnectionState.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                    
                    val temp = snapshot.child("temp").getValue(Double::class.java) ?: 0.0
                    val hum = snapshot.child("hum").getValue(Double::class.java) ?: 0.0
                    val luz = snapshot.child("luz").getValue(Int::class.java) ?: 0
                    
                    ioScope.launch {
                        val result = performAnalysis(temp, hum, luz)
                        withContext(Dispatchers.Main) {
                            updateUIAndSave(temp, hum, luz, result.irh, result.seq, result.somb, result.recommendation)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                tvConnectionState.text = "Error de conexión"
                tvConnectionState.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                btnConnect.text = "CONECTAR AL DISPOSITIVO"
                btnConnect.isEnabled = true
            }
        })
    }

    private fun updateUIAndSave(temp: Double, hum: Double, luz: Int, irh: Double, seq: Double, somb: Double, accion: String) {
        val locale = Locale.getDefault()
        tvTemp.text = String.format(locale, "%.1f °C", temp)
        tvHum.text = String.format(locale, "%.1f %%", hum)
        tvLuz.text = String.format(locale, "%d %%", luz)
        tvIRH.text = String.format(locale, "%.1f", irh)
        tvSeq.text = if (seq < PredictiveTheorem.PREDICCION_MAX_HORAS) String.format(locale, "%.1f h", seq) else "Sin riesgo"
        tvSomb.text = if (somb < PredictiveTheorem.PREDICCION_MAX_HORAS) String.format(locale, "%.1f h", somb) else "Sin necesidad"
        tvAccion.text = accion
        tvCausa.text = ""
        tvLastUpdate.text = String.format("Última actualización: %s", SimpleDateFormat("HH:mm:ss", locale).format(Date()))

        val isStressful = irh >= PredictiveTheorem.IRH_OPTIMO || 
                          temp < PredictiveTheorem.TEMP_OPTIMA_MIN || temp > PredictiveTheorem.TEMP_OPTIMA_MAX ||
                          hum < PredictiveTheorem.HUM_OPTIMA_MIN || hum > PredictiveTheorem.HUM_OPTIMA_MAX ||
                          luz > PredictiveTheorem.LUZ_OPTIMA_MAX

        when {
            irh >= PredictiveTheorem.IRH_RIESGO -> {
                tvAlerta.text = "⚠️ ¡RIESGO CRÍTICO!"
                tvAlerta.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                tvAlerta.setTextColor(Color.WHITE)
                if (lastAlertState != 2) {
                    sendNotification(String.format("🚨 RIESGO CRÍTICO (IRH: %.1f)", irh), accion)
                    lastAlertState = 2
                }
            }
            irh >= PredictiveTheorem.IRH_ADVERTENCIA || isStressful -> {
                tvAlerta.text = "⚠️ ADVERTENCIA"
                tvAlerta.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                tvAlerta.setTextColor(Color.BLACK)
                if (lastAlertState != 1) {
                    sendNotification(String.format("⚠️ Advertencia (IRH: %.1f)", irh), accion)
                    lastAlertState = 1
                }
            }
            else -> {
                tvAlerta.text = "✅ Condiciones óptimas"
                tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
                tvAlerta.setTextColor(Color.BLACK)
                if (lastAlertState != 0) {
                    sendNotification(String.format("✅ Planta fuera de peligro (IRH: %.1f)", irh), "Las condiciones han vuelto a la normalidad.")
                    lastAlertState = 0
                }
            }
        }

        val reading = SensorReading(System.currentTimeMillis(), temp, hum, luz, irh, seq, somb, accion)
        lastReading = reading
        ioScope.launch {
            databaseLocal.sensorDao().insert(reading)
            databaseLocal.sensorDao().pruneData()
            readingsSinceLastLearning++
            if (readingsSinceLastLearning >= LEARNING_THRESHOLD) {
                readingsSinceLastLearning = 0
                val history = databaseLocal.sensorDao().getLast2000Asc()
                herbalAI.performSelfLearning(history)
            }
            val curr = System.currentTimeMillis()
            if (curr - lastChartUpdate >= 30000) {
                lastChartUpdate = curr
                withContext(Dispatchers.Main) { loadDataAndDrawChart() }
            }
        }
    }

    private fun handleDisconnection() {
        if (!isDisconnected) {
            isDisconnected = true
            tvConnectionState.text = "Desincronizado"
            tvConnectionState.setTextColor(Color.RED)
            btnConnect.text = "DESVINCULADO - RECONECTAR"
            btnConnect.isEnabled = true
            tvAlerta.text = "Esperando datos..."
            tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
            sendSyncNotification("⚠️ ESP-32 Desconectado", "Se ha perdido la sincronización con el dispositivo.")
        }
    }

    private fun sendNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setColor(ContextCompat.getColor(this, R.color.green_herbal))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun sendSyncNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setColor(Color.GRAY)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SYNC_NOTIFICATION_ID, builder.build())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Alertas de Control Herbal", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun loadDataAndDrawChart() {
        ioScope.launch {
            val readings = databaseLocal.sensorDao().getLast2000Asc()
            withContext(Dispatchers.Main) {
                if (readings.isNotEmpty()) drawChart(readings)
                else lineChart.setNoDataText("Esperando datos...")
            }
        }
    }

    private fun drawChart(readings: List<SensorReading>) {
        val tE = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
        val hE = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
        val lE = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
        val tS = LineDataSet(tE, "Temp").apply { color = Color.RED; setDrawCircles(false); lineWidth = 2f }
        val hS = LineDataSet(hE, "Hum").apply { color = Color.BLUE; setDrawCircles(false); lineWidth = 2f }
        val lS = LineDataSet(lE, "Luz").apply { color = Color.YELLOW; setDrawCircles(false); lineWidth = 2f }
        lineChart.apply {
            data = LineData(tS, hS, lS)
            description.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = object : ValueFormatter() {
                private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                override fun getAxisLabel(v: Float, a: AxisBase?): String {
                    val idx = v.toInt()
                    return if (idx in readings.indices) sdf.format(Date(readings[idx].timestamp)) else ""
                }
            }
            invalidate()
        }
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        val intent = Intent(this, HistoryActivity::class.java)
        when (item.itemId) {
            R.id.nav_daily -> intent.putExtra("HISTORY_TYPE", "DIARIO")
            R.id.nav_weekly -> intent.putExtra("HISTORY_TYPE", "SEMANAL")
            R.id.nav_monthly -> intent.putExtra("HISTORY_TYPE", "MENSUAL")
        }
        startActivity(intent)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}

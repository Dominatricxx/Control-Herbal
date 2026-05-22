package com.example.controlherbal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import com.example.controlherbal.ai.HerbalAI

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "Control Herbal"
        private const val LEARNING_THRESHOLD = 20 // Aprender cada 20 nuevos registros
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

    private val databaseFirebase = FirebaseDatabase.getInstance("https://com-example-controlherba-b07af-default-rtdb.firebaseio.com/").getReference("sensor")
    private val CHANNEL_ID = "herbal_alerts_channel"
    private var lastAlertState = 0 

    private lateinit var databaseLocal: SensorDatabase
    private lateinit var herbalAI: HerbalAI
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var lastChartUpdate: Long = 0
    private var tflite: Interpreter? = null
    private var lastReading: SensorReading? = null
    private var readingsSinceLastLearning = 0

    private fun loadModel() {
        try {
            val assetFileDescriptor = assets.openFd("herbal_model.tflite")
            val inputStream = java.io.FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            tflite = Interpreter(modelBuffer)
            Log.d(TAG, "IA: Modelo cargado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "IA: Modelo no encontrado. Usando lógica base de Arduino.")
        }
    }

    private fun performAnalysis(temp: Double, hum: Double, luz: Int): PredictiveTheorem.AnalysisResult {
        var deltaTemp = 0.0
        var deltaHum = 0.0
        var deltaLuz = 0.0

        lastReading?.let { prev ->
            val dt = (System.currentTimeMillis() - prev.timestamp) / 3600000.0 // hours
            if (dt > 0.001) {
                deltaTemp = (temp - prev.temperature) / dt
                deltaHum = (hum - prev.humidity) / dt
                deltaLuz = (luz - prev.light) / dt
            }
        }

        val result = PredictiveTheorem.analyze(temp, hum, luz, deltaTemp, deltaHum, deltaLuz)
        
        // REFINAMIENTO POR IA (Machine Learning)
        val irhAI = herbalAI.predictRefinedIRH(temp, hum, luz)
        // El IRH final es una mezcla equilibrada entre la lógica base y lo aprendido por la IA
        val finalIrh = (result.irh + irhAI) / 2.0
        
        // Si hay modelo TFLite, también lo incluimos en el promedio
        if (tflite != null) {
            val input = arrayOf(floatArrayOf(temp.toFloat(), hum.toFloat(), luz.toFloat()))
            val output = arrayOf(floatArrayOf(0f))
            tflite?.run(input, output)
            val irhTFLite = output[0][0].toDouble()
            return result.copy(irh = (finalIrh + irhTFLite) / 2.0)
        }
        
        return result.copy(irh = finalIrh)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        btnConnect.setOnClickListener {
            startFirebaseListener()
        }

        databaseLocal = SensorDatabase.getInstance(this)
        herbalAI = HerbalAI(this)

        ioScope.launch {
            loadModel()
            // Obtener el último registro de la BD para tener deltas iniciales
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas de Control Herbal"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                enableVibration(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setColor(ContextCompat.getColor(this, R.color.green_herbal))
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        val intent = android.content.Intent(this, HistoryActivity::class.java)
        when (item.itemId) {
            R.id.nav_daily -> intent.putExtra("HISTORY_TYPE", "DIARIO")
            R.id.nav_weekly -> intent.putExtra("HISTORY_TYPE", "SEMANAL")
            R.id.nav_monthly -> intent.putExtra("HISTORY_TYPE", "MENSUAL")
        }
        startActivity(intent)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun startFirebaseListener() {
        tvConnectionState.text = "Sincronizando..."
        tvConnectionState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        btnConnect.text = "Sincronizando..."
        btnConnect.isEnabled = false

        databaseFirebase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val deviceName = snapshot.child("deviceName").getValue(String::class.java) ?: "ESP-32"
                    btnConnect.text = "Vinculado a $deviceName"
                    btnConnect.isEnabled = false
                    
                    tvConnectionState.text = "Conectado a Firebase"
                    tvConnectionState.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                    
                    val temp = snapshot.child("temp").getValue(Double::class.java) ?: 0.0
                    val hum = snapshot.child("hum").getValue(Double::class.java) ?: 0.0
                    val luz = snapshot.child("luz").getValue(Int::class.java) ?: 0
                    
                    // Usamos la lógica de Arduino adaptada a la App
                    ioScope.launch {
                        val result = performAnalysis(temp, hum, luz)
                        withContext(Dispatchers.Main) {
                            updateUIAndSave(temp, hum, luz, result.irh, result.seq, result.somb, result.recommendation, "")
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

    private fun updateUIAndSave(temp: Double, hum: Double, luz: Int, irh: Double, seq: Double, somb: Double, accion: String, causa: String) {
        tvTemp.text = String.format("%.1f °C", temp)
        tvHum.text = String.format("%.1f %%", hum)
        tvLuz.text = "$luz %"
        tvIRH.text = String.format("%.1f", irh)
        tvSeq.text = if (seq < PredictiveTheorem.PREDICCION_MAX_HORAS) String.format("%.1f h", seq) else "Sin riesgo"
        tvSomb.text = if (somb < PredictiveTheorem.PREDICCION_MAX_HORAS) String.format("%.1f h", somb) else "Sin necesidad"
        tvAccion.text = accion
        // Causa ya está incluida en la recomendación de Arduino, la ocultamos o limpiamos si viene vacía
        tvCausa.text = if (causa.isNotEmpty()) "Causa: $causa" else ""
        tvLastUpdate.text = "Última actualización: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"

        // Umbrales de alerta según Arduino (IRH_ADVERTENCIA = 50, IRH_RIESGO = 75)
        when {
            irh >= PredictiveTheorem.IRH_RIESGO -> {
                tvAlerta.text = "⚠️ ¡RIESGO CRÍTICO!"
                tvAlerta.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                tvAlerta.setTextColor(Color.WHITE)
                if (lastAlertState != 2) {
                    sendNotification("🚨 RIESGO CRÍTICO", accion)
                    lastAlertState = 2
                }
            }
            irh >= PredictiveTheorem.IRH_ADVERTENCIA -> {
                tvAlerta.text = "⚠️ ADVERTENCIA"
                tvAlerta.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                tvAlerta.setTextColor(Color.BLACK)
                if (lastAlertState != 1) {
                    sendNotification("⚠️ Advertencia", accion)
                    lastAlertState = 1
                }
            }
            else -> {
                tvAlerta.text = "✅ Condiciones óptimas"
                tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                tvAlerta.setTextColor(Color.BLACK)
                lastAlertState = 0
            }
        }

        val reading = SensorReading(System.currentTimeMillis(), temp, hum, luz, irh, seq, somb, accion)
        lastReading = reading
        ioScope.launch {
            databaseLocal.sensorDao().insert(reading)
            databaseLocal.sensorDao().pruneData()
            
            // Ciclo de Auto-aprendizaje (Machine Learning)
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
}

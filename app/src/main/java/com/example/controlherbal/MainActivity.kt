package com.example.controlherbal

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import org.tensorflow.lite.Interpreter
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "Control Herbal"
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
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var lastChartUpdate: Long = 0
    private var tflite: Interpreter? = null

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
            Log.e(TAG, "IA: Modelo no encontrado. Usando lógica base.")
        }
    }

    private fun predictWithAI(temp: Double, hum: Double, luz: Int): Pair<Double, Double> {
        if (tflite != null) {
            val input = arrayOf(floatArrayOf(temp.toFloat(), hum.toFloat(), luz.toFloat()))
            val output = arrayOf(floatArrayOf(0f))
            tflite?.run(input, output)
            val irhIA = output[0][0].toDouble()
            val seqIA = 12.0 - (irhIA / 10.0)
            return Pair(seqIA.coerceIn(1.0, 12.0), 6.0)
        }
        val factorRiesgo = (temp * 0.4) + ((100 - hum) * 0.3) + (luz * 0.3)
        val seqIA = 12.0 - (factorRiesgo / 10.0)
        val sombIA = if (luz > 80) 2.0 else 6.0
        return Pair(seqIA.coerceIn(1.0, 12.0), sombIA.coerceIn(1.0, 12.0))
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

        ioScope.launch {
            loadModel()
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
                    val irh = snapshot.child("irh").getValue(Double::class.java) ?: 0.0
                    val seq = snapshot.child("seq").getValue(Double::class.java) ?: 0.0
                    val somb = snapshot.child("somb").getValue(Double::class.java) ?: 0.0
                    val accion = snapshot.child("acc").getValue(String::class.java) ?: "Sin datos"
                    val causa = snapshot.child("cau").getValue(String::class.java) ?: "Evaluando..."

                    ioScope.launch {
                        val prediccionIA = predictWithAI(temp, hum, luz)
                        val seqRef = (seq + prediccionIA.first) / 2
                        withContext(Dispatchers.Main) {
                            updateUIAndSave(temp, hum, luz, irh, seqRef, somb, accion, causa)
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
        tvSeq.text = if (seq > 0) String.format("%.1f h", seq) else "Sin riesgo"
        tvSomb.text = if (somb > 0) String.format("%.1f h", somb) else "Sin necesidad"
        tvAccion.text = accion
        tvCausa.text = "Causa: $causa"
        tvLastUpdate.text = "Última actualización: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"

        when {
            irh > 75 -> {
                tvAlerta.text = "⚠️ ¡RIESGO CRÍTICO!"
                tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                tvAlerta.setTextColor(Color.WHITE)
                if (lastAlertState != 2) {
                    sendNotification("🚨 RIESGO CRÍTICO", "Acción: $accion")
                    lastAlertState = 2
                }
            }
            irh > 25 -> {
                tvAlerta.text = "⚠️ ADVERTENCIA"
                tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                tvAlerta.setTextColor(Color.BLACK)
                if (lastAlertState != 1) {
                    sendNotification("⚠️ Advertencia", "Acción: $accion")
                    lastAlertState = 1
                }
            }
            else -> {
                tvAlerta.text = "✅ Condiciones óptimas"
                tvAlerta.setBackgroundColor(Color.TRANSPARENT)
                tvAlerta.setTextColor(Color.BLACK)
                lastAlertState = 0
            }
        }

        val reading = SensorReading(System.currentTimeMillis(), temp, hum, luz, irh, seq, somb, accion)
        ioScope.launch {
            databaseLocal.sensorDao().insert(reading)
            databaseLocal.sensorDao().pruneData()
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

        val tS = LineDataSet(tE, "Temp").apply { color = Color.RED; setDrawCircles(false) }
        val hS = LineDataSet(hE, "Hum").apply { color = Color.BLUE; setDrawCircles(false) }
        val lS = LineDataSet(lE, "Luz").apply { color = Color.YELLOW; setDrawCircles(false) }

        lineChart.apply {
            data = LineData(tS, hS, lS)
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

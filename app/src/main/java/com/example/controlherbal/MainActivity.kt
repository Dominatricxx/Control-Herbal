package com.example.controlherbal

import android.Manifest
import android.animation.ValueAnimator
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
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.database.Plant
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
    private lateinit var ivGrowthPlant: ImageView
    private var tvPlantNameAndEmoji: TextView? = null
    private var layoutPlantInfo: View? = null
    private lateinit var btnAddPlant: ImageButton

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
    private var currentPlant: Plant? = null
    private var firebaseListener: ValueEventListener? = null
    
    // BANDERA CRÍTICA DE VINCULACIÓN
    private var isLinkingInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        databaseLocal = SensorDatabase.getInstance(this)
        herbalAI = HerbalAI(this)

        ioScope.launch {
            val plant = databaseLocal.plantDao().getSelectedPlant()
            val plantCount = databaseLocal.plantDao().getPlantCount()
            
            withContext(Dispatchers.Main) {
                if (plant == null) {
                    val intent = Intent(this@MainActivity, PlantSetupActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    currentPlant = plant
                    initializeUI()
                    updateMenuVisibility(plantCount)
                    setupFirebase()
                }
            }
        }
    }

    private fun initializeUI() {
        setContentView(R.layout.activity_main_drawer)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        
        // Aplicar color rojo al menú de eliminar planta
        colorDeleteMenuItem(navView)

        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.menu.findItem(R.id.nav_main).isVisible = false

        tvConnectionState = findViewById(R.id.tvConnectionState)
        tvConnectionState.text = "Desincronizado"
        tvConnectionState.setTextColor(Color.RED)

        tvTemp = findViewById(R.id.tvTemp)
        tvHum = findViewById(R.id.tvHum)
        tvLuz = findViewById(R.id.tvLuz)
        tvIRH = findViewById(R.id.tvIRH)
        tvSeq = findViewById(R.id.tvSeq)
        tvSomb = findViewById(R.id.tvSomb)
        tvAccion = findViewById(R.id.tvAccion)
        tvCausa = findViewById(R.id.tvCausa)
        
        tvAlerta = findViewById(R.id.tvAlerta)
        tvAlerta.text = "Esperando datos..."
        tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
        tvAlerta.setTextColor(Color.BLACK)

        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        lineChart = findViewById(R.id.lineChart)
        
        btnConnect = findViewById(R.id.btnConnect)
        btnConnect.text = "VINCULAR DISPOSITIVO"
        btnConnect.isEnabled = true
        
        ivGrowthPlant = findViewById<ImageView>(R.id.ivGrowthPlant)

        tvPlantNameAndEmoji = findViewById(R.id.tvPlantNameAndEmoji)
        layoutPlantInfo = findViewById(R.id.layoutPlantInfo)
        btnAddPlant = findViewById(R.id.btnAddPlant)

        showPlantInfo()

        btnAddPlant.setOnClickListener {
            val intent = Intent(this, PlantSetupActivity::class.java)
            startActivity(intent)
        }

        btnConnect.setOnClickListener { 
            startFirebaseListener() 
        }

        ioScope.launch {
            loadModel()
            currentPlant?.let { plant ->
                val readings = databaseLocal.sensorDao().getLast2000Asc(plant.id)
                if (readings.isNotEmpty()) {
                    lastReading = readings.last()
                }
            }
            withContext(Dispatchers.Main) {
                loadDataAndDrawChart()
            }
        }
        createNotificationChannel()
        requestNotificationPermission()
    }

    private fun colorDeleteMenuItem(navView: NavigationView) {
        val menu = navView.menu
        val deleteItem = menu.findItem(R.id.nav_delete_plant)
        val s = SpannableString(deleteItem.title)
        s.setSpan(ForegroundColorSpan(Color.RED), 0, s.length, 0)
        deleteItem.title = s
    }

    private fun updateMenuVisibility(plantCount: Int) {
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.menu.findItem(R.id.nav_plants).isVisible = plantCount >= 2
    }

    private fun setupFirebase() {
        try {
            databaseFirebase = FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/").getReference("sensor")
            databaseFirebase.keepSynced(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}")
        }
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = assets.openFd("herbal_model.tflite")
            val inputStream = java.io.FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
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
        currentPlant?.let { plant ->
            layoutPlantInfo?.visibility = View.VISIBLE
            
            val fullType = plant.type // Ej: Categoría: Flores | Tipo: Girasol 🌻 (Helianthus annuus)
            
            // Extraer Nombre Científico (entre paréntesis)
            val scientific = if (fullType.contains("(")) {
                fullType.substringAfter("(").substringBefore(")")
            } else ""

            // Extraer Categoría
            var category = if (fullType.contains("Categoría:")) {
                fullType.substringAfter("Categoría:").substringBefore("|").trim()
            } else ""
            
            // Inferencia y añadido de emojis para las categorías
            category = when {
                category.contains("Flor") || fullType.contains("🌻") || fullType.contains("🌹") || fullType.contains("🌷") -> "Flores 🌸"
                category.contains("Hierba") || fullType.contains("🌿") || fullType.contains("🍃") -> "Hierbas 🌿"
                category.contains("Medicinal") || category.contains("💊") -> "Medicinales 💊"
                category.contains("Huerto") || fullType.contains("🍅") || fullType.contains("🌶️") || fullType.contains("🍋") -> "Huerto 🍅"
                category.contains("Suculenta") || fullType.contains("🌵") -> "Suculentas 🌵"
                category.isNotEmpty() -> if (!category.contains(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]"))) "$category 🌱" else category
                else -> "Herbal 🌱"
            }

            // Extraer Nombre Común y Emoji (después de "Tipo: " y antes de " (")
            val commonPart = if (fullType.contains("Tipo:")) {
                fullType.substringAfter("Tipo:").substringBefore("(").trim()
            } else {
                // Para compatibilidad con registros que no tienen el nuevo formato
                fullType.substringBefore("(").trim()
            }
            
            val envText = plant.environment.split(" ").firstOrNull() ?: "Luz"
            val envEmoji = plant.environment.split(" ").lastOrNull() ?: "🌞"

            val titleText = plant.name
            val detailText = "$category   |   $commonPart   |   $envText $envEmoji"
            
            val fullDisplay = if (scientific.isNotEmpty()) {
                "$titleText\n$detailText\n($scientific)"
            } else {
                "$titleText\n$detailText"
            }

            val spannable = SpannableString(fullDisplay).apply {
                val firstLineEnd = titleText.length
                val secondLineEnd = firstLineEnd + 1 + detailText.length
                
                // Línea 1: Nombre de la planta (Tamaño normal/grande)
                
                // Línea 2: Categoría | Tipo | Ambiente (Tamaño pequeño)
                setSpan(RelativeSizeSpan(0.8f), firstLineEnd + 1, secondLineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(Color.DKGRAY), firstLineEnd + 1, secondLineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                // Línea 3: Nombre científico (Más pequeño y gris)
                if (scientific.isNotEmpty()) {
                    setSpan(RelativeSizeSpan(0.7f), secondLineEnd + 1, fullDisplay.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(Color.GRAY), secondLineEnd + 1, fullDisplay.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            
            tvPlantNameAndEmoji?.text = spannable
        }
    }

    private fun resetUIForLinking() {
        // Limpiar datos previos para evitar mostrar información falsa
        tvTemp.text = "-- °C"
        tvHum.text = "-- %"
        tvLuz.text = "-- %"
        tvIRH.text = "--"
        tvSeq.text = "-- h"
        tvSomb.text = "-- h"
        tvAccion.text = "--"
        
        tvAlerta.text = "Esperando datos..."
        tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
        tvAlerta.setTextColor(Color.BLACK)
        
        tvConnectionState.text = "Conectando..."
        tvConnectionState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
    }

    private fun startGrowthAnimation() {
        ivGrowthPlant.visibility = View.VISIBLE
        
        // Cargar el GIF usando Glide con configuración de animación infinita
        Glide.with(this)
            .asGif()
            .load(R.drawable.gif_planta)
            .into(ivGrowthPlant)
        
        val density = resources.displayMetrics.density
        // Ajustamos las posiciones para que nazca justo en el borde superior del botón
        val startY = 60f * density  // Posición bajo el borde (oculto en el "suelo")
        val endY = -15f * density   // Posición máxima de crecimiento

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200 // Un poco más lento para que sea orgánico
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                // Movimiento vertical: emerge del botón y vuelve a entrar
                ivGrowthPlant.translationY = startY - (progress * (startY - endY))
                
                // Efecto de escala opcional para que se vea que "crece" al subir
                val scale = 0.5f + (progress * 0.5f)
                ivGrowthPlant.scaleX = scale
                ivGrowthPlant.scaleY = scale
            }
        }
        ivGrowthPlant.tag = animator
        animator.start()
    }

    private fun stopGrowthAnimation() {
        (ivGrowthPlant.tag as? ValueAnimator)?.cancel()
        Glide.with(this).clear(ivGrowthPlant)
        ivGrowthPlant.visibility = View.GONE
    }

    private fun startFirebaseListener() {
        // ACTIVAR ESTADO DE VINCULACIÓN ESTRICTO
        isLinkingInProgress = true
        resetUIForLinking()
        startGrowthAnimation()
        
        btnConnect.text = "Vinculando a ESP-32..."
        btnConnect.isEnabled = false

        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                handler.removeCallbacks(syncTimeoutRunnable)
                handler.postDelayed(syncTimeoutRunnable, 5000)
                
                if (snapshot.exists()) {
                    val deviceName = snapshot.child("deviceName").getValue(String::class.java) ?: "ESP-32"
                    
                    // SOLO SALIMOS DEL ESTADO VINCULANDO SI HAY DATOS REALES
                    if (snapshot.hasChild("temp")) {
                        isLinkingInProgress = false
                        stopGrowthAnimation()
                        
                        if (isDisconnected) {
                            isDisconnected = false
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancel(SYNC_NOTIFICATION_ID)
                        }

                        // AHORA SÍ: ESTADO VINCULADO REAL
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
                    } else {
                        // Sigue buscando datos: Mantener estados de espera obligatorios
                        btnConnect.text = String.format("Vinculando a %s...", deviceName)
                        tvConnectionState.text = "Conectando..."
                        tvAlerta.text = "Esperando datos..."
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isLinkingInProgress = false
                stopGrowthAnimation()
                tvConnectionState.text = "Error de conexión"
                tvConnectionState.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                btnConnect.text = "CONECTAR AL DISPOSITIVO"
                btnConnect.isEnabled = true
            }
        }
        
        firebaseListener?.let { databaseFirebase.addValueEventListener(it) }
    }

    override fun onStop() {
        super.onStop()
        firebaseListener?.let { databaseFirebase.removeEventListener(it) }
        firebaseListener = null
    }

    private fun updateUIAndSave(temp: Double, hum: Double, luz: Int, irh: Double, seq: Double, somb: Double, accion: String) {
        // BLOQUEO ABSOLUTO: Si estamos vinculando, ignoramos cualquier intento de diagnóstico
        if (isLinkingInProgress) return

        val locale = Locale.getDefault()
        tvTemp.text = String.format(locale, "%.1f °C", temp)
        tvHum.text = String.format(locale, "%.1f %%", hum)
        tvLuz.text = String.format(locale, "%d %%", luz)
        tvIRH.text = String.format(locale, "%.1f", irh)
        tvSeq.text = if (seq < PredictiveTheorem.PREDICCION_MAX_HORAS) String.format(locale, "%.1f h", seq) else "Sin riesgo"
        tvSomb.text = if (somb < PredictiveTheorem.PREDICCION_MAX_HORAS) String.format(locale, "%.1f h", somb) else "Sin necesidad"
        
        tvAccion.text = accion
        tvAccion.setTextColor(ContextCompat.getColor(this, R.color.green_herbal))
        
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

        currentPlant?.let { plant ->
            val reading = SensorReading(System.currentTimeMillis(), plant.id, temp, hum, luz, irh, seq, somb, accion)
            lastReading = reading
            ioScope.launch {
                databaseLocal.sensorDao().insert(reading)
                databaseLocal.sensorDao().pruneData(plant.id)
                readingsSinceLastLearning++
                if (readingsSinceLastLearning >= LEARNING_THRESHOLD) {
                    readingsSinceLastLearning = 0
                    val history = databaseLocal.sensorDao().getLast2000Asc(plant.id)
                    herbalAI.performSelfLearning(history)
                }
                val curr = System.currentTimeMillis()
                if (curr - lastChartUpdate >= 30000) {
                    lastChartUpdate = curr
                    withContext(Dispatchers.Main) { loadDataAndDrawChart() }
                }
            }
        }
    }

    private fun handleDisconnection() {
        if (!isDisconnected) {
            isDisconnected = true
            isLinkingInProgress = false
            stopGrowthAnimation()
            tvConnectionState.text = "Desincronizado"
            tvConnectionState.setTextColor(Color.RED)
            btnConnect.text = "DESVINCULADO - RECONECTAR"
            btnConnect.isEnabled = true
            tvAlerta.text = "Esperando datos..."
            tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
            tvAlerta.setTextColor(Color.BLACK)
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
        currentPlant?.let { plant ->
            ioScope.launch {
                val readings = databaseLocal.sensorDao().getLast2000Asc(plant.id)
                withContext(Dispatchers.Main) {
                    if (readings.isNotEmpty()) drawChart(readings)
                    else lineChart.setNoDataText("Esperando datos...")
                }
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
            R.id.nav_daily -> intent.putExtra("HISTORY_TYPE", "Diario")
            R.id.nav_weekly -> intent.putExtra("HISTORY_TYPE", "Semanal")
            R.id.nav_monthly -> intent.putExtra("HISTORY_TYPE", "Mensual")
            R.id.nav_plants -> {
                showPlantsSelectionDialog()
                return true
            }
            R.id.nav_delete_plant -> {
                showDeletePlantDialog()
                return true
            }
        }
        startActivity(intent)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showDeletePlantDialog() {
        val plant = currentPlant ?: return
        AlertDialog.Builder(this)
            .setTitle("Eliminar planta")
            .setMessage("¿Estás seguro de que quieres eliminar a '${plant.name}'? Se perderán todos sus datos.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteCurrentPlant()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteCurrentPlant() {
        val plantToDelete = currentPlant ?: return
        ioScope.launch {
            val db = databaseLocal
            db.sensorDao().deleteAllByPlantId(plantToDelete.id)
            db.plantDao().delete(plantToDelete)
            
            val remainingPlants = db.plantDao().getAll()
            if (remainingPlants.isNotEmpty()) {
                db.plantDao().update(remainingPlants[0].copy(isSelected = true))
            }

            withContext(Dispatchers.Main) {
                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun showPlantsSelectionDialog() {
        ioScope.launch {
            val plants = databaseLocal.plantDao().getAll()
            
            withContext(Dispatchers.Main) {
                val builder = AlertDialog.Builder(this@MainActivity)
                val dialogView = layoutInflater.inflate(R.layout.dialog_rounded_list, null)
                builder.setView(dialogView)
                
                val dialog = builder.create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                
                val listView = dialogView.findViewById<android.widget.ListView>(R.id.dialogListView)
                val adapter = object : android.widget.ArrayAdapter<Plant>(this@MainActivity, R.layout.item_plant_selection, plants) {
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        val plant = getItem(position)
                        val typeEmoji = plant?.type?.split(" ")?.lastOrNull() ?: ""
                        view.text = "${plant?.name} $typeEmoji"
                        return view
                    }
                }
                
                listView.adapter = adapter
                listView.setOnItemClickListener { _, _, position, _ ->
                    selectPlant(plants[position])
                    dialog.dismiss()
                }
                
                dialog.show()
            }
        }
    }

    private fun selectPlant(plant: Plant) {
        ioScope.launch {
            databaseLocal.plantDao().deselectAll()
            databaseLocal.plantDao().update(plant.copy(isSelected = true))
            
            withContext(Dispatchers.Main) {
                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}

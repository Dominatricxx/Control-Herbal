package com.example.controlherbal

import android.Manifest
import android.animation.ObjectAnimator
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.logic.PredictiveTheorem
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
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
    private lateinit var tvSoil: TextView
    private lateinit var tvLuz: TextView
    private lateinit var tvIRH: TextView
    private lateinit var tvSeq: TextView
    private lateinit var tvSomb: TextView
    private lateinit var tvAccion: TextView
    private lateinit var tvCausa: TextView
    private lateinit var tvAlerta: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvWateringRecommended: TextView
    private lateinit var tvNextWateringTime: TextView
    private lateinit var combinedChart: CombinedChart
    private lateinit var cbTemp: android.widget.CheckBox
    private lateinit var cbHum: android.widget.CheckBox
    private lateinit var cbSoil: android.widget.CheckBox
    private lateinit var cbLuz: android.widget.CheckBox
    private lateinit var cbIRH: android.widget.CheckBox
    private lateinit var btnConnect: Button
    private lateinit var btnWatering: Button
    private var tvPlantNameAndEmoji: TextView? = null
    private var layoutPlantInfo: View? = null
    private lateinit var btnAddPlant: ImageButton
    private lateinit var btnResetData: Button
    private lateinit var btnDeletePlant: Button

    private lateinit var databaseFirebase: DatabaseReference
    private val CHANNEL_ID = "herbal_alerts_channel"
    private val NOTIFICATION_ID = 1001
    private val SYNC_NOTIFICATION_ID = 1002
    private var lastAlertState = 0 
    private var isDisconnected = false
    private var lastBootCount: Int = -1
    private val handler = Handler(Looper.getMainLooper())
    private val syncTimeoutRunnable = Runnable { handleDisconnection() }

    private lateinit var viewModel: SensorViewModel
    private lateinit var databaseLocal: SensorDatabase
    private lateinit var herbalAI: HerbalAI
    private var tflite: Interpreter? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var lastChartUpdate: Long = 0
    private var lastReading: SensorReading? = null
    private var readingsSinceLastLearning = 0
    private var currentPlant: Plant? = null
    private var firebaseListener: ValueEventListener? = null
    
    // BANDERA CRÍTICA DE VINCULACIÓN
    private var isLinkingInProgress = false
    private var isFirstPacketAfterLinking = false

    private var lastWateringAlertState = 0 // 0: nada, 1: recomendado, 2: sobre-riego

    private var lastProcessTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        //No llamar a setContentView todavía para evitar el parpadeo blanco/principal
        databaseLocal = SensorDatabase.getInstance(this)
        
        ioScope.launch {
            val plant = databaseLocal.plantDao().getSelectedPlant()
            val plantCount = databaseLocal.plantDao().getPlantCount()
            
            withContext(Dispatchers.Main) {
                if (plant == null) {
                    val intent = Intent(this@MainActivity, PlantSetupActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    setContentView(R.layout.activity_main_drawer)
                    currentPlant = plant
                    
                    try {
                        herbalAI = HerbalAI(this@MainActivity)
                        viewModel = androidx.lifecycle.ViewModelProvider(this@MainActivity).get(SensorViewModel::class.java)
                        
                        initializeUI()

                        // Observar cambios del ViewModel
                        lifecycleScope.launch {
                            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                                viewModel.uiState.collect { state ->
                                    state.analysisResult?.let { res ->
                                        updateUIAndSave(
                                            state.temp, state.hum, state.luz, state.soil,
                                            res.irh, res.seq, res.somb,
                                            res.recommendation, res.wateringRecommended, res.nextWateringHours
                                        )
                                    }
                                }
                            }
                        }

                        showPlantInfo()
                        updateMenuVisibility(plantCount)
                        setupFirebase()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in Main init: ${e.message}")
                    }
                }
            }
        }
    }

    private fun initializeUI() {
        // Ya no llamamos a setContentView aquí porque se llamó en onCreate
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.menu.findItem(R.id.nav_main).isVisible = false

        tvConnectionState = findViewById(R.id.tvConnectionState)
        // ... rest of initializeUI stays same ...
        tvConnectionState.text = "Desincronizado"
        tvConnectionState.setTextColor(Color.RED)

        tvTemp = findViewById(R.id.tvTemp)
        tvHum = findViewById(R.id.tvHum)
        tvSoil = findViewById(R.id.tvSoil)
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
        tvWateringRecommended = findViewById(R.id.tvWateringRecommended)
        tvNextWateringTime = findViewById(R.id.tvNextWateringTime)
        combinedChart = findViewById(R.id.combinedChart)
        
        cbTemp = findViewById(R.id.cbTemp)
        cbHum = findViewById(R.id.cbHum)
        cbSoil = findViewById(R.id.cbSoil)
        cbLuz = findViewById(R.id.cbLuz)
        cbIRH = findViewById(R.id.cbIRH)

        val chartListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ -> 
            loadDataAndDrawChart() 
        }
        cbTemp.setOnCheckedChangeListener(chartListener)
        cbHum.setOnCheckedChangeListener(chartListener)
        cbSoil.setOnCheckedChangeListener(chartListener)
        cbLuz.setOnCheckedChangeListener(chartListener)
        cbIRH.setOnCheckedChangeListener(chartListener)
        
        btnConnect = findViewById(R.id.btnConnect)
        btnConnect.text = "VINCULAR DISPOSITIVO"
        btnConnect.isEnabled = true

        btnWatering = findViewById(R.id.btnWatering)
        btnWatering.setOnClickListener { registerWatering() }

        tvPlantNameAndEmoji = findViewById(R.id.tvPlantNameAndEmoji)
        layoutPlantInfo = findViewById(R.id.layoutPlantInfo)
        btnAddPlant = findViewById(R.id.btnAddPlant)

        showPlantInfo()

        btnAddPlant.setOnClickListener {
            val intent = Intent(this, PlantSetupActivity::class.java)
            startActivity(intent)
        }

        btnResetData = findViewById(R.id.btnResetData)
        btnDeletePlant = findViewById(R.id.btnDeletePlant)
        
        btnResetData.setOnClickListener { showResetDataDialog() }
        btnDeletePlant.setOnClickListener { showDeletePlantDialog() }

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

    private fun updateMenuVisibility(plantCount: Int) {
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.menu.findItem(R.id.nav_plants).isVisible = plantCount >= 2
        navView.menu.findItem(R.id.nav_comparison).isVisible = plantCount >= 2
    }

    private fun setupFirebase() {
        try {
            // URL explícita para evitar errores de resolución automática
            val dbInstance = FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/")
            databaseFirebase = dbInstance.getReference("sensor")
            
            // Habilitar persistencia y sincronización para mayor estabilidad
            databaseFirebase.keepSynced(true)
            
            Log.d(TAG, "Firebase configurado en path: sensor")
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

    private fun registerWatering() {
        val plant = currentPlant ?: return
        val now = System.currentTimeMillis()
        
        ioScope.launch {
            val updatedPlant = plant.copy(lastWateringTime = now, pendingSync = true)
            databaseLocal.plantDao().update(updatedPlant)
            currentPlant = updatedPlant
            
            // Programar sincronización
            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.controlherbal.sync.WateringSyncWorker>()
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build())
                .build()
            androidx.work.WorkManager.getInstance(this@MainActivity).enqueue(syncRequest)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Riego registrado (se sincronizará en cuanto tengas conexión) 💧", Toast.LENGTH_SHORT).show()
                // Solo forzar re-análisis si hay una lectura real (no vacía)
                if (tvTemp.text.isNotEmpty() && tvTemp.text != "--") {
                    lastReading?.let { r ->
                        val res = PredictiveTheorem.analyze(
                            r.temperature, r.humidity, r.light, r.soilMoisture,
                            lastWateringTime = System.currentTimeMillis(),
                            plantType = currentPlant?.type ?: ""
                        )
                        updateUIAndSave(r.temperature, r.humidity, r.light, r.soilMoisture, res.irh, res.seq, res.somb, res.recommendation, res.wateringRecommended, res.nextWateringHours)
                    }
                }
            }
        }
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
        // LIMPIEZA ABSOLUTA: Los campos deben quedar vacíos hasta que lleguen datos reales del ESP-32
        tvTemp.text = ""
        tvHum.text = ""
        tvLuz.text = ""
        tvIRH.text = ""
        tvSeq.text = ""
        tvSomb.text = ""
        tvAccion.text = ""
        tvWateringRecommended.text = ""
        tvNextWateringTime.text = ""
        
        tvAlerta.text = "Sincronizando..."
        tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
        tvAlerta.setTextColor(Color.BLACK)
        
        tvConnectionState.text = "Conectando..."
        tvConnectionState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
    }

    private fun startFirebaseListener() {
        Log.d(TAG, "Iniciando vinculación...")
        isLinkingInProgress = true
        isFirstPacketAfterLinking = true
        isDisconnected = false
        resetUIForLinking()
        
        btnConnect.text = "Vinculando a ESP-32..."
        btnConnect.isEnabled = false

        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.exists()) {
                        val boot = (snapshot.child("boot").value as? Number)?.toInt() ?: -1
                        
                        // Detectar si el dato es "fresco" (ha cambiado el ciclo desde la última vez)
                        val isDataChanging = lastBootCount != -1 && boot != lastBootCount

                        if (isDataChanging) {
                            handler.removeCallbacks(syncTimeoutRunnable)
                            handler.postDelayed(syncTimeoutRunnable, 10000)
                            
                            if (isDisconnected) {
                                isDisconnected = false
                                runOnUiThread {
                                    tvConnectionState.text = "Sincronizado"
                                    tvConnectionState.setTextColor(Color.parseColor("#2E7D32"))
                                    btnConnect.text = "DISPOSITIVO VINCULADO ✅"
                                }
                            }
                        }

                        if (isLinkingInProgress) {
                            if (isFirstPacketAfterLinking) {
                                // Primer paquete recibido: lo guardamos pero no damos por terminada la vinculación
                                // para evitar usar datos "stale" (viejos) de la base de datos de Firebase.
                                lastBootCount = boot
                                isFirstPacketAfterLinking = false
                                runOnUiThread {
                                    btnConnect.text = "Esperando señal..."
                                    tvConnectionState.text = "Conectando..."
                                }
                                return // No procesamos datos todavía
                            } else if (boot != lastBootCount) {
                                // El boot ha cambiado: el dispositivo está enviando datos en vivo
                                isLinkingInProgress = false
                                runOnUiThread {
                                    btnConnect.text = "DISPOSITIVO VINCULADO ✅"
                                    tvConnectionState.text = "Sincronizado"
                                    tvConnectionState.setTextColor(Color.parseColor("#2E7D32"))
                                }
                            } else {
                                // Sigue siendo el mismo paquete (posiblemente viejo), esperamos.
                                return
                            }
                        }

                        // A partir de aquí, proceso normal de datos
                        val temp = (snapshot.child("temp").value as? Number)?.toDouble() ?: 0.0
                        val hum = (snapshot.child("hum").value as? Number)?.toDouble() ?: 0.0
                        val luz = (snapshot.child("luz").value as? Number)?.toInt() ?: 0
                        val soil = (snapshot.child("soil").value as? Number)?.toDouble() ?: 0.0
                        val irh = (snapshot.child("irh").value as? Number)?.toDouble() ?: -1.0
                        val seq = (snapshot.child("seq").value as? Number)?.toDouble() ?: -1.0
                        val somb = (snapshot.child("somb").value as? Number)?.toDouble() ?: -1.0
                        val acc = snapshot.child("acc").value as? String ?: ""
                        val sensorsOk = (snapshot.child("sensores_ok").value as? Number)?.toInt() ?: 1
                        
                        if (temp == 0.0 && hum == 0.0 && luz == 0 && soil == 0.0) return

                        if (lastBootCount != -1 && boot > 0 && boot < lastBootCount) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "🔄 El dispositivo se ha reiniciado", Toast.LENGTH_SHORT).show()
                            }
                        }
                        lastBootCount = boot

                        if (sensorsOk == 0) {
                            runOnUiThread {
                                tvAlerta.text = "⚠️ FALLO DE SENSORES"
                                tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.RED)
                                tvAlerta.setTextColor(Color.WHITE)
                            }
                        }

                        viewModel.updateFromFirebase(temp, hum, luz, soil, currentPlant, lastReading, irh, seq, somb, acc)
                    } else {
                        Log.e(TAG, "El snapshot no existe en el path 'sensor'")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onDataChange error crítico: ${e.message}")
                    e.printStackTrace()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase cancelado: ${error.message}")
                isLinkingInProgress = false
                runOnUiThread {
                    tvConnectionState.text = "Error de conexión"
                    tvConnectionState.setTextColor(Color.RED)
                    btnConnect.text = "REINTENTAR VINCULACIÓN"
                    btnConnect.isEnabled = true
                }
            }
        }
        
        firebaseListener?.let { databaseFirebase.addValueEventListener(it) }
    }

    override fun onStop() {
        super.onStop()
        try {
            firebaseListener?.let { listener ->
                if (::databaseFirebase.isInitialized) {
                    databaseFirebase.removeEventListener(listener)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing listener: ${e.message}")
        }
        firebaseListener = null
    }

    private fun updateUIAndSave(temp: Double, hum: Double, luz: Int, soil: Double, irh: Double, seq: Double, somb: Double, accion: String, wateringRecommended: Boolean, nextWateringHours: Double) {
        // BLOQUEO ABSOLUTO: Si estamos vinculando, NO actualizar interfaz con datos
        if (isLinkingInProgress) return
        
        // Verificación extra: si todos los valores son 0, probablemente es un dato fantasma, ignorar
        if (temp == 0.0 && hum == 0.0 && luz == 0 && soil == 0.0) return

        val locale = Locale.getDefault()
        tvTemp.text = String.format(locale, "%.1f °C", temp)
        tvHum.text = String.format(locale, "%.1f %%", hum)
        tvSoil.text = String.format(locale, "%.1f %%", soil)
        tvLuz.text = String.format(locale, "%d %%", luz)
        tvIRH.text = String.format(locale, "%.1f", irh)
        tvSeq.text = if (seq < PredictiveTheorem.PREDICCION_MAX_HORAS) String.format(locale, "%.1f h", seq) else "Sin riesgo"
        tvSomb.text = if (somb < PredictiveTheorem.PREDICCION_MAX_HORAS) String.format(locale, "%.1f h", somb) else "Sin necesidad"
        
        tvWateringRecommended.text = "Riego recomendado: ${if (wateringRecommended) "SÍ" else "No"}"
        tvWateringRecommended.setTextColor(if (wateringRecommended) Color.RED else Color.parseColor("#1E88E5"))
        
        if (nextWateringHours <= 0) {
            tvNextWateringTime.text = "¡Necesita riego ahora!"
        } else {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, (nextWateringHours * 60).toInt())
            val timeStr = SimpleDateFormat("HH:mm", locale).format(calendar.time)
            tvNextWateringTime.text = "Próximo riego estimado: $timeStr"
        }
        
        tvAccion.text = accion
        
        // Notificaciones Push para Riego
        handleWateringNotifications(wateringRecommended, accion)

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
            val reading = SensorReading(System.currentTimeMillis(), plant.id, temp, hum, luz, soil, irh, seq, somb, accion)
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

    private fun handleWateringNotifications(recommended: Boolean, action: String) {
        if (recommended && lastWateringAlertState != 1) {
            sendNotification("💧 Riego Recomendado", "Tu planta necesita hidratación según el análisis de la IA.")
            lastWateringAlertState = 1
        } else if (action.contains("SOBRE-RIEGO") && lastWateringAlertState != 2) {
            sendNotification("🛑 Alerta de Sobre-Riego", "¡Cuidado! Estás regando demasiado. Riesgo de asfixia radicular.")
            lastWateringAlertState = 2
        } else if (!recommended && !action.contains("SOBRE-RIEGO")) {
            lastWateringAlertState = 0
        }
    }

    private fun handleDisconnection() {
        if (!isDisconnected) {
            isDisconnected = true
            isLinkingInProgress = false
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
                    else combinedChart.setNoDataText("Esperando datos...")
                }
            }
        }
    }

    private fun drawChart(readings: List<SensorReading>) {
        val combinedData = com.github.mikephil.charting.data.CombinedData()
        
        // Líneas
        val lineData = com.github.mikephil.charting.data.LineData()
        
        if (cbTemp.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
            lineData.addDataSet(LineDataSet(entries, "Temp").apply { color = Color.RED; setDrawCircles(false); lineWidth = 2f })
        }
        if (cbHum.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
            lineData.addDataSet(LineDataSet(entries, "Hum").apply { color = Color.BLUE; setDrawCircles(false); lineWidth = 2f })
        }
        if (cbSoil.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }
            lineData.addDataSet(LineDataSet(entries, "Suelo").apply { color = Color.parseColor("#2E7D32"); setDrawCircles(false); lineWidth = 2.5f })
        }
        if (cbLuz.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
            lineData.addDataSet(LineDataSet(entries, "Luz").apply { color = Color.rgb(255, 215, 0); setDrawCircles(false); lineWidth = 2f })
        }
        
        combinedData.setData(lineData)

        // Barras (IRH)
        if (cbIRH.isChecked) {
            val barEntries = readings.mapIndexed { i, r -> com.github.mikephil.charting.data.BarEntry(i.toFloat(), r.irh.toFloat()) }
            val barDataSet = com.github.mikephil.charting.data.BarDataSet(barEntries, "IRH").apply {
                color = Color.argb(150, 211, 47, 47) // Rojo traslúcido
                setDrawValues(false)
            }
            combinedData.setData(com.github.mikephil.charting.data.BarData(barDataSet))
        }

        combinedChart.apply {
            data = combinedData
            description.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = object : ValueFormatter() {
                private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                override fun getAxisLabel(v: Float, a: AxisBase?): String {
                    val idx = v.toInt()
                    return if (idx in readings.indices) sdf.format(Date(readings[idx].timestamp)) else ""
                }
            }
            axisRight.isEnabled = false
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
            R.id.nav_comparison -> {
                startActivity(Intent(this, ComparisonActivity::class.java))
                return true
            }
        }
        startActivity(intent)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showResetDataDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_reset_options, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvResetTitle)
        tvTitle.text = "Reiniciar de '${plant.name}'"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnReset24h).setOnClickListener {
            resetData(plant.id, System.currentTimeMillis() - (24 * 3600 * 1000L), System.currentTimeMillis(), false)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnReset7d).setOnClickListener {
            resetData(plant.id, System.currentTimeMillis() - (7 * 24 * 3600 * 1000L), System.currentTimeMillis(), false)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnResetMonth).setOnClickListener {
            resetData(plant.id, System.currentTimeMillis() - (30 * 24 * 3600 * 1000L), System.currentTimeMillis(), false)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnResetAll).setOnClickListener {
            resetData(plant.id, 0, System.currentTimeMillis(), true)
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnCancelReset).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun resetData(plantId: Int, start: Long, end: Long, isAll: Boolean) {
        ioScope.launch {
            if (isAll) {
                databaseLocal.sensorDao().deleteAllByPlantId(plantId)
            } else {
                databaseLocal.sensorDao().deleteReadingsBetween(plantId, start, end)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Datos eliminados correctamente", Toast.LENGTH_SHORT).show()
                loadDataAndDrawChart()
            }
        }
    }

    private fun showDeletePlantDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnAction = dialogView.findViewById<Button>(R.id.btnAction)

        tvTitle.text = "Eliminar planta"
        tvMessage.text = "¿Estás seguro de que quieres eliminar a '${plant.name}'? Se perderán todos sus datos."
        btnAction.text = "Eliminar"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnAction.setOnClickListener {
            deleteCurrentPlant()
            dialog.dismiss()
        }
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
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
                        val row = convertView ?: layoutInflater.inflate(R.layout.item_plant_selection, parent, false)
                        val plant = getItem(position) ?: return row

                        val tvName = row.findViewById<TextView>(R.id.tvItemPlantName)
                        val tvDetails = row.findViewById<TextView>(R.id.tvItemPlantDetails)
                        val tvScientific = row.findViewById<TextView>(R.id.tvItemPlantScientific)

                        tvName.text = plant.name

                        val fullType = plant.type
                        val category = if (fullType.contains("Categoría:")) fullType.substringAfter("Categoría:").substringBefore("|").trim() else ""
                        val typePart = if (fullType.contains("Tipo:")) fullType.substringAfter("Tipo:").substringBefore("(").trim() else fullType.substringBefore("(")
                        val scientific = if (fullType.contains("(")) fullType.substringAfter("(").substringBefore(")") else ""

                        tvDetails.text = "${if (category.isNotEmpty()) "$category | " else ""}$typePart | ${plant.environment}"
                        tvScientific.text = if (scientific.isNotEmpty()) "($scientific)" else ""
                        tvScientific.visibility = if (scientific.isNotEmpty()) View.VISIBLE else View.GONE

                        return row
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
            
            // Sincronizar tipo de planta con ESP-32 vía Firebase
            // 1: Luz, 2: Híbrida, 3: Sombra
            val tipoInt = when {
                plant.environment.contains("Luz", ignoreCase = true) -> 1
                plant.environment.contains("Sombra", ignoreCase = true) -> 3
                else -> 2 // Híbrida
            }
            
            Log.d(TAG, "Sincronizando tipo de planta con Firebase: $tipoInt para ${plant.name}")
            FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/")
                .getReference("config/tipoPlanta").setValue(tipoInt)

            withContext(Dispatchers.Main) {
                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}

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
import com.example.controlherbal.ChatActivity
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
    private lateinit var btnEditNameIcon: ImageButton
    private lateinit var btnDataControl: Button
    private lateinit var btnDeletePlant: Button
    private lateinit var cardAiDiagnosis: androidx.cardview.widget.CardView
    private lateinit var tvAiDiagnosisTitle: TextView
    private lateinit var tvAiDiagnosisBody: TextView
    private lateinit var btnClearAiDiagnosis: Button

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
    
    private var isLinkingInProgress = false
    private var isFirstPacketAfterLinking = false

    private var lastWateringAlertState = 0 
    private var lastRecommendationText = "" 

    private var lastProcessTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        databaseLocal = SensorDatabase.getInstance(this)
        setContentView(R.layout.activity_main_drawer)
        initializeUI()
        
        lifecycleScope.launch {
            val plant = withContext(Dispatchers.IO) {
                databaseLocal.plantDao().getSelectedPlant()
            }
            val plantCount = withContext(Dispatchers.IO) {
                databaseLocal.plantDao().getPlantCount()
            }
            
            if (plant == null) {
                val intent = Intent(this@MainActivity, PlantSetupActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                currentPlant = plant
                startSensorService()
                
                try {
                    herbalAI = HerbalAI(this@MainActivity)
                    viewModel = androidx.lifecycle.ViewModelProvider(this@MainActivity).get(SensorViewModel::class.java)
                    viewModel.observeLatestReading(plant.id)

                    lifecycleScope.launch {
                        repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                            viewModel.uiState.collect { state ->
                                if (state.isLinking) {
                                    showLinkingState()
                                } else if (state.isConnected && state.analysisResult != null) {
                                    val res = state.analysisResult
                                    updateUIAndSave(
                                        state.temp, state.hum, state.luz, state.soil,
                                        res.irh, res.seq, res.somb,
                                        res.recommendation, res.wateringRecommended, res.nextWateringHours
                                    )
                                } else if (!isLinkingInProgress) {
                                    showDisconnectedState()
                                }
                            }
                        }
                    }

                    showPlantInfo()
                    updateMenuVisibility(plantCount)
                    setupFirebase()
                } catch (e: Exception) {
                    Log.e(TAG, "Error: ${e.message}")
                }
            }
        }
    }

    private fun initializeUI() {
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
        tvSoil = findViewById(R.id.tvSoil)
        tvLuz = findViewById(R.id.tvLuz)
        tvIRH = findViewById(R.id.tvIRH)
        tvSeq = findViewById(R.id.tvSeq)
        tvSomb = findViewById(R.id.tvSomb)
        tvAccion = findViewById(R.id.tvAccion)
        tvCausa = findViewById(R.id.tvCausa)
        tvAlerta = findViewById(R.id.tvAlerta)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvWateringRecommended = findViewById(R.id.tvWateringRecommended)
        tvNextWateringTime = findViewById(R.id.tvNextWateringTime)
        combinedChart = findViewById(R.id.combinedChart)
        
        cbTemp = findViewById(R.id.cbTemp)
        cbHum = findViewById(R.id.cbHum)
        cbSoil = findViewById(R.id.cbSoil)
        cbLuz = findViewById(R.id.cbLuz)
        cbIRH = findViewById(R.id.cbIRH)

        val chartListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ -> loadDataAndDrawChart() }
        cbTemp.setOnCheckedChangeListener(chartListener)
        cbHum.setOnCheckedChangeListener(chartListener)
        cbSoil.setOnCheckedChangeListener(chartListener)
        cbLuz.setOnCheckedChangeListener(chartListener)
        cbIRH.setOnCheckedChangeListener(chartListener)
        
        btnConnect = findViewById(R.id.btnConnect)
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

        btnDataControl = findViewById(R.id.btnDataControl)
        btnEditNameIcon = findViewById(R.id.btnEditNameIcon)
        btnDeletePlant = findViewById(R.id.btnDeletePlant)
        
        cardAiDiagnosis = findViewById(R.id.cardAiDiagnosis)
        tvAiDiagnosisTitle = findViewById(R.id.tvAiDiagnosisTitle)
        tvAiDiagnosisBody = findViewById(R.id.tvAiDiagnosisBody)
        btnClearAiDiagnosis = findViewById(R.id.btnClearAiDiagnosis)

        btnClearAiDiagnosis.setOnClickListener {
            clearAiDiagnosis()
        }
        
        showDisconnectedState()
        
        btnDataControl.setOnClickListener { showDataControlDialog() }
        tvPlantNameAndEmoji?.setOnClickListener {
            btnEditNameIcon.visibility = if (btnEditNameIcon.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        btnEditNameIcon.setOnClickListener { showEditNameDialog() }
        btnDeletePlant.setOnClickListener { showDeletePlantDialog() }
        btnConnect.setOnClickListener { startFirebaseListener() }

        ioScope.launch {
            loadModel()
            currentPlant?.let { plant ->
                val readings = databaseLocal.sensorDao().getLast2000Asc(plant.id)
                if (readings.isNotEmpty()) lastReading = readings.last()
            }
            withContext(Dispatchers.Main) { loadDataAndDrawChart() }
        }
        createNotificationChannel()
        requestNotificationPermission()
    }

    private fun startSensorService() {
        val intent = Intent(this, com.example.controlherbal.sync.SensorForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateMenuVisibility(plantCount: Int) {
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.menu.findItem(R.id.nav_plants).isVisible = plantCount >= 2
        navView.menu.findItem(R.id.nav_comparison).isVisible = plantCount >= 2
    }

    private fun setupFirebase() {
        try {
            val dbInstance = FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/")
            databaseFirebase = dbInstance.getReference("sensor")
            databaseFirebase.keepSynced(true)
            
            // Forzar una lectura inicial para asegurar que el SDK esté conectado
            databaseFirebase.get().addOnSuccessListener { 
                Log.d(TAG, "Firebase conectado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Error: ${e.message}")
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
            Log.e(TAG, "TFLite Error")
        }
    }

    private fun registerWatering() {
        val plant = currentPlant ?: return
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnAction = dialogView.findViewById<Button>(R.id.btnAction)

        tvTitle.text = "Riego Manual 💧"
        tvMessage.text = "¿Deseas activar el sistema de riego automático para '${plant.name}'?"
        btnAction.text = "ACTIVAR"
        btnAction.setBackgroundColor(Color.parseColor("#1E88E5"))
        
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnAction.setOnClickListener {
            val now = System.currentTimeMillis()
            ioScope.launch {
                // 1. Registro Local
                val updatedPlant = plant.copy(lastWateringTime = now, pendingSync = true)
                databaseLocal.plantDao().update(updatedPlant)
                currentPlant = updatedPlant
                
                // 2. Control Proyecto B
                try {
                    val controlRef = FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/").getReference("control/riego")
                    
                    val duracion = when {
                        plant.environment.contains("Luz", ignoreCase = true) -> 10
                        plant.environment.contains("Sombra", ignoreCase = true) -> 30
                        else -> 20
                    }
                    
                    val command = mapOf(
                        "activar" to 1,
                        "duracion" to duracion,
                        "timestamp" to ServerValue.TIMESTAMP,
                        "fuente" to "App_Android"
                    )
                    controlRef.setValue(command)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error Proyecto B: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Comando enviado al Proyecto B 💧", Toast.LENGTH_SHORT).show()
                    btnWatering.isEnabled = false
                    handler.postDelayed({ btnWatering.isEnabled = true }, 10000)
                    dialog.dismiss()
                }
            }
        }
        
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPlantInfo() {
        currentPlant?.let { plant ->
            layoutPlantInfo?.visibility = View.VISIBLE
            val fullType = plant.type
            val scientific = if (fullType.contains("(")) fullType.substringAfter("(").substringBefore(")") else ""
            var category = if (fullType.contains("Categoría:")) fullType.substringAfter("Categoría:").substringBefore("|").trim() else ""
            category = when {
                category.contains("Flor") || fullType.contains("🌻") || fullType.contains("🌹") || fullType.contains("🌷") -> "Flores 🌸"
                category.contains("Hierba") || fullType.contains("🌿") || fullType.contains("🍃") -> "Hierbas 🌿"
                category.contains("Medicinal") || category.contains("💊") -> "Medicinales 💊"
                category.contains("Huerto") || fullType.contains("🍅") || fullType.contains("🌶️") || fullType.contains("🍋") -> "Huerto 🍅"
                category.contains("Suculenta") || fullType.contains("🌵") -> "Suculentas 🌵"
                else -> "Herbal 🌱"
            }
            val commonPart = if (fullType.contains("Tipo:")) fullType.substringAfter("Tipo:").substringBefore("(").trim() else fullType.substringBefore("(").trim()
            val envText = plant.environment.split(" ").firstOrNull() ?: "Luz"
            val envEmoji = plant.environment.split(" ").lastOrNull() ?: "🌞"

            val titleText = plant.name
            val detailText = "$category   |   $commonPart   |   $envText $envEmoji"
            val fullDisplay = if (scientific.isNotEmpty()) "$titleText\n$detailText\n($scientific)" else "$titleText\n$detailText"

            val spannable = SpannableString(fullDisplay).apply {
                val firstLineEnd = titleText.length
                val secondLineEnd = firstLineEnd + 1 + detailText.length
                setSpan(RelativeSizeSpan(0.8f), firstLineEnd + 1, secondLineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(Color.DKGRAY), firstLineEnd + 1, secondLineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (scientific.isNotEmpty()) {
                    setSpan(RelativeSizeSpan(0.7f), secondLineEnd + 1, fullDisplay.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(Color.GRAY), secondLineEnd + 1, fullDisplay.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            tvPlantNameAndEmoji?.text = spannable

            // Mostrar diagnóstico IA si existe
            if (!plant.aiDiagnosis.isNullOrEmpty()) {
                cardAiDiagnosis.visibility = View.VISIBLE
                tvAiDiagnosisTitle.text = plant.aiDiagnosis
                tvAiDiagnosisBody.text = plant.aiRecommendation
            } else {
                cardAiDiagnosis.visibility = View.GONE
            }
        }
    }

    private fun clearAiDiagnosis() {
        val plant = currentPlant ?: return
        ioScope.launch {
            val updated = plant.copy(aiDiagnosis = null, aiRecommendation = null)
            databaseLocal.plantDao().update(updated)
            currentPlant = updated
            withContext(Dispatchers.Main) {
                cardAiDiagnosis.visibility = View.GONE
            }
        }
    }

    private fun showLinkingState() {
        runOnUiThread {
            tvTemp.text = "--"
            tvHum.text = "--"
            tvSoil.text = "--"
            tvLuz.text = "--"
            tvIRH.text = "--"
            tvSeq.text = "--"
            tvSomb.text = "--"
            tvAccion.text = "Sincronizando..."
            tvWateringRecommended.text = ""
            tvNextWateringTime.text = ""
            tvAlerta.text = "Esperando datos..."
            tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
            tvAlerta.setTextColor(Color.BLACK)
            tvConnectionState.text = "Vinculando..."
            tvConnectionState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            btnConnect.text = "VINCULANDO..."
            btnConnect.isEnabled = false
        }
    }

    private fun showDisconnectedState() {
        tvConnectionState.text = "Desconectado"
        tvConnectionState.setTextColor(Color.RED)
        btnConnect.text = "VINCULAR DISPOSITIVO"
        btnConnect.isEnabled = true
        tvAlerta.text = "Sin conexión"
        tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
        tvAlerta.setTextColor(Color.BLACK)
    }

    private fun startFirebaseListener() {
        // Ahora el servicio en primer plano es el único responsable de Firebase.
        // Vincular simplemente se asegura de que el servicio esté corriendo.
        isLinkingInProgress = true
        showLinkingState()
        
        startSensorService()
        
        // Simulamos vinculación para UX, la UI se actualizará vía Room
        handler.postDelayed({
            isLinkingInProgress = false
            runOnUiThread {
                tvConnectionState.text = "Sincronizado (Segundo Plano)"
                tvConnectionState.setTextColor(Color.parseColor("#2E7D32"))
                btnConnect.text = "DISPOSITIVO VINCULADO ✅"
            }
        }, 2000)
    }

    override fun onStop() {
        super.onStop()
        firebaseListener?.let { if (::databaseFirebase.isInitialized) databaseFirebase.removeEventListener(it) }
        firebaseListener = null
    }

    private fun updateUIAndSave(temp: Double, hum: Double, luz: Double, soil: Double, irh: Double, seq: Double, somb: Double, accion: String, wateringRecommended: Boolean, nextWateringHours: Double) {
        if (isLinkingInProgress) return
        if (temp == 0.0 && hum == 0.0 && luz == 0.0 && soil == 0.0) return

        val locale = Locale.getDefault()
        tvTemp.text = String.format(locale, "%.1f °C", temp)
        tvHum.text = String.format(locale, "%.1f %%", hum)
        tvSoil.text = String.format(locale, "%.1f %%", soil)
        tvLuz.text = String.format(locale, "%.1f %%", luz)
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
            val sdf = if (nextWateringHours > 20) SimpleDateFormat("EEE HH:mm", locale) else SimpleDateFormat("HH:mm", locale)
            tvNextWateringTime.text = "Próximo riego estimado: ${sdf.format(calendar.time)}"
        }
        
        tvAccion.text = accion
        if (accion != lastRecommendationText) {
            handleSignificantNotifications(wateringRecommended, accion)
            lastRecommendationText = accion
        }

        tvAccion.setTextColor(ContextCompat.getColor(this, R.color.green_herbal))
        tvCausa.text = ""
        tvLastUpdate.text = String.format("Última actualización: %s", SimpleDateFormat("HH:mm:ss", locale).format(Date()))

        val isStressful = irh >= PredictiveTheorem.IRH_OPTIMO || 
                         temp < PredictiveTheorem.TEMP_OPTIMA_MIN || 
                         temp > PredictiveTheorem.TEMP_OPTIMA_MAX || 
                         hum < PredictiveTheorem.HUM_OPTIMA_MIN || 
                         hum > PredictiveTheorem.HUM_OPTIMA_MAX

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
            val reading = SensorReading(
                timestamp = System.currentTimeMillis(),
                plantId = plant.id,
                temperature = temp,
                humidity = hum,
                light = luz,
                soilMoisture = soil,
                irh = irh,
                seq = seq,
                somb = somb,
                action = accion
            )
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

    private fun handleSignificantNotifications(recommended: Boolean, action: String) {
        if (recommended && lastWateringAlertState != 1) {
            sendNotification("💧 Riego Recomendado", "Tu planta necesita hidratación según el análisis de la IA.")
            lastWateringAlertState = 1
        } else if (action.contains("SOBRE-RIEGO") && lastWateringAlertState != 2) {
            sendNotification("🛑 Alerta de Sobre-Riego", "¡Cuidado! Estás regando demasiado.")
            lastWateringAlertState = 2
        } else if (!recommended && !action.contains("SOBRE-RIEGO")) lastWateringAlertState = 0

        if (!action.contains("óptimas", ignoreCase = true) && !action.contains("estables", ignoreCase = true)) {
            // Solo lanzamos alerta si no es exclusivamente por luz alta (☀️)
            val isOnlyLight = action.contains("☀️") && !action.contains("🌡️") && !action.contains("🔥") && !action.contains("💧")
            if (!isOnlyLight && (action.contains("⚠️") || action.contains("🌡️") || action.contains("🔥"))) {
                sendNotification("🌿 Actualización de Cuidados", action)
            }
        }
    }

    private fun handleDisconnection() {
        if (!isDisconnected) {
            isDisconnected = true
            isLinkingInProgress = false
            viewModel.setLinking(false)
            showDisconnectedState()
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
            val channel = NotificationChannel(CHANNEL_ID, "Alertas de Control Herbal", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun loadDataAndDrawChart() {
        currentPlant?.let { plant ->
            ioScope.launch {
                val readings = databaseLocal.sensorDao().getLast2000Asc(plant.id)
                withContext(Dispatchers.Main) {
                    if (readings.isNotEmpty()) {
                        drawChart(readings)
                    } else {
                        combinedChart.clear()
                        combinedChart.setNoDataText("Esperando datos...")
                        combinedChart.invalidate()
                    }
                }
            }
        }
    }

    private fun drawChart(readings: List<SensorReading>) {
        val combinedData = com.github.mikephil.charting.data.CombinedData()
        val lineData = com.github.mikephil.charting.data.LineData()
        if (cbTemp.isChecked) lineData.addDataSet(LineDataSet(readings.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }, "Temp").apply { color = Color.RED; setDrawCircles(false); lineWidth = 2f })
        if (cbHum.isChecked) lineData.addDataSet(LineDataSet(readings.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }, "Hum").apply { color = Color.BLUE; setDrawCircles(false); lineWidth = 2f })
        if (cbSoil.isChecked) lineData.addDataSet(LineDataSet(readings.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }, "Suelo").apply { color = Color.parseColor("#2E7D32"); setDrawCircles(false); lineWidth = 2.5f })
        if (cbLuz.isChecked) lineData.addDataSet(LineDataSet(readings.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }, "Luz").apply { color = Color.rgb(255, 215, 0); setDrawCircles(false); lineWidth = 2f })
        combinedData.setData(lineData)
        if (cbIRH.isChecked) {
            val barData = com.github.mikephil.charting.data.BarData(com.github.mikephil.charting.data.BarDataSet(readings.mapIndexed { i, r -> com.github.mikephil.charting.data.BarEntry(i.toFloat(), r.irh.toFloat()) }, "IRH").apply { color = Color.argb(150, 211, 47, 47); setDrawValues(false) })
            barData.barWidth = 0.5f
            combinedData.setData(barData)
        }
        
        combinedChart.apply {
            data = combinedData
            description.isEnabled = false
            setExtraOffsets(5f, 5f, 5f, 15f)
            
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 105f
            axisRight.isEnabled = false
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -30f
                axisMinimum = -0.5f
                axisMaximum = readings.size.toFloat() - 0.5f

                valueFormatter = object : ValueFormatter() {
                    private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getAxisLabel(v: Float, a: AxisBase?): String {
                        val idx = v.toInt()
                        return if (idx in readings.indices) sdf.format(Date(readings[idx].timestamp)) else ""
                    }
                }
            }
            
            setTouchEnabled(true)
            isDragEnabled = true
            isScaleXEnabled = true
            isScaleYEnabled = false
            
            animateX(400)
            invalidate()
        }
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        val intent = Intent(this, HistoryActivity::class.java)
        when (item.itemId) {
            R.id.nav_daily -> intent.putExtra("HISTORY_TYPE", "Diario")
            R.id.nav_weekly -> intent.putExtra("HISTORY_TYPE", "Semanal")
            R.id.nav_monthly -> intent.putExtra("HISTORY_TYPE", "Mensual")
            R.id.nav_plants -> { showPlantsSelectionDialog(); return true }
            R.id.nav_comparison -> { startActivity(Intent(this, ComparisonActivity::class.java)); return true }
            R.id.nav_chat -> { startActivity(Intent(this, ChatActivity::class.java)); return true }
        }
        startActivity(intent)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showDataControlDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_data_control, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvControlTitle)
        tvTitle.text = "Gestión de '${plant.name}'"
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<Button>(R.id.btnImportFirebase).setOnClickListener { importDataFromFirebase(); dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnResetLocal).setOnClickListener { showResetDataDialog(); dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnCancelControl).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun importDataFromFirebase() {
        val plant = currentPlant ?: return
        val progressDialog = AlertDialog.Builder(this).setMessage("Importando registros...").setCancelable(false).show()
        databaseFirebase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ioScope.launch {
                    try {
                        if (snapshot.exists()) {
                            val readingsToInsert = mutableListOf<SensorReading>()
                            fun processNode(data: DataSnapshot) {
                                val temp = (data.child("temp").value as? Number)?.toDouble() ?: return
                                val hum = (data.child("hum").value as? Number)?.toDouble() ?: 0.0
                                val luzRaw = (data.child("luz_raw").value as? Number)?.toDouble() ?: (data.child("luz").value as? Number)?.toDouble() ?: 0.0
                                val luz = PredictiveTheorem.filtrarSensibilidadLuz(luzRaw)
                                val soil = (data.child("soil").value as? Number)?.toDouble() ?: 0.0
                                val irh = (data.child("irh").value as? Number)?.toDouble() ?: 0.0
                                val seq = (data.child("seq").value as? Number)?.toDouble() ?: 0.0
                                val somb = (data.child("somb").value as? Number)?.toDouble() ?: 0.0
                                val action = data.child("acc").value as? String ?: ""
                                val timestamp = (data.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()
                                readingsToInsert.add(SensorReading(timestamp, plant.id, temp, hum, luz, soil, irh, seq, somb, action))
                            }
                            if (snapshot.hasChild("history")) snapshot.child("history").children.forEach { processNode(it) } else processNode(snapshot)
                            if (readingsToInsert.isNotEmpty()) {
                                readingsToInsert.forEach { databaseLocal.sensorDao().insert(it) }
                                withContext(Dispatchers.Main) { progressDialog.dismiss(); Toast.makeText(this@MainActivity, "Importados ${readingsToInsert.size} ✅", Toast.LENGTH_LONG).show(); loadDataAndDrawChart() }
                            } else withContext(Dispatchers.Main) { progressDialog.dismiss(); Toast.makeText(this@MainActivity, "Sin datos válidos", Toast.LENGTH_SHORT).show() }
                        } else withContext(Dispatchers.Main) { progressDialog.dismiss(); Toast.makeText(this@MainActivity, "Sin datos en Firebase", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) { withContext(Dispatchers.Main) { progressDialog.dismiss(); Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } }
                }
            }
            override fun onCancelled(error: DatabaseError) { progressDialog.dismiss(); Toast.makeText(this@MainActivity, "Error de conexión", Toast.LENGTH_SHORT).show() }
        })
    }

    private fun showResetDataDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_reset_options, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvResetTitle)
        tvTitle.text = "Reiniciar '${plant.name}'"
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<Button>(R.id.btnReset24h).setOnClickListener { resetData(plant.id, System.currentTimeMillis() - (24 * 3600 * 1000L), System.currentTimeMillis(), false); dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnReset7d).setOnClickListener { resetData(plant.id, System.currentTimeMillis() - (7 * 24 * 3600 * 1000L), System.currentTimeMillis(), false); dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnResetMonth).setOnClickListener { resetData(plant.id, System.currentTimeMillis() - (30 * 24 * 3600 * 1000L), System.currentTimeMillis(), false); dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnResetAll).setOnClickListener { resetData(plant.id, 0, System.currentTimeMillis(), true); dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnCancelReset).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showEditNameDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val etInput = dialogView.findViewById<android.widget.EditText>(R.id.etInput)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnOk = dialogView.findViewById<Button>(R.id.btnOk)
        tvTitle.text = "Editar Nombre"
        etInput.setText(plant.name)
        val filter = android.text.InputFilter { source, start, end, dest, dstart, dend ->
            for (i in start until end) {
                val char = source[i]
                if (!Character.isLetterOrDigit(char) && char != ' ') return@InputFilter ""
            }
            null
        }
        etInput.filters = arrayOf(filter)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnOk.setOnClickListener {
            val newName = etInput.text.toString().trim()
            if (newName.isNotEmpty()) {
                ioScope.launch {
                    val updatedPlant = plant.copy(name = newName)
                    databaseLocal.plantDao().update(updatedPlant)
                    currentPlant = updatedPlant
                    withContext(Dispatchers.Main) { showPlantInfo(); Toast.makeText(this@MainActivity, "Nombre actualizado", Toast.LENGTH_SHORT).show(); dialog.dismiss() }
                }
            } else Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun resetData(plantId: Int, start: Long, end: Long, isAll: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            // 1. Limpiar UI primero para dar feedback inmediato
            combinedChart.clear()
            combinedChart.setNoDataText("Borrando...")
            combinedChart.invalidate()

            withContext(Dispatchers.IO) {
                try {
                    if (isAll) {
                        databaseLocal.sensorDao().deleteAllByPlantId(plantId)
                    } else {
                        databaseLocal.sensorDao().deleteReadingsBetween(plantId, start, end)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error al borrar: ${e.message}")
                }
            }

            // 2. Resetear variables de estado
            lastReading = null
            readingsSinceLastLearning = 0
            lastChartUpdate = 0
            
            Toast.makeText(this@MainActivity, "Datos eliminados correctamente ✅", Toast.LENGTH_SHORT).show()
            loadDataAndDrawChart()
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
        tvMessage.text = "¿Estás seguro de que quieres eliminar a '${plant.name}'?"
        btnAction.text = "Eliminar"
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnAction.setOnClickListener { deleteCurrentPlant(); dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun deleteCurrentPlant() {
        val plantToDelete = currentPlant ?: return
        ioScope.launch {
            val db = databaseLocal
            db.sensorDao().deleteAllByPlantId(plantToDelete.id)
            db.plantDao().delete(plantToDelete)
            val remainingPlants = db.plantDao().getAll()
            if (remainingPlants.isNotEmpty()) db.plantDao().update(remainingPlants[0].copy(isSelected = true))
            withContext(Dispatchers.Main) { val intent = Intent(this@MainActivity, MainActivity::class.java); intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK; startActivity(intent); finish() }
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
                listView.adapter = object : android.widget.ArrayAdapter<Plant>(this@MainActivity, R.layout.item_plant_selection, R.id.tvItemPlantName, plants) {
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
                listView.setOnItemClickListener { _, _, position, _ -> selectPlant(plants[position]); dialog.dismiss() }
                dialog.show()
            }
        }
    }

    private fun selectPlant(plant: Plant) {
        ioScope.launch {
            databaseLocal.plantDao().deselectAll()
            databaseLocal.plantDao().update(plant.copy(isSelected = true))
            val tipoInt = when { plant.environment.contains("Luz", ignoreCase = true) -> 1; plant.environment.contains("Sombra", ignoreCase = true) -> 3; else -> 2 }
            FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/").getReference("config/tipoPlanta").setValue(tipoInt)
            withContext(Dispatchers.Main) { val intent = Intent(this@MainActivity, MainActivity::class.java); intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK; startActivity(intent); finish() }
        }
    }
}

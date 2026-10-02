package com.example.controlherbal.ui.activities

import android.content.Intent
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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.controlherbal.R
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import com.example.controlherbal.data.sync.SensorForegroundService
import com.github.mikephil.charting.charts.CombinedChart
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.*
import com.example.controlherbal.ui.viewmodel.SensorViewModel
import com.example.controlherbal.ui.widget.HerbalWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "Control Herbal"
        private const val LEARNING_THRESHOLD = 20
    }

    internal lateinit var drawerLayout: DrawerLayout
    internal lateinit var tvConnectionState: TextView
    internal lateinit var tvTemp: TextView
    internal lateinit var tvHum: TextView
    internal lateinit var tvSoil: TextView
    internal lateinit var tvLuz: TextView
    internal lateinit var tvIRH: TextView
    internal lateinit var tvSeq: TextView
    internal lateinit var tvSomb: TextView
    internal lateinit var tvAccion: TextView
    internal lateinit var tvCausa: TextView
    internal lateinit var tvAlerta: TextView
    internal lateinit var tvLastUpdate: TextView
    internal lateinit var tvDayNightStatus: TextView
    internal lateinit var tvWateringRecommended: TextView
    internal lateinit var tvNextWateringTime: TextView
    internal lateinit var combinedChart: CombinedChart
    internal lateinit var cbTemp: CheckBox
    internal lateinit var cbHum: CheckBox
    internal lateinit var cbSoil: CheckBox
    internal lateinit var cbLuz: CheckBox
    internal lateinit var cbIRH: CheckBox
    internal lateinit var btnConnect: Button
    internal lateinit var btnWatering: Button
    internal var tvPlantNameAndEmoji: TextView? = null
    internal var layoutPlantInfo: View? = null
    internal lateinit var btnAddPlant: ImageButton
    internal lateinit var btnEditNameIcon: ImageButton
    internal lateinit var btnDataControl: Button
    internal lateinit var btnDeletePlant: Button
    internal lateinit var cardAiDiagnosis: CardView
    internal lateinit var tvAiDiagnosisTitle: TextView
    internal lateinit var tvAiDiagnosisBody: TextView
    internal lateinit var btnClearAiDiagnosis: Button

    internal lateinit var databaseFirebase: DatabaseReference
    internal val CHANNEL_ID = "herbal_alerts_channel"
    internal val NOTIFICATION_ID = 1001
    internal val SYNC_NOTIFICATION_ID = 1002
    internal var lastAlertState = 0 
    internal var isDisconnected = false
    internal var lastBootCount: Int = -1
    internal val handler = Handler(Looper.getMainLooper())
    internal val syncTimeoutRunnable = Runnable { handleDisconnection() }

    internal lateinit var viewModel: SensorViewModel
    internal lateinit var databaseLocal: SensorDatabase
    internal lateinit var herbalAI: HerbalAI
    internal var tflite: Interpreter? = null
    internal val ioScope = CoroutineScope(Dispatchers.IO)
    internal var lastChartUpdate: Long = 0
    internal var lastReading: SensorReading? = null
    internal var readingsSinceLastLearning = 0
    internal var currentPlant: Plant? = null
    internal var firebaseListener: ValueEventListener? = null
    
    internal var isLinkingInProgress = false
    internal var isFirstPacketAfterLinking = false

    internal var lastWateringAlertState = 0 
    internal var lastRecommendationText = "" 

    internal var lastProcessTime: Long = 0

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
                    viewModel = ViewModelProvider(this@MainActivity).get(SensorViewModel::class.java)
                    viewModel.observeLatestReading(plant.id)

                    lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.uiState.collect { state ->
                                if (state.isLinking) {
                                    showLinkingState()
                                } else if (state.isConnected && state.analysisResult != null) {
                                    val res = state.analysisResult
                                    updateUIAndSave(
                                        state.temp, state.hum, state.luz, state.soil,
                                        res.irh, res.seq, res.somb,
                                        res.recommendation, res.wateringRecommended, res.nextWateringHours,
                                        res.confidence
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
        tvDayNightStatus = findViewById(R.id.tvDayNightStatus)
        tvWateringRecommended = findViewById(R.id.tvWateringRecommended)
        tvNextWateringTime = findViewById(R.id.tvNextWateringTime)
        combinedChart = findViewById(R.id.combinedChart)
        
        cbTemp = findViewById(R.id.cbTemp)
        cbHum = findViewById(R.id.cbHum)
        cbSoil = findViewById(R.id.cbSoil)
        cbLuz = findViewById(R.id.cbLuz)
        cbIRH = findViewById(R.id.cbIRH)

        val chartListener = CompoundButton.OnCheckedChangeListener { _, _ -> loadDataAndDrawChart() }
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
        val intent = Intent(this, SensorForegroundService::class.java)
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
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
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
                val updatedPlant = plant.copy(lastWateringTime = now, pendingSync = true)
                databaseLocal.plantDao().update(updatedPlant)
                currentPlant = updatedPlant
                
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
            tvDayNightStatus.text = ""
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
        tvDayNightStatus.text = ""
        btnConnect.text = "VINCULAR DISPOSITIVO"
        btnConnect.isEnabled = true
        tvAlerta.text = "Sin conexión"
        tvAlerta.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
        tvAlerta.setTextColor(Color.BLACK)
    }

    private fun startFirebaseListener() {
        viewModel.setLinking(true)
        showLinkingState()
        startSensorService()
        Toast.makeText(this, "Buscando señal del dispositivo...", Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        firebaseListener?.let { if (::databaseFirebase.isInitialized) databaseFirebase.removeEventListener(it) }
        firebaseListener = null
    }

    internal fun handleDisconnection() {
        if (!isDisconnected) {
            isDisconnected = true
            isLinkingInProgress = false
            viewModel.setLinking(false)
            showDisconnectedState()
            sendSyncNotification("⚠️ ESP-32 Desconectado", "Se ha perdido la sincronización con el dispositivo.")
        }
    }

    private fun updateUIAndSave(temp: Double, hum: Double, luz: Double, soil: Double, irh: Double, seq: Double, somb: Double, accion: String, wateringRecommended: Boolean, nextWateringHours: Double, confidence: Double = 1.0) {
        if (temp == 0.0 && hum == 0.0 && luz == 0.0 && soil == 0.0) return

        val locale = Locale.getDefault()
        tvConnectionState.text = "Sincronizado"
        tvConnectionState.setTextColor(Color.parseColor("#2E7D32"))
        
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isDay = luz > 10.0 || currentHour in 7..19
        tvDayNightStatus.text = if (isDay) "(Día ☀️)" else "(Noche 🌙)"

        btnConnect.text = "DISPOSITIVO VINCULADO ✅"
        btnConnect.isEnabled = true

        tvTemp.text = String.format(locale, "%.1f °C", temp)
        tvHum.text = String.format(locale, "%.1f %%", hum)
        tvSoil.text = String.format(locale, "%.1f %%", soil)
        tvLuz.text = String.format(locale, "%.1f %%", luz)
        tvIRH.text = String.format(locale, "%.1f", irh)
        
        fun formatHours(h: Double): String {
            if (h >= PredictiveTheorem.PREDICCION_MAX_HORAS - 1.0) return "Sin riesgo"
            if (h < 1.0) return String.format(locale, "%.1f h", h)
            val totalHours = h.toInt()
            val days = totalHours / 24
            val rem = totalHours % 24
            return if (days > 0) "${days}d ${rem}h" else "${rem}h"
        }

        tvSeq.text = formatHours(seq)
        tvSomb.text = formatHours(somb)
        
        if (confidence < 0.5) {
            tvNextWateringTime.text = "Calculando predicción segura... ⏳"
            tvNextWateringTime.setTextColor(Color.GRAY)
        } else {
            tvNextWateringTime.setTextColor(Color.BLACK)
            if (nextWateringHours <= 0) {
                tvNextWateringTime.text = "¡Necesita riego ahora! 💧"
            } else {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MINUTE, (nextWateringHours * 60).toInt())
                val diffHours = nextWateringHours
                val diffDays = (diffHours / 24).toInt()
                val timeString = when {
                    diffDays >= 1 -> {
                        val remainingHours = (diffHours % 24).toInt()
                        val dayName = SimpleDateFormat("EEEE", locale).format(calendar.time).replaceFirstChar { it.uppercase() }
                        val timeOfDay = SimpleDateFormat("HH:mm", locale).format(calendar.time)
                        if (diffDays >= 7) "aprox. en ${diffDays / 7} semana(s) ($dayName $timeOfDay)"
                        else if (remainingHours > 0) "aprox. $diffDays días y $remainingHours h ($dayName $timeOfDay)"
                        else "aprox. $diffDays días ($dayName $timeOfDay)"
                    }
                    else -> {
                        val h = diffHours.toInt()
                        val m = ((diffHours - h) * 60).toInt()
                        if (h > 0) "aprox. $h h y $m min (${SimpleDateFormat("HH:mm", locale).format(calendar.time)})"
                        else "aprox. $m min (${SimpleDateFormat("HH:mm", locale).format(calendar.time)})"
                    }
                }
                tvNextWateringTime.text = "Próximo riego estimado: $timeString"
                if (confidence < 0.9) tvNextWateringTime.append(" (Aprendiendo... 🧠)")
            }
        }
        
        tvWateringRecommended.text = "Riego recomendado: ${if (wateringRecommended) "SÍ" else "No"}"
        tvWateringRecommended.setTextColor(if (wateringRecommended) Color.RED else Color.parseColor("#1E88E5"))
        
        tvAccion.text = accion
        if (accion != lastRecommendationText) {
            handleSignificantNotifications(wateringRecommended, accion)
            lastRecommendationText = accion
        }

        tvAccion.setTextColor(ContextCompat.getColor(this, R.color.green_herbal))
        tvCausa.text = ""
        tvLastUpdate.text = String.format("Última actualización: %s", SimpleDateFormat("HH:mm:ss", locale).format(Date()))

        val hasExplicitWarning = accion.contains("⚠️") || accion.contains("EXTREMO") || 
                                accion.contains("URGENTE") || accion.contains("INMEDIATO")
        val isStressful = irh >= PredictiveTheorem.IRH_ADVERTENCIA || hasExplicitWarning

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
            isStressful -> {
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
                HerbalWidgetManager.updateWidgets(this@MainActivity)

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

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
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

    private fun showPlantsSelectionDialog() {
        ioScope.launch {
            val plants = databaseLocal.plantDao().getAll()
            withContext(Dispatchers.Main) {
                val builder = AlertDialog.Builder(this@MainActivity)
                val dialogView = layoutInflater.inflate(R.layout.dialog_rounded_list, null)
                builder.setView(dialogView)
                val dialog = builder.create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                val listView = dialogView.findViewById<ListView>(R.id.dialogListView)
                listView.adapter = object : ArrayAdapter<Plant>(this@MainActivity, R.layout.item_plant_selection, R.id.tvItemPlantName, plants) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
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

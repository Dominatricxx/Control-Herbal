package com.example.controlherbal.ui.activities.plant

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.style.*

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.accessibility.ChartColors
import com.example.controlherbal.common.accessibility.StatusLevel
import com.example.controlherbal.common.accessibility.StatusPalette
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.navigation.NavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvAvgTemp: TextView
    private lateinit var tvAvgHum: TextView
    private lateinit var tvAvgSoil: TextView
    private lateinit var tvAvgLuz: TextView
    private lateinit var tvAvgIRH: TextView
    private lateinit var combinedChart: CombinedChart
    private lateinit var cbTemp: CheckBox
    private lateinit var cbHum: CheckBox
    private lateinit var cbSoil: CheckBox
    private lateinit var cbLuz: CheckBox
    private lateinit var cbIRH: CheckBox
    private lateinit var btnDataControl: Button
    private lateinit var databaseLocal: SensorDatabase
    private lateinit var databaseFirebase: DatabaseReference
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var currentPlant: Plant? = null

    companion object {
        private const val TAG = "HistoryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_drawer)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        
        val type = SecurityUtils.getSafeIntentString(intent, AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_DIARIO)
        
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val menu = navView.menu
        when(type) {
            AppConstants.HISTORY_DIARIO -> menu.findItem(R.id.nav_daily).isVisible = false
            AppConstants.HISTORY_SEMANAL -> menu.findItem(R.id.nav_weekly).isVisible = false
            AppConstants.HISTORY_MENSUAL -> menu.findItem(R.id.nav_monthly).isVisible = false
        }

        tvTitle = findViewById(R.id.tvHistoryTitle)
        val tvHeaderTitle: TextView = findViewById(R.id.tvHeaderTitle)
        
        tvAvgTemp = findViewById(R.id.tvAvgTemp)
        tvAvgHum = findViewById(R.id.tvAvgHum)
        tvAvgSoil = findViewById(R.id.tvAvgSoil)
        tvAvgLuz = findViewById(R.id.tvAvgLuz)
        tvAvgIRH = findViewById(R.id.tvAvgIRH)
        
        combinedChart = findViewById(R.id.combinedChart)
        cbTemp = findViewById(R.id.cbTemp)
        cbHum = findViewById(R.id.cbHum)
        cbSoil = findViewById(R.id.cbSoil)
        cbLuz = findViewById(R.id.cbLuz)
        cbIRH = findViewById(R.id.cbIRH)

        val chartListener = CompoundButton.OnCheckedChangeListener { _, _ -> 
            val currentType = SecurityUtils.getSafeIntentString(intent, AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_DIARIO)
            loadHistoryData(currentType)
        }
        cbTemp.setOnCheckedChangeListener(chartListener)
        cbHum.setOnCheckedChangeListener(chartListener)
        cbSoil.setOnCheckedChangeListener(chartListener)
        cbLuz.setOnCheckedChangeListener(chartListener)
        cbIRH.setOnCheckedChangeListener(chartListener)

        btnDataControl = findViewById(R.id.btnDataControl)
        btnDataControl.setOnClickListener { showDataControlDialog() }

        databaseLocal = SensorDatabase.getInstance(this)
        
        try {
            val dbInstance = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            databaseFirebase = dbInstance.getReference(AppConstants.FIREBASE_SENSOR_NODE)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error Firebase: ${e.message}")
        }

        tvHeaderTitle.text = "Registro $type"
        tvTitle.text = "Análisis Detallado"

        ioScope.launch {
            val plant = databaseLocal.plantDao().getSelectedPlant()
            currentPlant = plant
            val plantCount = databaseLocal.plantDao().getPlantCount()
            
            withContext(Dispatchers.Main) {
                navView.menu.findItem(R.id.nav_plants).isVisible = plantCount >= 2
                navView.menu.findItem(R.id.nav_comparison).isVisible = plantCount >= 2
                loadHistoryData(type)
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return com.example.controlherbal.ui.helper.NavigationDrawerHandler.handleNavigation(this, drawerLayout, item)
    }

    private fun loadHistoryData(type: String) {
        val plantId = currentPlant?.id ?: return
        ioScope.launch {
            val endTime = System.currentTimeMillis()
            val startTime = when (type) {
                AppConstants.HISTORY_DIARIO -> endTime - (24 * 3600 * 1000L)
                AppConstants.HISTORY_SEMANAL -> endTime - (7 * 24 * 3600 * 1000L)
                AppConstants.HISTORY_MENSUAL -> endTime - (30 * 24 * 3600 * 1000L)
                else -> endTime - (24 * 3600 * 1000L)
            }

            val filteredReadings = databaseLocal.sensorDao().getReadingsBetween(plantId, startTime, endTime)

            if (filteredReadings.isNotEmpty()) {
                val avgTemp = filteredReadings.map { it.temperature }.average()
                val avgHum = filteredReadings.map { it.humidity }.average()
                val avgSoil = filteredReadings.map { it.soilMoisture }.average()
                val avgLuz = filteredReadings.map { it.light }.average()
                val avgIRH = filteredReadings.map { it.irh }.average()

                val herbalAI = HerbalAI(this@HistoryActivity)
                val aiIrh = herbalAI.predictRefinedIRH(avgTemp, avgHum, avgLuz, avgSoil, currentPlant?.type ?: "Híbrido")
                val combinedIrh = (avgIRH + aiIrh) / 2.0

                withContext(Dispatchers.Main) {
                    tvAvgTemp.text = String.format(Locale.getDefault(), "%.1f °C", avgTemp)
                    tvAvgHum.text = String.format(Locale.getDefault(), "%.1f %%", avgHum)
                    tvAvgSoil.text = String.format(Locale.getDefault(), "%.1f %%", avgSoil)
                    tvAvgLuz.text = String.format(Locale.getDefault(), "%.1f %%", avgLuz)
                    tvAvgIRH.text = String.format(Locale.getDefault(), "%.1f (IA)", combinedIrh)
                    
                    // Estado con color Y símbolo (el color nunca es la única señal)
                    val irhLevel = when {
                        combinedIrh > PredictiveTheorem.IRH_RIESGO -> StatusLevel.CRITICAL
                        combinedIrh > PredictiveTheorem.IRH_ADVERTENCIA -> StatusLevel.WARN
                        else -> StatusLevel.OK
                    }
                    tvAvgIRH.text = "${irhLevel.symbol} " + String.format(Locale.getDefault(), "%.1f (IA)", combinedIrh)
                    tvAvgIRH.setTextColor(StatusPalette.current(this@HistoryActivity).forLevel(irhLevel))

                    drawHistoryChart(filteredReadings)
                }
            } else {
                withContext(Dispatchers.Main) {
                    combinedChart.clear()
                    combinedChart.setNoDataText("No hay registros para este periodo")
                    combinedChart.invalidate()
                    tvAvgTemp.text = "--"
                    tvAvgHum.text = "--"
                    tvAvgSoil.text = "--"
                    tvAvgLuz.text = "--"
                    tvAvgIRH.text = "--"
                }
            }
        }
    }

    private fun drawHistoryChart(readings: List<SensorReading>) {
        val combinedData = CombinedData()
        val lineData = LineData()

        if (cbTemp.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
            lineData.addDataSet(ChartStyle.apply(LineDataSet(entries, "Temperatura"), ChartColors.TEMP, 0))
        }
        if (cbHum.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
            lineData.addDataSet(ChartStyle.apply(LineDataSet(entries, "Humedad"), ChartColors.HUM, 1))
        }
        if (cbSoil.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }
            lineData.addDataSet(ChartStyle.apply(LineDataSet(entries, "Suelo"), ChartColors.SOIL, 2))
        }
        if (cbLuz.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
            lineData.addDataSet(ChartStyle.apply(LineDataSet(entries, "Luz"), ChartColors.LIGHT, 3))
        }

        combinedData.setData(lineData)

        if (cbIRH.isChecked) {
            val barEntries = readings.mapIndexed { i, r -> BarEntry(i.toFloat(), r.irh.toFloat()) }
            val barDataSet = BarDataSet(barEntries, "IRH").apply {
                color = Color.argb(150, 211, 47, 47)
                setDrawValues(false)
            }
            val barData = BarData(barDataSet)
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
                setLabelCount(8, false)
                granularity = 1f
                setDrawGridLines(false)
                labelRotationAngle = -45f
                axisMinimum = -0.5f
                axisMaximum = readings.size.toFloat() - 0.5f

                valueFormatter = object : ValueFormatter() {
                    private val sdfDaily = SimpleDateFormat("HH:mm", Locale.getDefault())
                    private val sdfFull = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    
                    override fun getAxisLabel(v: Float, a: AxisBase?): String {
                        val idx = v.toInt()
                        if (idx !in readings.indices) return ""
                        
                        val type = SecurityUtils.getSafeIntentString(intent, AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_DIARIO)
                        val sdf = if (type == AppConstants.HISTORY_DIARIO) sdfDaily else sdfFull
                        return sdf.format(Date(readings[idx].timestamp))
                    }
                }
            }
            
            setTouchEnabled(true)
            isDragEnabled = true
            isScaleXEnabled = true
            isScaleYEnabled = false
            setPinchZoom(true)
            
            animateX(500)
            invalidate()
        }
    }

    private fun showDataControlDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_data_control, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvControlTitle)
        tvTitle.text = "Gestión de '${SecurityUtils.sanitizeText(plant.name)}'"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnImportFirebase).setOnClickListener {
            importDataFromFirebase()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnResetLocal).setOnClickListener {
            showResetDataDialog()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnCancelControl).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun importDataFromFirebase() {
        val plant = currentPlant ?: return
        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Importando registros desde la nube...")
            .setCancelable(false)
            .show()

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
                                val action = SecurityUtils.sanitizeText(data.child("acc").value as? String ?: "")
                                val timestamp = (data.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()

                                readingsToInsert.add(
                                    SensorReading(
                                        timestamp = timestamp,
                                        plantId = plant.id,
                                        temperature = temp,
                                        humidity = hum,
                                        light = luz,
                                        soilMoisture = soil,
                                        irh = irh,
                                        seq = seq,
                                        somb = somb,
                                        action = action
                                    )
                                )
                            }

                            if (snapshot.hasChild("history")) {
                                snapshot.child("history").children.forEach { processNode(it) }
                            } else {
                                processNode(snapshot)
                            }

                            if (readingsToInsert.isNotEmpty()) {
                                readingsToInsert.forEach { databaseLocal.sensorDao().insert(it) }
                                withContext(Dispatchers.Main) {
                                    progressDialog.dismiss()
                                    Toast.makeText(this@HistoryActivity, "Se han importado ${readingsToInsert.size} registros ✅", Toast.LENGTH_LONG).show()
                                    val type = SecurityUtils.getSafeIntentString(intent, AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_DIARIO)
                                    loadHistoryData(type)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    progressDialog.dismiss()
                                    Toast.makeText(this@HistoryActivity, "No se encontraron registros válidos en la nube", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                progressDialog.dismiss()
                                Toast.makeText(this@HistoryActivity, "No hay datos en Firebase para esta planta", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        SecureLogger.e(TAG, "Error en importación: ${e.message}")
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            Toast.makeText(this@HistoryActivity, "Error al importar datos", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                progressDialog.dismiss()
                Toast.makeText(this@HistoryActivity, "Error de conexión con Firebase", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showResetDataDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_reset_options, null)
        val tvTitleDialog = dialogView.findViewById<TextView>(R.id.tvResetTitle)
        tvTitleDialog.text = "Reiniciar '${SecurityUtils.sanitizeText(plant.name)}'"

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
        lifecycleScope.launch(Dispatchers.Main) {
            combinedChart.clear()
            combinedChart.setNoDataText("Borrando...")
            combinedChart.invalidate()

            withContext(Dispatchers.IO) {
                if (isAll) {
                    databaseLocal.sensorDao().deleteAllByPlantId(plantId)
                } else {
                    databaseLocal.sensorDao().deleteReadingsBetween(plantId, start, end)
                }
            }

            Toast.makeText(this@HistoryActivity, "Datos eliminados correctamente ✅", Toast.LENGTH_SHORT).show()
            val type = SecurityUtils.getSafeIntentString(intent, AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_DIARIO)
            loadHistoryData(type)
        }
    }
}

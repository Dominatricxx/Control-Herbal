package com.example.controlherbal

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.data.*
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvAvgTemp: TextView
    private lateinit var tvAvgHum: TextView
    private lateinit var tvAvgSoil: TextView
    private lateinit var tvAvgLuz: TextView
    private lateinit var tvAvgIRH: TextView
    private lateinit var combinedChart: CombinedChart
    private lateinit var cbTemp: android.widget.CheckBox
    private lateinit var cbHum: android.widget.CheckBox
    private lateinit var cbSoil: android.widget.CheckBox
    private lateinit var cbLuz: android.widget.CheckBox
    private lateinit var cbIRH: android.widget.CheckBox
    private lateinit var btnResetData: android.widget.Button
    private lateinit var btnDeletePlant: android.widget.Button
    private lateinit var databaseLocal: SensorDatabase
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var currentPlant: Plant? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_drawer)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        
        val type = intent.getStringExtra("HISTORY_TYPE") ?: "DIARIO"
        
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val menu = navView.menu
        when(type) {
            "DIARIO" -> menu.findItem(R.id.nav_daily).isVisible = false
            "SEMANAL" -> menu.findItem(R.id.nav_weekly).isVisible = false
            "MENSUAL" -> menu.findItem(R.id.nav_monthly).isVisible = false
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

        val chartListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ -> 
            val type = intent.getStringExtra("HISTORY_TYPE") ?: "DIARIO"
            loadHistoryData(type)
        }
        cbTemp.setOnCheckedChangeListener(chartListener)
        cbHum.setOnCheckedChangeListener(chartListener)
        cbSoil.setOnCheckedChangeListener(chartListener)
        cbLuz.setOnCheckedChangeListener(chartListener)
        cbIRH.setOnCheckedChangeListener(chartListener)

        btnResetData = findViewById(R.id.btnResetData)
        btnDeletePlant = findViewById(R.id.btnDeletePlant)
        
        btnResetData.setOnClickListener { showResetDataDialog() }
        btnDeletePlant.setOnClickListener { showDeletePlantDialog() }

        databaseLocal = SensorDatabase.getInstance(this)

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

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_main -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                startActivity(intent)
            }
            R.id.nav_daily -> {
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("HISTORY_TYPE", "DIARIO")
                startActivity(intent)
            }
            R.id.nav_weekly -> {
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("HISTORY_TYPE", "SEMANAL")
                startActivity(intent)
            }
            R.id.nav_monthly -> {
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("HISTORY_TYPE", "MENSUAL")
                startActivity(intent)
            }
            R.id.nav_plants -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                startActivity(intent)
            }
            R.id.nav_comparison -> {
                startActivity(Intent(this, ComparisonActivity::class.java))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun loadHistoryData(type: String) {
        val plantId = currentPlant?.id ?: return
        ioScope.launch {
            val endTime = System.currentTimeMillis()
            val startTime = when (type) {
                "DIARIO" -> endTime - (24 * 3600 * 1000L)
                "SEMANAL" -> endTime - (7 * 24 * 3600 * 1000L)
                "MENSUAL" -> endTime - (30 * 24 * 3600 * 1000L)
                else -> endTime - (24 * 3600 * 1000L)
            }

            val filteredReadings = databaseLocal.sensorDao().getReadingsBetween(plantId, startTime, endTime)

            if (filteredReadings.isNotEmpty()) {
                val avgTemp = filteredReadings.map { it.temperature }.average()
                val avgHum = filteredReadings.map { it.humidity }.average()
                val avgLuz = filteredReadings.map { it.light }.average()
                val avgIRH = filteredReadings.map { it.irh }.average()

                withContext(Dispatchers.Main) {
                    tvAvgTemp.text = String.format("%.1f °C", avgTemp)
                    tvAvgHum.text = String.format("%.1f %%", avgHum)
                    tvAvgLuz.text = String.format("%.1f %%", avgLuz)
                    tvAvgIRH.text = String.format("%.1f", avgIRH)
                    drawHistoryChart(filteredReadings)
                }
            }
        }
    }

    private fun drawHistoryChart(readings: List<SensorReading>) {
        val combinedData = com.github.mikephil.charting.data.CombinedData()
        val lineData = com.github.mikephil.charting.data.LineData()

        if (cbTemp.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
            lineData.addDataSet(LineDataSet(entries, "Temperatura").apply { color = Color.RED; setDrawCircles(false) })
        }
        if (cbHum.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
            lineData.addDataSet(LineDataSet(entries, "Humedad").apply { color = Color.BLUE; setDrawCircles(false) })
        }
        if (cbSoil.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }
            lineData.addDataSet(LineDataSet(entries, "Suelo").apply { color = Color.parseColor("#2E7D32"); setDrawCircles(false) })
        }
        if (cbLuz.isChecked) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
            lineData.addDataSet(LineDataSet(entries, "Luz").apply { color = Color.rgb(255, 215, 0); setDrawCircles(false) })
        }

        combinedData.setData(lineData)

        if (cbIRH.isChecked) {
            val barEntries = readings.mapIndexed { i, r -> com.github.mikephil.charting.data.BarEntry(i.toFloat(), r.irh.toFloat()) }
            val barDataSet = com.github.mikephil.charting.data.BarDataSet(barEntries, "IRH").apply {
                color = Color.argb(150, 211, 47, 47)
                setDrawValues(false)
            }
            combinedData.setData(com.github.mikephil.charting.data.BarData(barDataSet))
        }

        combinedChart.apply {
            data = combinedData
            description.isEnabled = false
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                private val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                override fun getAxisLabel(v: Float, a: com.github.mikephil.charting.components.AxisBase?): String {
                    val idx = v.toInt()
                    return if (idx in readings.indices) sdf.format(java.util.Date(readings[idx].timestamp)) else ""
                }
            }
            axisRight.isEnabled = false
            invalidate()
        }
    }

    private fun showResetDataDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_reset_options, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvResetTitle)
        tvTitle.text = "Reiniciar de '${plant.name}'"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.widget.Button>(R.id.btnReset24h).setOnClickListener {
            resetData(plant.id, System.currentTimeMillis() - (24 * 3600 * 1000L), System.currentTimeMillis(), false)
            dialog.dismiss()
        }
        dialogView.findViewById<android.widget.Button>(R.id.btnReset7d).setOnClickListener {
            resetData(plant.id, System.currentTimeMillis() - (7 * 24 * 3600 * 1000L), System.currentTimeMillis(), false)
            dialog.dismiss()
        }
        dialogView.findViewById<android.widget.Button>(R.id.btnResetMonth).setOnClickListener {
            resetData(plant.id, System.currentTimeMillis() - (30 * 24 * 3600 * 1000L), System.currentTimeMillis(), false)
            dialog.dismiss()
        }
        dialogView.findViewById<android.widget.Button>(R.id.btnResetAll).setOnClickListener {
            resetData(plant.id, 0, System.currentTimeMillis(), true)
            dialog.dismiss()
        }
        dialogView.findViewById<android.widget.Button>(R.id.btnCancelReset).setOnClickListener {
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
                android.widget.Toast.makeText(this@HistoryActivity, "Datos eliminados correctamente", android.widget.Toast.LENGTH_SHORT).show()
                val type = intent.getStringExtra("HISTORY_TYPE") ?: "DIARIO"
                loadHistoryData(type)
            }
        }
    }

    private fun showDeletePlantDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnAction = dialogView.findViewById<android.widget.Button>(R.id.btnAction)

        tvTitle.text = "Eliminar planta"
        tvMessage.text = "¿Estás seguro de que quieres eliminar a '${plant.name}'? Se perderán todos sus datos."
        btnAction.text = "Eliminar"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
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
                val intent = Intent(this@HistoryActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}

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
    private lateinit var btnDataControl: android.widget.Button
    private lateinit var databaseLocal: SensorDatabase
    private lateinit var databaseFirebase: com.google.firebase.database.DatabaseReference
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

        btnDataControl = findViewById(R.id.btnDataControl)
        
        btnDataControl.setOnClickListener { showDataControlDialog() }

        databaseLocal = SensorDatabase.getInstance(this)
        
        try {
            val dbInstance = com.google.firebase.database.FirebaseDatabase.getInstance("https://controlherbal-97558-default-rtdb.firebaseio.com/")
            databaseFirebase = dbInstance.getReference("sensor")
        } catch (e: Exception) {
            android.util.Log.e("HistoryActivity", "Error Firebase: ${e.message}")
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
                val avgSoil = filteredReadings.map { it.soilMoisture }.average()
                val avgLuz = filteredReadings.map { it.light }.average()
                val avgIRH = filteredReadings.map { it.irh }.average()

                withContext(Dispatchers.Main) {
                    tvAvgTemp.text = String.format("%.1f °C", avgTemp)
                    tvAvgHum.text = String.format("%.1f %%", avgHum)
                    tvAvgSoil.text = String.format("%.1f %%", avgSoil)
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

    private fun showDataControlDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_data_control, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvControlTitle)
        tvTitle.text = "Gestión de '${plant.name}'"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.widget.Button>(R.id.btnImportFirebase).setOnClickListener {
            importDataFromFirebase()
            dialog.dismiss()
        }

        dialogView.findViewById<android.widget.Button>(R.id.btnResetLocal).setOnClickListener {
            showResetDataDialog()
            dialog.dismiss()
        }

        dialogView.findViewById<android.widget.Button>(R.id.btnCancelControl).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun importDataFromFirebase() {
        val plant = currentPlant ?: return
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Importando registros desde la nube...")
            .setCancelable(false)
            .show()

        databaseFirebase.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                ioScope.launch {
                    try {
                        if (snapshot.exists()) {
                            val readingsToInsert = mutableListOf<SensorReading>()
                            
                            fun processNode(data: com.google.firebase.database.DataSnapshot) {
                                val temp = (data.child("temp").value as? Number)?.toDouble() ?: return
                                val hum = (data.child("hum").value as? Number)?.toDouble() ?: 0.0
                                val luz = (data.child("luz").value as? Number)?.toInt() ?: 0
                                val soil = (data.child("soil").value as? Number)?.toDouble() ?: 0.0
                                val irh = (data.child("irh").value as? Number)?.toDouble() ?: 0.0
                                val seq = (data.child("seq").value as? Number)?.toDouble() ?: 0.0
                                val somb = (data.child("somb").value as? Number)?.toDouble() ?: 0.0
                                val action = data.child("acc").value as? String ?: ""
                                val timestamp = (data.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()

                                readingsToInsert.add(SensorReading(timestamp, plant.id, temp, hum, luz, soil, irh, seq, somb, action))
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
                                    android.widget.Toast.makeText(this@HistoryActivity, "Se han importado ${readingsToInsert.size} registros ✅", android.widget.Toast.LENGTH_LONG).show()
                                    val type = intent.getStringExtra("HISTORY_TYPE") ?: "DIARIO"
                                    loadHistoryData(type)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    progressDialog.dismiss()
                                    android.widget.Toast.makeText(this@HistoryActivity, "No se encontraron registros válidos en la nube", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                progressDialog.dismiss()
                                android.widget.Toast.makeText(this@HistoryActivity, "No hay datos en Firebase para esta planta", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            android.widget.Toast.makeText(this@HistoryActivity, "Error al importar: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                progressDialog.dismiss()
                android.widget.Toast.makeText(this@HistoryActivity, "Error de conexión con Firebase", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
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

    private fun showEditNameDialog() {
        val plant = currentPlant ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val etInput = dialogView.findViewById<android.widget.EditText>(R.id.etInput)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnOk)

        tvTitle.text = "Editar Nombre"
        etInput.setText(plant.name)
        etInput.hint = "Nuevo nombre (solo letras y números)"

        val filter = android.text.InputFilter { source, start, end, dest, dstart, dend ->
            for (i in start until end) {
                val char = source[i]
                if (!Character.isLetterOrDigit(char) && char != ' ') {
                    return@InputFilter ""
                }
            }
            null
        }
        etInput.filters = arrayOf(filter)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnOk.setOnClickListener {
            val newName = etInput.text.toString().trim()
            if (newName.isNotEmpty()) {
                ioScope.launch {
                    val updatedPlant = plant.copy(name = newName)
                    databaseLocal.plantDao().update(updatedPlant)
                    currentPlant = updatedPlant
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@HistoryActivity, "Nombre actualizado correctamente", android.widget.Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            } else {
                android.widget.Toast.makeText(this, "El nombre no puede estar vacío", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
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
}

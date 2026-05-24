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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
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
    private lateinit var tvAvgLuz: TextView
    private lateinit var tvAvgIRH: TextView
    private lateinit var lineChart: LineChart
    private lateinit var databaseLocal: SensorDatabase
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var currentPlant: Plant? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_drawer)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        
        // Colorear el menú de eliminar planta
        colorDeleteMenuItem(navView)

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
        tvAvgLuz = findViewById(R.id.tvAvgLuz)
        tvAvgIRH = findViewById(R.id.tvAvgIRH)
        
        lineChart = findViewById(R.id.historyChart)
        databaseLocal = SensorDatabase.getInstance(this)

        tvHeaderTitle.text = "Registro $type"
        tvTitle.text = "Análisis Detallado"

        ioScope.launch {
            val plant = databaseLocal.plantDao().getSelectedPlant()
            currentPlant = plant
            val plantCount = databaseLocal.plantDao().getPlantCount()
            
            withContext(Dispatchers.Main) {
                navView.menu.findItem(R.id.nav_plants).isVisible = plantCount >= 2
                loadHistoryData(type)
            }
        }
    }

    private fun colorDeleteMenuItem(navView: NavigationView) {
        val menu = navView.menu
        val deleteItem = menu.findItem(R.id.nav_delete_plant)
        val s = SpannableString(deleteItem.title)
        s.setSpan(ForegroundColorSpan(Color.RED), 0, s.length, 0)
        deleteItem.title = s
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
            R.id.nav_delete_plant -> {
                // Volver a MainActivity para gestionar la eliminación
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
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
        val tempEntries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
        val humEntries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }

        val tempSet = LineDataSet(tempEntries, "Temperatura").apply {
            color = Color.RED
            setDrawCircles(false)
        }
        val humSet = LineDataSet(humEntries, "Humedad").apply {
            color = Color.BLUE
            setDrawCircles(false)
        }

        lineChart.data = LineData(tempSet, humSet)
        lineChart.invalidate()
    }
}

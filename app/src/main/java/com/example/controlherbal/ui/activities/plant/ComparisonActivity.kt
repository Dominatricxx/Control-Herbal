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

import com.example.controlherbal.common.accessibility.ChartColors
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.controlherbal.R
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.navigation.NavigationView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.fillMaxSize
import com.example.controlherbal.ui.components.AnimatedGradientBackground
import com.example.controlherbal.ui.theme.ControlHerbalTheme
import androidx.core.view.WindowCompat
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import com.example.controlherbal.ui.components.GooeyBottomBar
import com.example.controlherbal.ui.activities.privacy.PrivacyActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComparisonActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnSelectPlant1: Button
    private lateinit var btnSelectPlant2: Button
    private lateinit var comparisonChart: LineChart
    
    private lateinit var cbTemp: CheckBox
    private lateinit var cbHum: CheckBox
    private lateinit var cbSoil: CheckBox
    private lateinit var cbLuz: CheckBox
    private lateinit var cbIRH: CheckBox
    private lateinit var cbSeq: CheckBox
    private lateinit var cbSomb: CheckBox

    private lateinit var cardPlant1: View
    private lateinit var tvNameP1: TextView
    private lateinit var tvTempP1: TextView
    private lateinit var tvHumP1: TextView
    private lateinit var tvSoilP1: TextView
    private lateinit var tvLuzP1: TextView
    private lateinit var tvSeqP1: TextView
    private lateinit var tvSombP1: TextView
    private lateinit var tvIrhP1: TextView

    private lateinit var cardPlant2: View
    private lateinit var tvNameP2: TextView
    private lateinit var tvTempP2: TextView
    private lateinit var tvHumP2: TextView
    private lateinit var tvSoilP2: TextView
    private lateinit var tvLuzP2: TextView
    private lateinit var tvSeqP2: TextView
    private lateinit var tvSombP2: TextView
    private lateinit var tvIrhP2: TextView

    private lateinit var databaseLocal: SensorDatabase
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    private var plant1: Plant? = null
    private var plant2: Plant? = null
    private var readingsP1: List<SensorReading> = emptyList()
    private var readingsP2: List<SensorReading> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_comparison_drawer)

        findViewById<ComposeView>(R.id.composeBackgroundView).setContent {
            ControlHerbalTheme {
                AnimatedGradientBackground(modifier = androidx.compose.ui.Modifier.fillMaxSize())
            }
        }

        findViewById<ComposeView>(R.id.composeBottomBar).setContent {
            var selectedTab by remember { mutableStateOf(2) }
            val tabs = listOf(
                Triple("Panel", Icons.Default.Home, "Panel principal"),
                Triple("Sensores", Icons.Default.Search, "Historial"),
                Triple("Comparar", Icons.Default.Star, "Comparador"),
                Triple("Perfil", Icons.Default.Person, "Privacidad")
            )
            ControlHerbalTheme {
                GooeyBottomBar(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { index ->
                        selectedTab = index
                        when (index) {
                            0 -> { startActivity(Intent(this, MainActivity::class.java)); finish() }
                            1 -> { startActivity(Intent(this, HistoryActivity::class.java)); finish() }
                            2 -> {}
                            3 -> { startActivity(Intent(this, PrivacyActivity::class.java)); finish() }
                        }
                    }
                )
            }
        }

        databaseLocal = SensorDatabase.getInstance(this)
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
        
        navView.menu.findItem(R.id.nav_comparison).isVisible = false

        btnSelectPlant1 = findViewById(R.id.btnSelectPlant1)
        btnSelectPlant2 = findViewById(R.id.btnSelectPlant2)
        comparisonChart = findViewById(R.id.comparisonChart)

        cbTemp = findViewById(R.id.cbTemp)
        cbHum = findViewById(R.id.cbHum)
        cbSoil = findViewById(R.id.cbSoil)
        cbLuz = findViewById(R.id.cbLuz)
        cbIRH = findViewById(R.id.cbIRH)
        cbSeq = findViewById(R.id.cbSeq)
        cbSomb = findViewById(R.id.cbSomb)

        val chartListener = CompoundButton.OnCheckedChangeListener { _, _ -> updateChart() }
        cbTemp.setOnCheckedChangeListener(chartListener)
        cbHum.setOnCheckedChangeListener(chartListener)
        cbSoil.setOnCheckedChangeListener(chartListener)
        cbLuz.setOnCheckedChangeListener(chartListener)
        cbIRH.setOnCheckedChangeListener(chartListener)
        cbSeq.setOnCheckedChangeListener(chartListener)
        cbSomb.setOnCheckedChangeListener(chartListener)

        cardPlant1 = findViewById(R.id.cardPlant1)
        tvNameP1 = findViewById(R.id.tvNameP1)
        tvTempP1 = findViewById(R.id.tvTempP1)
        tvHumP1 = findViewById(R.id.tvHumP1)
        tvSoilP1 = findViewById(R.id.tvSoilP1)
        tvLuzP1 = findViewById(R.id.tvLuzP1)
        tvSeqP1 = findViewById(R.id.tvSeqP1)
        tvSombP1 = findViewById(R.id.tvSombP1)
        tvIrhP1 = findViewById(R.id.tvIrhP1)

        cardPlant2 = findViewById(R.id.cardPlant2)
        tvNameP2 = findViewById(R.id.tvNameP2)
        tvTempP2 = findViewById(R.id.tvTempP2)
        tvHumP2 = findViewById(R.id.tvHumP2)
        tvSoilP2 = findViewById(R.id.tvSoilP2)
        tvLuzP2 = findViewById(R.id.tvLuzP2)
        tvSeqP2 = findViewById(R.id.tvSeqP2)
        tvSombP2 = findViewById(R.id.tvSombP2)
        tvIrhP2 = findViewById(R.id.tvIrhP2)

        btnSelectPlant1.setOnClickListener { showPlantSelectionDialog(1) }
        btnSelectPlant2.setOnClickListener { showPlantSelectionDialog(2) }

        ioScope.launch {
            val count = databaseLocal.plantDao().getPlantCount()
            withContext(Dispatchers.Main) {
                navView.menu.findItem(R.id.nav_plants).isVisible = count >= 2
            }
        }
    }

    private fun showPlantSelectionDialog(slot: Int) {
        ioScope.launch {
            val plants = databaseLocal.plantDao().getAll()
            withContext(Dispatchers.Main) {
                val builder = AlertDialog.Builder(this@ComparisonActivity)
                val dialogView = layoutInflater.inflate(R.layout.dialog_rounded_list, null)
                builder.setView(dialogView)
                val dialog = builder.create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                
                val listView = dialogView.findViewById<ListView>(R.id.dialogListView)
                val adapter = object : ArrayAdapter<Plant>(this@ComparisonActivity, R.layout.item_plant_selection, R.id.tvItemPlantName, plants) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val row = convertView ?: layoutInflater.inflate(R.layout.item_plant_selection, parent, false)
                        val plant = getItem(position) ?: return row

                        val tvName = row.findViewById<TextView>(R.id.tvItemPlantName)
                        val tvDetails = row.findViewById<TextView>(R.id.tvItemPlantDetails)
                        val tvScientific = row.findViewById<TextView>(R.id.tvItemPlantScientific)

                        tvName.text = SecurityUtils.sanitizeText(plant.name)

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
                    selectPlant(plants[position], slot)
                    dialog.dismiss()
                }
                dialog.show()
            }
        }
    }

    private fun selectPlant(plant: Plant, slot: Int) {
        val safeName = SecurityUtils.sanitizeText(plant.name)
        if (slot == 1) {
            plant1 = plant
            btnSelectPlant1.text = safeName
            loadDataForPlant(plant, 1)
        } else {
            plant2 = plant
            btnSelectPlant2.text = safeName
            loadDataForPlant(plant, 2)
        }
    }

    private fun loadDataForPlant(plant: Plant, slot: Int) {
        ioScope.launch {
            val readings = databaseLocal.sensorDao().getLast2000Asc(plant.id)
            withContext(Dispatchers.Main) {
                if (slot == 1) {
                    readingsP1 = readings
                    updatePlantCard(1)
                } else {
                    readingsP2 = readings
                    updatePlantCard(2)
                }
                updateChart()
            }
        }
    }

    private fun updatePlantCard(slot: Int) {
        if (slot == 1) {
            val plant = plant1 ?: return
            val last = readingsP1.lastOrNull()
            cardPlant1.visibility = View.VISIBLE
            tvNameP1.text = SecurityUtils.sanitizeText(plant.name)
            tvTempP1.text = "Temp: ${last?.temperature ?: "--"}°C"
            tvHumP1.text = "Hum: ${last?.humidity ?: "--"}%"
            tvSoilP1.text = "Suelo: ${last?.soilMoisture ?: "--"}%"
            tvLuzP1.text = "Luz: ${last?.light ?: "--"}%"
            tvSeqP1.text = "Seq: ${last?.seq ?: "--"}h"
            tvSombP1.text = "Somb: ${last?.somb ?: "--"}h"
            tvIrhP1.text = "IRH: ${last?.irh ?: "--"}"
        } else {
            val plant = plant2 ?: return
            val last = readingsP2.lastOrNull()
            cardPlant2.visibility = View.VISIBLE
            tvNameP2.text = SecurityUtils.sanitizeText(plant.name)
            tvTempP2.text = "Temp: ${last?.temperature ?: "--"}°C"
            tvHumP2.text = "Hum: ${last?.humidity ?: "--"}%"
            tvSoilP2.text = "Suelo: ${last?.soilMoisture ?: "--"}%"
            tvLuzP2.text = "Luz: ${last?.light ?: "--"}%"
            tvSeqP2.text = "Seq: ${last?.seq ?: "--"}h"
            tvSombP2.text = "Somb: ${last?.somb ?: "--"}h"
            tvIrhP2.text = "IRH: ${last?.irh ?: "--"}"
        }
    }

    private fun updateChart() {
        val lineData = LineData()

        if (cbTemp.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
                lineData.addDataSet(ChartStyle.apply(LineDataSet(e, "${plant1?.name ?: "P1"} Temp"), ChartColors.TEMP, 0))
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
                lineData.addDataSet(ChartStyle.applySecondary(LineDataSet(e, "${plant2?.name ?: "P2"} Temp"), ChartColors.TEMP))
            }
        }
        
        if (cbHum.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
                lineData.addDataSet(ChartStyle.apply(LineDataSet(e, "${plant1?.name ?: "P1"} Hum"), ChartColors.HUM, 1))
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
                lineData.addDataSet(ChartStyle.applySecondary(LineDataSet(e, "${plant2?.name ?: "P2"} Hum"), ChartColors.HUM))
            }
        }

        if (cbSoil.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }
                lineData.addDataSet(ChartStyle.apply(LineDataSet(e, "${plant1?.name ?: "P1"} Suelo"), ChartColors.SOIL, 2))
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }
                lineData.addDataSet(ChartStyle.applySecondary(LineDataSet(e, "${plant2?.name ?: "P2"} Suelo"), ChartColors.SOIL))
            }
        }

        if (cbLuz.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
                lineData.addDataSet(ChartStyle.apply(LineDataSet(e, "${plant1?.name ?: "P1"} Luz"), ChartColors.LIGHT, 3))
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
                lineData.addDataSet(ChartStyle.applySecondary(LineDataSet(e, "${plant2?.name ?: "P2"} Luz"), ChartColors.LIGHT))
            }
        }

        if (cbIRH.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.irh.toFloat()) }
                lineData.addDataSet(ChartStyle.apply(LineDataSet(e, "${plant1?.name ?: "P1"} IRH"), ChartColors.IRH, 0))
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.irh.toFloat()) }
                lineData.addDataSet(ChartStyle.applySecondary(LineDataSet(e, "${plant2?.name ?: "P2"} IRH"), ChartColors.IRH))
            }
        }

        if (cbSeq.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.seq.toFloat()) }
                lineData.addDataSet(ChartStyle.apply(LineDataSet(e, "${plant1?.name ?: "P1"} Seq"), ChartColors.SEQ, 1))
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.seq.toFloat()) }
                lineData.addDataSet(ChartStyle.applySecondary(LineDataSet(e, "${plant2?.name ?: "P2"} Seq"), ChartColors.SEQ))
            }
        }

        if (cbSomb.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.somb.toFloat()) }
                lineData.addDataSet(ChartStyle.apply(LineDataSet(e, "${plant1?.name ?: "P1"} Somb"), ChartColors.SOMB, 2))
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.somb.toFloat()) }
                lineData.addDataSet(ChartStyle.applySecondary(LineDataSet(e, "${plant2?.name ?: "P2"} Somb"), ChartColors.SOMB))
            }
        }

        comparisonChart.data = lineData
        comparisonChart.description.isEnabled = false
        comparisonChart.invalidate()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_main -> startActivity(Intent(this, MainActivity::class.java))
            R.id.nav_daily -> startActivity(Intent(this, HistoryActivity::class.java).putExtra(AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_DIARIO))
            R.id.nav_weekly -> startActivity(Intent(this, HistoryActivity::class.java).putExtra(AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_SEMANAL))
            R.id.nav_monthly -> startActivity(Intent(this, HistoryActivity::class.java).putExtra(AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_MENSUAL))
            R.id.nav_plants -> startActivity(Intent(this, MainActivity::class.java))
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}

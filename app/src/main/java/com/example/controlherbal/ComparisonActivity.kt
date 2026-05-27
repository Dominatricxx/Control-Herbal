package com.example.controlherbal

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
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

    private lateinit var cardPlant1: View
    private lateinit var tvNameP1: TextView
    private lateinit var tvTempP1: TextView
    private lateinit var tvHumP1: TextView
    private lateinit var tvSoilP1: TextView
    private lateinit var tvLuzP1: TextView
    private lateinit var tvIrhP1: TextView

    private lateinit var cardPlant2: View
    private lateinit var tvNameP2: TextView
    private lateinit var tvTempP2: TextView
    private lateinit var tvHumP2: TextView
    private lateinit var tvSoilP2: TextView
    private lateinit var tvLuzP2: TextView
    private lateinit var tvIrhP2: TextView

    private lateinit var databaseLocal: SensorDatabase
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    private var plant1: Plant? = null
    private var plant2: Plant? = null
    private var readingsP1: List<SensorReading> = emptyList()
    private var readingsP2: List<SensorReading> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comparison_drawer)

        databaseLocal = SensorDatabase.getInstance(this)
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        colorDeleteMenuItem(navView)

        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        
        navView.menu.findItem(R.id.nav_comparison).isVisible = false

        btnSelectPlant1 = findViewById(R.id.btnSelectPlant1)
        btnSelectPlant2 = findViewById(R.id.btnSelectPlant2)
        comparisonChart = findViewById(R.id.comparisonChart)

        cbTemp = findViewById(R.id.cbTemp)
        cbHum = findViewById(R.id.cbHum)
        cbSoil = findViewById(R.id.cbSoil)
        cbLuz = findViewById(R.id.cbLuz)
        cbIRH = findViewById(R.id.cbIRH)

        val chartListener = CompoundButton.OnCheckedChangeListener { _, _ -> updateChart() }
        cbTemp.setOnCheckedChangeListener(chartListener)
        cbHum.setOnCheckedChangeListener(chartListener)
        cbSoil.setOnCheckedChangeListener(chartListener)
        cbLuz.setOnCheckedChangeListener(chartListener)
        cbIRH.setOnCheckedChangeListener(chartListener)

        cardPlant1 = findViewById(R.id.cardPlant1)
        tvNameP1 = findViewById(R.id.tvNameP1)
        tvTempP1 = findViewById(R.id.tvTempP1)
        tvHumP1 = findViewById(R.id.tvHumP1)
        tvSoilP1 = findViewById(R.id.tvSoilP1)
        tvLuzP1 = findViewById(R.id.tvLuzP1)
        tvIrhP1 = findViewById(R.id.tvIrhP1)

        cardPlant2 = findViewById(R.id.cardPlant2)
        tvNameP2 = findViewById(R.id.tvNameP2)
        tvTempP2 = findViewById(R.id.tvTempP2)
        tvHumP2 = findViewById(R.id.tvHumP2)
        tvSoilP2 = findViewById(R.id.tvSoilP2)
        tvLuzP2 = findViewById(R.id.tvLuzP2)
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

    private fun colorDeleteMenuItem(navView: NavigationView) {
        val menu = navView.menu
        val deleteItem = menu.findItem(R.id.nav_delete_plant)
        val s = SpannableString(deleteItem.title)
        s.setSpan(ForegroundColorSpan(Color.RED), 0, s.length, 0)
        deleteItem.title = s
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
                val adapter = object : ArrayAdapter<Plant>(this@ComparisonActivity, R.layout.item_plant_selection, plants) {
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        val plant = getItem(position)
                        view.text = plant?.name ?: ""
                        return view
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
        if (slot == 1) {
            plant1 = plant
            btnSelectPlant1.text = plant.name
            loadDataForPlant(plant, 1)
        } else {
            plant2 = plant
            btnSelectPlant2.text = plant.name
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
            tvNameP1.text = plant.name
            tvTempP1.text = "Temp: ${last?.temperature ?: "--"}°C"
            tvHumP1.text = "Hum: ${last?.humidity ?: "--"}%"
            tvSoilP1.text = "Suelo: ${last?.soilMoisture ?: "--"}%"
            tvLuzP1.text = "Luz: ${last?.light ?: "--"}%"
            tvIrhP1.text = "IRH: ${last?.irh ?: "--"}"
        } else {
            val plant = plant2 ?: return
            val last = readingsP2.lastOrNull()
            cardPlant2.visibility = View.VISIBLE
            tvNameP2.text = plant.name
            tvTempP2.text = "Temp: ${last?.temperature ?: "--"}°C"
            tvHumP2.text = "Hum: ${last?.humidity ?: "--"}%"
            tvSoilP2.text = "Suelo: ${last?.soilMoisture ?: "--"}%"
            tvLuzP2.text = "Luz: ${last?.light ?: "--"}%"
            tvIrhP2.text = "IRH: ${last?.irh ?: "--"}"
        }
    }

    private fun updateChart() {
        val lineData = LineData()

        if (cbTemp.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant1?.name ?: "P1"} Temp").apply { color = Color.RED; setDrawCircles(false) })
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant2?.name ?: "P2"} Temp").apply { color = Color.parseColor("#88FF0000"); setDrawCircles(false); enableDashedLine(10f, 5f, 0f) })
            }
        }
        
        if (cbHum.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant1?.name ?: "P1"} Hum").apply { color = Color.BLUE; setDrawCircles(false) })
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant2?.name ?: "P2"} Hum").apply { color = Color.parseColor("#880000FF"); setDrawCircles(false); enableDashedLine(10f, 5f, 0f) })
            }
        }

        if (cbSoil.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant1?.name ?: "P1"} Suelo").apply { color = Color.GREEN; setDrawCircles(false) })
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant2?.name ?: "P2"} Suelo").apply { color = Color.parseColor("#8800FF00"); setDrawCircles(false); enableDashedLine(10f, 5f, 0f) })
            }
        }

        if (cbLuz.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant1?.name ?: "P1"} Luz").apply { color = Color.YELLOW; setDrawCircles(false) })
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant2?.name ?: "P2"} Luz").apply { color = Color.parseColor("#88FFFF00"); setDrawCircles(false); enableDashedLine(10f, 5f, 0f) })
            }
        }

        if (cbIRH.isChecked) {
            if (readingsP1.isNotEmpty()) {
                val e = readingsP1.mapIndexed { i, r -> Entry(i.toFloat(), r.irh.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant1?.name ?: "P1"} IRH").apply { color = Color.DKGRAY; setDrawCircles(false) })
            }
            if (readingsP2.isNotEmpty()) {
                val e = readingsP2.mapIndexed { i, r -> Entry(i.toFloat(), r.irh.toFloat()) }
                lineData.addDataSet(LineDataSet(e, "${plant2?.name ?: "P2"} IRH").apply { color = Color.parseColor("#88444444"); setDrawCircles(false); enableDashedLine(10f, 5f, 0f) })
            }
        }

        comparisonChart.data = lineData
        comparisonChart.description.isEnabled = false
        comparisonChart.invalidate()
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_main -> startActivity(Intent(this, MainActivity::class.java))
            R.id.nav_daily -> startActivity(Intent(this, HistoryActivity::class.java).putExtra("HISTORY_TYPE", "DIARIO"))
            R.id.nav_weekly -> startActivity(Intent(this, HistoryActivity::class.java).putExtra("HISTORY_TYPE", "SEMANAL"))
            R.id.nav_monthly -> startActivity(Intent(this, HistoryActivity::class.java).putExtra("HISTORY_TYPE", "MENSUAL"))
            R.id.nav_plants -> startActivity(Intent(this, MainActivity::class.java))
            R.id.nav_delete_plant -> startActivity(Intent(this, MainActivity::class.java))
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}

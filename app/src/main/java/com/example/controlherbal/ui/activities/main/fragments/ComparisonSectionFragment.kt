package com.example.controlherbal.ui.activities.main.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Comparador funcional: selección de plantas, métricas y gráficas seleccionables. */
class ComparisonSectionFragment : Fragment() {
    private lateinit var db: SensorDatabase
    private lateinit var chart: LineChart
    private lateinit var checks: List<CheckBox>
    private lateinit var buttons: List<Button>
    private var plants: Array<Plant> = emptyArray()
    private var readings = arrayOf(emptyList<SensorReading>(), emptyList())
    private lateinit var cards: List<View>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.activity_comparison, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        db = SensorDatabase.getInstance(requireContext())
        chart = view.findViewById(R.id.comparisonChart)
        checks = listOf(R.id.cbTemp, R.id.cbHum, R.id.cbSoil, R.id.cbLuz, R.id.cbIRH, R.id.cbSeq, R.id.cbSomb)
            .map(view::findViewById)
        checks.forEach { it.setOnCheckedChangeListener { _, _ -> drawChart() } }
        buttons = listOf(view.findViewById(R.id.btnSelectPlant1), view.findViewById(R.id.btnSelectPlant2))
        buttons.forEachIndexed { index, button -> button.setOnClickListener { selectPlant(index) } }
        cards = listOf(view.findViewById(R.id.cardPlant1), view.findViewById(R.id.cardPlant2))
        loadPlants()
    }

    private fun loadPlants() {
        viewLifecycleOwner.lifecycleScope.launch {
            plants = db.plantDao().getAll().toTypedArray()
            withContext(Dispatchers.Main) {
                if (plants.isEmpty()) {
                    Toast.makeText(requireContext(), "Añade al menos una planta para comparar", Toast.LENGTH_LONG).show()
                } else {
                    select(0, plants.first())
                    plants.getOrNull(1)?.let { select(1, it) }
                }
            }
        }
    }

    private fun selectPlant(slot: Int) {
        if (plants.isEmpty()) { loadPlants(); return }
        AlertDialog.Builder(requireContext()).setTitle("Seleccionar planta")
            .setItems(plants.map { SecurityUtils.sanitizeText(it.name) }.toTypedArray()) { _, index ->
                select(slot, plants[index])
            }.show()
    }

    private fun select(slot: Int, plant: Plant) {
        buttons[slot].text = SecurityUtils.sanitizeText(plant.name)
        viewLifecycleOwner.lifecycleScope.launch {
            readings[slot] = db.sensorDao().getLast2000Asc(plant.id)
            renderCard(slot, plant)
            drawChart()
        }
    }

    private fun renderCard(slot: Int, plant: Plant) {
        val last = readings[slot].lastOrNull()
        val suffix = if (slot == 0) "P1" else "P2"
        val values = listOf(
            "tvName$suffix" to SecurityUtils.sanitizeText(plant.name),
            "tvTemp$suffix" to "Temp: ${last?.temperature ?: "--"}°C",
            "tvHum$suffix" to "Hum: ${last?.humidity ?: "--"}%",
            "tvSoil$suffix" to "Suelo: ${last?.soilMoisture ?: "--"}%",
            "tvLuz$suffix" to "Luz: ${last?.light ?: "--"}%",
            "tvSeq$suffix" to "Seq: ${last?.seq ?: "--"}h",
            "tvSomb$suffix" to "Somb: ${last?.somb ?: "--"}h",
            "tvIrh$suffix" to "IRH: ${last?.irh ?: "--"}"
        )
        values.forEach { (name, value) ->
            val id = resources.getIdentifier(name, "id", requireContext().packageName)
            view?.findViewById<TextView>(id)?.text = value
        }
        cards[slot].isVisible = true
    }

    private fun drawChart() {
        val fields: List<Pair<String, (SensorReading) -> Float>> = listOf(
            "Temp" to { it.temperature.toFloat() },
            "Hum" to { it.humidity.toFloat() },
            "Suelo" to { it.soilMoisture.toFloat() },
            "Luz" to { it.light.toFloat() },
            "IRH" to { it.irh.toFloat() },
            "Seq" to { it.seq.toFloat() },
            "Somb" to { it.somb.toFloat() }
        )
        val data = LineData()
        fields.forEachIndexed { index, (label, value) ->
            if (!checks[index].isChecked) return@forEachIndexed
            readings.forEachIndexed { slot, list ->
                if (list.isNotEmpty()) {
                    val entries = list.mapIndexed { i, item -> Entry(i.toFloat(), value(item)) }
                    val name = plants.getOrNull(slot)?.name ?: "P${slot + 1}"
                    data.addDataSet(LineDataSet(entries, "$name $label").apply {
                        color = if (slot == 0) Color.rgb(46, 125, 50) else Color.rgb(30, 136, 229)
                        lineWidth = 2f
                        setDrawValues(false)
                    })
                }
            }
        }
        chart.data = data
        chart.description.isEnabled = false
        chart.invalidate()
    }
}

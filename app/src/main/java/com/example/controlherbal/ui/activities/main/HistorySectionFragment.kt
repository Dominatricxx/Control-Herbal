package com.example.controlherbal.ui.activities.main

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.common.accessibility.StatusLevel
import com.example.controlherbal.common.accessibility.StatusPalette
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Historial diario/semanal/mensual real con cálculos IA y controles de gráficas. */
class HistorySectionFragment : Fragment() {
    private data class HistoryLine(val index: Int, val label: String, val color: Int, val value: (SensorReading) -> Float)
    private lateinit var db: SensorDatabase
    private var type = AppConstants.HISTORY_DIARIO
    private var currentPlant: Plant? = null
    private lateinit var chart: CombinedChart
    private lateinit var checks: List<CheckBox>
    private var importProgress: AlertDialog? = null

    companion object {
        fun newInstance(type: String) = HistorySectionFragment().apply {
            arguments = Bundle().apply { putString(AppConstants.HISTORY_TYPE_KEY, type) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = arguments?.getString(AppConstants.HISTORY_TYPE_KEY) ?: AppConstants.HISTORY_DIARIO
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.activity_history, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        db = SensorDatabase.getInstance(requireContext())
        chart = view.findViewById(R.id.combinedChart)
        checks = listOf(R.id.cbTemp, R.id.cbHum, R.id.cbSoil, R.id.cbLuz, R.id.cbIRH).map(view::findViewById)
        checks.forEach { it.setOnCheckedChangeListener { _, _ -> loadHistory() } }
        view.findViewById<View>(R.id.btnDataControl).setOnClickListener { showDataControls() }
        loadHistory()
    }

    fun setHistoryType(value: String) {
        type = value
        if (isAdded && view != null) updateTitleAndLoad()
    }

    override fun onResume() { super.onResume(); loadHistory() }
    override fun onDestroyView() {
        importProgress?.dismiss()
        importProgress = null
        super.onDestroyView()
    }

    private fun updateTitleAndLoad() {
        view?.findViewById<TextView>(R.id.tvHistoryTitle)?.text = "Registro $type · Análisis detallado"
        loadHistory()
    }

    private fun loadHistory() {
        if (!isAdded || !::db.isInitialized) return
        viewLifecycleOwner.lifecycleScope.launch {
            currentPlant = db.plantDao().getSelectedPlant()
            val selected = currentPlant ?: return@launch
            val now = System.currentTimeMillis()
            val period = when (type) {
                AppConstants.HISTORY_SEMANAL -> 7L * 24 * 3600 * 1000
                AppConstants.HISTORY_MENSUAL -> 30L * 24 * 3600 * 1000
                else -> 24L * 3600 * 1000
            }
            val readings = db.sensorDao().getReadingsBetween(selected.id, now - period, now)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (readings.isEmpty()) {
                    chart.clear()
                    chart.setNoDataText("No hay registros para este periodo")
                    listOf(R.id.tvAvgTemp, R.id.tvAvgHum, R.id.tvAvgSoil, R.id.tvAvgLuz, R.id.tvAvgIRH)
                        .forEach { view?.findViewById<TextView>(it)?.text = "--" }
                    return@withContext
                }
                val avgs = listOf(
                    readings.map { it.temperature }.average(),
                    readings.map { it.humidity }.average(),
                    readings.map { it.soilMoisture }.average(),
                    readings.map { it.light }.average()
                )
                val text = listOf(
                    String.format(Locale.getDefault(), "%.1f °C", avgs[0]),
                    String.format(Locale.getDefault(), "%.1f %%", avgs[1]),
                    String.format(Locale.getDefault(), "%.1f %%", avgs[2]),
                    String.format(Locale.getDefault(), "%.1f %%", avgs[3])
                )
                listOf(R.id.tvAvgTemp, R.id.tvAvgHum, R.id.tvAvgSoil, R.id.tvAvgLuz).zip(text)
                    .forEach { (id, value) -> view?.findViewById<TextView>(id)?.text = value }
                val aiIrh = HerbalAI(requireContext()).predictRefinedIRH(avgs[0], avgs[1], avgs[3], avgs[2], selected.type)
                val irh = (readings.map { it.irh }.average() + aiIrh) / 2
                val level = when {
                    irh > PredictiveTheorem.IRH_RIESGO -> StatusLevel.CRITICAL
                    irh > PredictiveTheorem.IRH_ADVERTENCIA -> StatusLevel.WARN
                    else -> StatusLevel.OK
                }
                view?.findViewById<TextView>(R.id.tvAvgIRH)?.apply {
                    this.text = "${level.symbol} ${String.format(Locale.getDefault(), "%.1f (IA)", irh)}"
                    setTextColor(StatusPalette.current(requireContext()).forLevel(level))
                }
                drawHistory(readings)
            }
        }
    }

    private fun drawHistory(readings: List<SensorReading>) {
        val data = LineData()
        val configs = listOf(
            HistoryLine(0, "Temperatura", 0xFFE53935.toInt()) { it.temperature.toFloat() },
            HistoryLine(1, "Humedad", 0xFF1E88E5.toInt()) { it.humidity.toFloat() },
            HistoryLine(2, "Suelo", 0xFF43A047.toInt()) { it.soilMoisture.toFloat() },
            HistoryLine(3, "Luz", 0xFFFFB300.toInt()) { it.light.toFloat() }
        )
        configs.forEach { line ->
            if (checks[line.index].isChecked) {
                val entries = readings.mapIndexed { i, reading -> Entry(i.toFloat(), line.value(reading)) }
                data.addDataSet(LineDataSet(entries, line.label).apply {
                    color = line.color
                    setDrawValues(false)
                    lineWidth = 2f
                })
            }
        }
        chart.data = CombinedData().apply {
            setData(data)
            if (checks[4].isChecked) {
                val bars = readings.mapIndexed { index, reading ->
                    BarEntry(index.toFloat(), reading.irh.toFloat())
                }
                setData(BarData(BarDataSet(bars, "IRH").apply {
                    color = Color.argb(150, 211, 47, 47)
                    setDrawValues(false)
                }).apply { barWidth = 0.5f })
            }
        }
        chart.description.isEnabled = false
        chart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            private val daily = SimpleDateFormat("HH:mm", Locale.getDefault())
            private val full = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                val index = value.toInt()
                if (index !in readings.indices) return ""
                return (if (type == AppConstants.HISTORY_DIARIO) daily else full).format(Date(readings[index].timestamp))
            }
        }
        chart.invalidate()
    }

    private fun showDataControls() {
        val selected = currentPlant ?: return
        AlertDialog.Builder(requireContext()).setTitle("Gestión de datos")
            .setItems(arrayOf("Importar historial de Firebase", "Restablecer datos locales", "Cancelar")) { _, choice ->
                when (choice) {
                    0 -> importFirebase(selected)
                    1 -> AlertDialog.Builder(requireContext()).setItems(
                        arrayOf("Últimas 24 horas", "Últimos 7 días", "Último mes", "Todos los registros", "Cancelar")
                    ) { _, period ->
                        if (period in 0..3) {
                            val now = System.currentTimeMillis()
                            val duration = when (period) {
                                0 -> 24L * 3600 * 1000
                                1 -> 7L * 24 * 3600 * 1000
                                2 -> 30L * 24 * 3600 * 1000
                                else -> Long.MAX_VALUE
                            }
                            viewLifecycleOwner.lifecycleScope.launch {
                                if (period == 3) db.sensorDao().deleteAllByPlantId(selected.id)
                                else db.sensorDao().deleteReadingsBetween(selected.id, now - duration, now)
                                loadHistory()
                            }
                        }
                    }.show()
                }
            }.show()
    }

    private fun importFirebase(selected: Plant) {
        val ref = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference(AppConstants.FIREBASE_SENSOR_NODE)
        val progress = AlertDialog.Builder(requireContext()).setMessage("Importando registros desde la nube…")
            .setCancelable(false).show()
        importProgress = progress
        val viewScope = viewLifecycleOwner.lifecycleScope
        ref.get().addOnSuccessListener { snapshot ->
            viewScope.launch {
                var count = 0
                suspend fun add(node: com.google.firebase.database.DataSnapshot) {
                    val temp = (node.child("temp").value as? Number)?.toDouble() ?: return
                    db.sensorDao().insert(SensorReading(
                        timestamp = (node.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis(),
                        plantId = selected.id,
                        temperature = temp,
                        humidity = (node.child("hum").value as? Number)?.toDouble() ?: 0.0,
                        light = PredictiveTheorem.filtrarSensibilidadLuz(
                            (node.child("luz_raw").value as? Number)?.toDouble()
                                ?: (node.child("luz").value as? Number)?.toDouble() ?: 0.0
                        ),
                        soilMoisture = (node.child("soil").value as? Number)?.toDouble() ?: 0.0,
                        irh = (node.child("irh").value as? Number)?.toDouble() ?: 0.0,
                        seq = (node.child("seq").value as? Number)?.toDouble() ?: 0.0,
                        somb = (node.child("somb").value as? Number)?.toDouble() ?: 0.0,
                        action = SecurityUtils.sanitizeText(node.child("acc").value as? String ?: "")
                    ))
                    count++
                }
                if (snapshot.hasChild("history")) snapshot.child("history").children.forEach { add(it) }
                else if (snapshot.exists()) add(snapshot)
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    if (importProgress === progress) importProgress = null
                    Toast.makeText(requireContext(), if (count > 0) "Importados $count registros" else "No se encontraron registros válidos", Toast.LENGTH_LONG).show()
                    loadHistory()
                }
            }
        }.addOnFailureListener {
            progress.dismiss()
            if (importProgress === progress) importProgress = null
            Toast.makeText(requireContext(), "No se pudo importar desde Firebase", Toast.LENGTH_SHORT).show()
        }
    }
}

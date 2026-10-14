package com.example.controlherbal.ui.activities.main

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.controlherbal.R
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.common.accessibility.PaletteMode
import com.example.controlherbal.common.accessibility.StatusLevel
import com.example.controlherbal.common.accessibility.StatusPalette
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.legal.AiConsent
import com.example.controlherbal.common.legal.ConsentManager
import com.example.controlherbal.common.legal.DataControl
import com.example.controlherbal.common.legal.LegalDoc
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.data.repository.PlantRepositoryImpl
import com.example.controlherbal.data.repository.SensorDataRepositoryImpl
import com.example.controlherbal.data.sync.FirebaseSensorSyncManager
import com.example.controlherbal.data.sync.SensorForegroundService
import com.example.controlherbal.domain.logic.PredictiveTheorem
import com.example.controlherbal.domain.usecase.ExecuteWateringUseCase
import com.example.controlherbal.ui.controller.WateringController
import com.example.controlherbal.ui.helper.ChartManager
import com.example.controlherbal.ui.presenter.SensorDashboardPresenter
import com.example.controlherbal.ui.style.ChartStyle
import com.example.controlherbal.ui.activities.plant.PlantSetupActivity
import com.example.controlherbal.ui.activities.privacy.LegalActivity
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class SectionPagerAdapter(
    activity: SectionHostActivity,
    private val historyType: String
) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 4
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> DashboardSectionFragment()
        1 -> HistorySectionFragment.newInstance(historyType)
        2 -> ComparisonSectionFragment()
        else -> PrivacySectionFragment()
    }
}

/** Reusa la UI real del dashboard, sus gráficos, control de riego, sincronización y gestión local. */
class DashboardSectionFragment : Fragment() {
    private lateinit var db: SensorDatabase
    private lateinit var plantRepository: PlantRepositoryImpl
    private lateinit var presenter: SensorDashboardPresenter
    private lateinit var chart: CombinedChart
    private lateinit var checks: List<CheckBox>
    private var plant: Plant? = null
    private var sync: FirebaseSensorSyncManager? = null
    private var importProgress: AlertDialog? = null
    private lateinit var watering: WateringController
    private val ioScope get() = viewLifecycleOwner.lifecycleScope

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.activity_main, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        db = SensorDatabase.getInstance(requireContext())
        plantRepository = PlantRepositoryImpl(db.plantDao())
        watering = WateringController(ExecuteWateringUseCase(SensorDataRepositoryImpl(db.sensorDao())))
        view.findViewById<View>(R.id.btnMenu)?.setOnClickListener { (activity as? SectionHostActivity)?.openDrawer() }
        presenter = SensorDashboardPresenter(
            requireContext(),
            view.findViewById(R.id.tvAlerta),
            view.findViewById(R.id.tvConnectionStatus),
            view.findViewById(R.id.tvConnectionState),
            view.findViewById(R.id.tvTemp),
            view.findViewById(R.id.tvHum),
            view.findViewById(R.id.tvLuz),
            view.findViewById(R.id.tvSoil),
            view.findViewById(R.id.tvIRH),
            view.findViewById(R.id.tvSeq),
            view.findViewById(R.id.tvSomb)
        )
        chart = view.findViewById(R.id.combinedChart)
        checks = listOf(R.id.cbTemp, R.id.cbHum, R.id.cbSoil, R.id.cbLuz, R.id.cbIRH).map(view::findViewById)
        checks.forEach { it.setOnCheckedChangeListener { _, _ -> reloadChart() } }
        view.findViewById<View>(R.id.btnConnect).setOnClickListener { presenter.showLinkingState(); ensureSync().startListening() }
        view.findViewById<View>(R.id.btnWatering).setOnClickListener {
            plant?.let { watering.showWateringDialog(requireActivity(), it, ioScope) { reloadChart() } }
                ?: toast("Configura una planta antes de regar")
        }
        view.findViewById<View>(R.id.btnAddPlant).setOnClickListener {
            startActivity(Intent(requireContext(), PlantSetupActivity::class.java))
        }
        view.findViewById<View>(R.id.tvPlantNameAndEmoji).setOnClickListener { showPlantSelection() }
        view.findViewById<View>(R.id.btnEditNameIcon).setOnClickListener { editPlantName() }
        view.findViewById<View>(R.id.btnDeletePlant).setOnClickListener { deletePlant() }
        view.findViewById<View>(R.id.btnDataControl).setOnClickListener { showDataControls() }
        view.findViewById<View>(R.id.btnClearAiDiagnosis).setOnClickListener { clearDiagnosis() }
    }

    override fun onResume() {
        super.onResume()
        ioScope.launch {
            plant = plantRepository.getSelectedPlant()
            if (plant == null) {
                startActivity(Intent(requireContext(), PlantSetupActivity::class.java))
                return@launch
            }
            plant?.let {
                requireView().findViewById<TextView>(R.id.tvPlantNameAndEmoji).text = it.name
                requireView().findViewById<View>(R.id.layoutPlantInfo).isVisible = true
                it.aiDiagnosis?.takeIf(String::isNotBlank)?.let { diagnosis ->
                    requireView().findViewById<View>(R.id.cardAiDiagnosis).isVisible = true
                    requireView().findViewById<TextView>(R.id.tvAiDiagnosisTitle).text = "Diagnóstico guardado"
                    requireView().findViewById<TextView>(R.id.tvAiDiagnosisBody).text = diagnosis
                }
                db.sensorDao().getLast2000Asc(it.id).lastOrNull()?.let { reading ->
                    presenter.updateMetrics(
                        reading.temperature, reading.humidity, reading.light, reading.soilMoisture,
                        reading.irh, reading.seq, reading.somb
                    )
                } ?: presenter.showDisconnectedState()
                reloadChart()
                startSensorService()
                ensureSync().startListening()
            }
        }
    }

    override fun onPause() {
        sync?.stopListening()
        super.onPause()
    }

    override fun onDestroyView() {
        sync?.stopListening()
        importProgress?.dismiss()
        importProgress = null
        super.onDestroyView()
    }

    private fun ensureSync(): FirebaseSensorSyncManager {
        if (sync == null) sync = FirebaseSensorSyncManager(
            onReadingReceived = { temp, hum, light, soil, result ->
                activity?.runOnUiThread {
                    presenter.updateMetrics(temp, hum, light, soil, result.irh, result.seq, result.somb)
                    reloadChart()
                }
            },
            onDisconnected = { activity?.runOnUiThread { presenter.showDisconnectedState() } }
        )
        return sync!!
    }

    private fun startSensorService() {
        val serviceIntent = Intent(requireContext(), SensorForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) requireContext().startForegroundService(serviceIntent)
        else requireContext().startService(serviceIntent)
    }

    private fun reloadChart() {
        val selected = plant ?: return
        ioScope.launch {
            val readings = db.sensorDao().getLast2000Asc(selected.id)
            withContext(Dispatchers.Main) {
                if (!isAdded || !::chart.isInitialized) return@withContext
                ChartManager.setupCombinedChart(
                    chart, readings,
                    checks[0].isChecked, checks[1].isChecked, checks[2].isChecked,
                    checks[3].isChecked, checks[4].isChecked
                )
            }
        }
    }

    fun showPlantSelection() {
        ioScope.launch {
            val plants = plantRepository.getAllPlants()
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val names = plants.map { SecurityUtils.sanitizeText(it.name) }
                AlertDialog.Builder(requireContext()).setTitle("Seleccionar planta")
                    .setItems(names.toTypedArray()) { _, index ->
                        ioScope.launch { selectPlant(plants[index]) }
                    }.setPositiveButton("Añadir") { _, _ ->
                        startActivity(Intent(requireContext(), PlantSetupActivity::class.java))
                    }.show()
            }
        }
    }

    private suspend fun selectPlant(selected: Plant) {
        plantRepository.selectPlant(selected)
        plant = selected
        withContext(Dispatchers.Main) {
            if (isAdded) {
                    view?.findViewById<TextView>(R.id.tvPlantNameAndEmoji)?.text = selected.name
                    reloadChart()
            }
        }
    }

    private fun editPlantName() {
        val current = plant ?: return
        val input = android.widget.EditText(requireContext()).apply { setText(current.name) }
        AlertDialog.Builder(requireContext()).setTitle("Editar nombre").setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = SecurityUtils.sanitizeText(input.text.toString().trim())
                if (newName.isNotBlank()) ioScope.launch {
                    db.plantDao().update(current.copy(name = newName))
                    plant = current.copy(name = newName)
                    requireView().findViewById<TextView>(R.id.tvPlantNameAndEmoji).text = newName
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun deletePlant() {
        val current = plant ?: return
        AlertDialog.Builder(requireContext()).setTitle("Eliminar planta")
            .setMessage("Se borrará '${SecurityUtils.sanitizeText(current.name)}' y sus registros.")
            .setPositiveButton("Eliminar") { _, _ -> ioScope.launch {
                db.sensorDao().deleteAllByPlantId(current.id)
                db.plantDao().delete(current)
                val remaining = db.plantDao().getAll()
                if (remaining.isEmpty()) startActivity(Intent(requireContext(), PlantSetupActivity::class.java))
                else {
                    db.plantDao().deselectAll()
                    db.plantDao().update(remaining.first().copy(isSelected = true))
                    plant = remaining.first()
                    reloadChart()
                }
            } }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showDataControls() {
        val options = arrayOf("Importar historial de Firebase", "Restablecer registros locales", "Cancelar")
        AlertDialog.Builder(requireContext()).setTitle("Gestión de datos")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importFirebase()
                    1 -> AlertDialog.Builder(requireContext()).setMessage("¿Eliminar todos los registros locales de esta planta?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            plant?.let { current -> ioScope.launch {
                                db.sensorDao().deleteAllByPlantId(current.id)
                                reloadChart()
                            } }
                        }.setNegativeButton(android.R.string.cancel, null).show()
                }
            }.show()
    }

    private fun importFirebase() {
        val selected = plant ?: return
        val ref = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference(AppConstants.FIREBASE_SENSOR_NODE)
        val progress = AlertDialog.Builder(requireContext()).setMessage("Importando registros…").setCancelable(false).show()
        importProgress = progress
        val viewScope = viewLifecycleOwner.lifecycleScope
        ref.get().addOnSuccessListener { snapshot ->
            viewScope.launch {
                var imported = 0
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
                    imported++
                }
                if (snapshot.hasChild("history")) snapshot.child("history").children.forEach { add(it) } else if (snapshot.exists()) add(snapshot)
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    if (importProgress === progress) importProgress = null
                    toast(if (imported > 0) "Importados $imported registros" else "No se encontraron registros válidos")
                    reloadChart()
                }
            }
        }.addOnFailureListener {
            progress.dismiss()
            if (importProgress === progress) importProgress = null
            toast("No se pudo importar desde Firebase")
        }
    }

    private fun clearDiagnosis() {
        val selected = plant ?: return
        ioScope.launch {
            db.plantDao().update(selected.copy(aiDiagnosis = null, aiRecommendation = null))
            plant = selected.copy(aiDiagnosis = null, aiRecommendation = null)
            requireView().findViewById<View>(R.id.cardAiDiagnosis).isVisible = false
        }
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}

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

/** Centro de privacidad completo como página del carrusel. */
class PrivacySectionFragment : Fragment() {
    private lateinit var switchAi: SwitchMaterial
    private lateinit var palette: RadioGroup

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.activity_privacy, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        view.findViewById<ImageButton>(R.id.btnPrivacyBack).apply {
            isVisible = false
        }
        val accepted = ConsentManager.acceptedAt(requireContext())
        view.findViewById<TextView>(R.id.tvAcceptedInfo).text = getString(
            R.string.privacy_accepted_info,
            ConsentManager.acceptedVersion(requireContext()) ?: "-",
            if (accepted > 0) DateFormat.getDateTimeInstance().format(Date(accepted)) else "-"
        )
        mapOf(
            R.id.btnDocPrivacy to LegalDoc.PRIVACY,
            R.id.btnDocTerms to LegalDoc.TERMS,
            R.id.btnDocCookies to LegalDoc.COOKIES,
            R.id.btnDocConsents to LegalDoc.CONSENTS
        ).forEach { (id, doc) ->
            view.findViewById<Button>(id).setOnClickListener {
                startActivity(LegalActivity.intent(requireContext(), doc))
            }
        }
        switchAi = view.findViewById(R.id.switchAi)
        switchAi.isChecked = AiConsent.isGranted(requireContext())
        switchAi.setOnClickListener {
            if (switchAi.isChecked) {
                switchAi.isChecked = false
                AiConsent.ensure(requireActivity(), onGranted = { switchAi.isChecked = true })
            } else AiConsent.revoke(requireContext())
        }
        setupPalette(view)
        view.findViewById<Button>(R.id.btnExport).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val json = DataControl.exportLocalJson(requireContext())
                if (json == null) Toast.makeText(requireContext(), R.string.privacy_export_empty, Toast.LENGTH_SHORT).show()
                else startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Control Herbal - mis datos")
                    putExtra(Intent.EXTRA_TEXT, json)
                }, getString(R.string.privacy_export_chooser)))
            }
        }
        view.findViewById<Button>(R.id.btnWipe).setOnClickListener {
            confirm(R.string.privacy_wipe, R.string.privacy_wipe_confirm) {
                viewLifecycleOwner.lifecycleScope.launch {
                    requireContext().stopService(Intent(requireContext(), SensorForegroundService::class.java))
                    DataControl.wipeLocal(requireContext())
                    Toast.makeText(requireContext(), R.string.privacy_done, Toast.LENGTH_SHORT).show()
                    returnToSplash()
                }
            }
        }
        view.findViewById<Button>(R.id.btnDeleteAccount).setOnClickListener {
            confirm(R.string.privacy_delete_account, R.string.privacy_delete_confirm) {
                viewLifecycleOwner.lifecycleScope.launch {
                    when (DataControl.deleteAccountAndData(requireContext())) {
                        AuthManager.DeleteResult.SUCCESS -> {
                            requireContext().stopService(Intent(requireContext(), SensorForegroundService::class.java))
                            Toast.makeText(requireContext(), R.string.privacy_done, Toast.LENGTH_SHORT).show()
                            returnToSplash()
                        }
                        AuthManager.DeleteResult.REQUIRES_RECENT_LOGIN -> Toast.makeText(requireContext(), R.string.privacy_delete_relogin, Toast.LENGTH_LONG).show()
                        AuthManager.DeleteResult.FAILED -> Toast.makeText(requireContext(), R.string.privacy_delete_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupPalette(view: View) {
        palette = view.findViewById(R.id.rgPalette)
        PaletteMode.values().forEach { mode ->
            palette.addView(RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = mode.label
                tag = mode
                textSize = 16f
                minHeight = (48 * resources.displayMetrics.density).toInt()
                isChecked = mode == StatusPalette.mode(requireContext())
            })
        }
        palette.setOnCheckedChangeListener { group, checked ->
            (group.findViewById<RadioButton>(checked)?.tag as? PaletteMode)?.let {
                StatusPalette.setMode(requireContext(), it)
                renderPalette(view)
            }
        }
        renderPalette(view)
    }

    private fun renderPalette(view: View) {
        val colors = StatusPalette.current(requireContext())
        listOf(
            Triple(R.id.previewOk, StatusLevel.OK, R.string.privacy_preview_ok),
            Triple(R.id.previewWarn, StatusLevel.WARN, R.string.privacy_preview_warn),
            Triple(R.id.previewCritical, StatusLevel.CRITICAL, R.string.privacy_preview_critical)
        ).forEach { (id, level, string) ->
            view.findViewById<TextView>(id).apply {
                text = "${level.symbol} ${getString(string)}"
                background = GradientDrawable().apply {
                    cornerRadius = 16f * resources.displayMetrics.density
                    setColor(colors.forLevel(level))
                }
            }
        }
    }

    private fun confirm(title: Int, message: Int, action: () -> Unit) {
        AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message)
            .setPositiveButton(title) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun returnToSplash() {
        startActivity(Intent(requireContext(), SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}

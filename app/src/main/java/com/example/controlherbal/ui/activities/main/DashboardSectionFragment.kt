package com.example.controlherbal.ui.activities.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.controlherbal.R
import com.example.controlherbal.ControlHerbalApp
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.data.repository.PlantRepositoryImpl
import com.example.controlherbal.data.sync.FirebaseSensorSyncManager
import com.example.controlherbal.data.sync.SensorForegroundService
import com.example.controlherbal.domain.logic.PredictiveTheorem
import com.example.controlherbal.domain.usecase.ExecuteWateringUseCase
import com.example.controlherbal.ui.activities.plant.PlantSetupActivity
import com.example.controlherbal.ui.controller.WateringController
import com.example.controlherbal.ui.helper.ChartManager
import com.example.controlherbal.ui.presenter.SensorDashboardPresenter
import com.example.controlherbal.ui.viewmodel.MainUiState
import com.example.controlherbal.ui.viewmodel.MainViewModel
import com.github.mikephil.charting.charts.CombinedChart
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val viewModel: MainViewModel by viewModels {
        val appContainer = (requireActivity().application as ControlHerbalApp).appContainer
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(appContainer.plantRepository, appContainer.sensorDataRepository) as T
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.activity_main, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val appContainer = (requireActivity().application as ControlHerbalApp).appContainer
        db = appContainer.database
        plantRepository = appContainer.plantRepository as PlantRepositoryImpl
        watering = WateringController(ExecuteWateringUseCase(appContainer.sensorDataRepository))
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

        // Recolección segura de StateFlow usando repeatOnLifecycle (UDF / MVI)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is MainUiState.Loading -> {}
                        is MainUiState.Success -> {
                            plant = state.plant
                            if (plant == null) {
                                startActivity(Intent(requireContext(), PlantSetupActivity::class.java))
                                return@collect
                            }
                            plant?.let { currentPlant ->
                                requireView().findViewById<TextView>(R.id.tvPlantNameAndEmoji).text = currentPlant.name
                                requireView().findViewById<View>(R.id.layoutPlantInfo).isVisible = true
                                currentPlant.aiDiagnosis?.takeIf(String::isNotBlank)?.let { diagnosis ->
                                    requireView().findViewById<View>(R.id.cardAiDiagnosis).isVisible = true
                                    requireView().findViewById<TextView>(R.id.tvAiDiagnosisTitle).text = "Diagnóstico guardado"
                                    requireView().findViewById<TextView>(R.id.tvAiDiagnosisBody).text = diagnosis
                                }
                                state.lastReading?.let { reading ->
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
                        is MainUiState.Error -> {
                            toast(state.message)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
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

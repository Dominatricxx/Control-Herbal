package com.example.controlherbal.ui.activities.main

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.data.repository.PlantRepositoryImpl
import com.example.controlherbal.data.repository.SensorDataRepositoryImpl
import com.example.controlherbal.data.sync.FirebaseSensorSyncManager
import com.example.controlherbal.data.sync.SensorForegroundService
import com.example.controlherbal.domain.usecase.ExecuteWateringUseCase
import com.example.controlherbal.ui.components.SensorDashboardSection
import com.example.controlherbal.ui.controller.WateringController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.controlherbal.ui.components.MainContentScreen
import com.example.controlherbal.ui.theme.ControlHerbalTheme
import com.example.controlherbal.ui.components.AnimatedGradientBackground
import androidx.compose.foundation.layout.fillMaxSize
import com.example.controlherbal.ui.dialogs.PlantSelectionDialog
import com.example.controlherbal.ui.helper.NavigationDrawerHandler
import com.example.controlherbal.ui.presenter.SensorDashboardPresenter
import com.example.controlherbal.ui.theme.ControlHerbalTheme
import com.example.controlherbal.ui.components.AnimatedGradientBackground
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.example.controlherbal.ui.viewmodel.SensorViewModel
import com.github.mikephil.charting.charts.CombinedChart
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity: Orquestador liviano de la pantalla principal (Clean Architecture).
 * Delegación total de UI, sincronización, gráficos y diálogos a componentes SRP dedicados.
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    internal lateinit var drawerLayout: DrawerLayout
    internal lateinit var combinedChart: CombinedChart
    internal lateinit var cbTemp: CheckBox
    internal lateinit var cbHum: CheckBox
    internal lateinit var cbSoil: CheckBox
    internal lateinit var cbLuz: CheckBox
    internal lateinit var cbIRH: CheckBox
    internal lateinit var btnConnect: Button
    internal lateinit var btnWatering: Button
    internal lateinit var cardAiDiagnosis: CardView
    internal lateinit var tvAiDiagnosisTitle: TextView
    internal lateinit var tvAiDiagnosisBody: TextView
    internal lateinit var btnClearAiDiagnosis: Button

    internal val handler = Handler(Looper.getMainLooper())
    internal lateinit var viewModel: SensorViewModel
    internal lateinit var databaseLocal: SensorDatabase
    internal lateinit var herbalAI: HerbalAI
    internal val ioScope = CoroutineScope(Dispatchers.IO)
    internal var lastChartUpdate: Long = 0
    internal var lastReading: SensorReading? = null
    internal var currentPlant: Plant? = null
    internal var lastRecommendationText = ""
    internal var lastAlertState = 0

    private lateinit var dashboardPresenter: SensorDashboardPresenter
    private lateinit var wateringController: WateringController
    private lateinit var syncManager: FirebaseSensorSyncManager
    private lateinit var plantRepository: PlantRepositoryImpl
    private lateinit var sensorDataRepository: SensorDataRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Inicializar Firebase y App Check con Play Integrity
        com.google.firebase.FirebaseApp.initializeApp(this)
        try {
            com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            // Inicializado previamente
        }

        databaseLocal = SensorDatabase.getInstance(this)
        plantRepository = PlantRepositoryImpl(databaseLocal.plantDao())
        sensorDataRepository = SensorDataRepositoryImpl(databaseLocal.sensorDao())
        wateringController = WateringController(ExecuteWateringUseCase(sensorDataRepository))

        setContentView(R.layout.activity_main_drawer)
        initializeUI()

        lifecycleScope.launch {
            val plant = plantRepository.getSelectedPlant()
            val plantCount = plantRepository.getPlantCount()

            if (plant == null) {
                startActivity(Intent(this@MainActivity, PlantSetupActivity::class.java))
                finish()
            } else {
                currentPlant = plant
                if (::sensorViewModel.isInitialized) {
                    sensorViewModel.observeLatestReading(plant.id)
                }
                startSensorService()
                setupPresenterAndSync()
                updateMenuVisibility(plantCount)
            }
        }
    }

    private lateinit var sensorViewModel: SensorViewModel

    private fun initializeUI() {
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        findViewById<View>(R.id.btnMenu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navView.menu.findItem(R.id.nav_main).isVisible = false

        // 1. Renderizar fondo dinámico animado en la capa inferior
        findViewById<ComposeView>(R.id.composeBackgroundView).setContent {
            ControlHerbalTheme {
                AnimatedGradientBackground(modifier = androidx.compose.ui.Modifier.fillMaxSize())
            }
        }

        // 2. Inicializar presentador, gráficas y todos los componentes del sistema completo
        dashboardPresenter = SensorDashboardPresenter(
            context = this,
            tvStatusTitle = findViewById(R.id.tvAlerta),
            tvStatusSubtitle = findViewById(R.id.tvLastUpdate),
            viewStatusIndicator = findViewById(R.id.tvConnectionState),
            tvTemp = findViewById(R.id.tvTemp),
            tvHum = findViewById(R.id.tvHum),
            tvLuz = findViewById(R.id.tvLuz),
            tvSoil = findViewById(R.id.tvSoil),
            tvIrh = findViewById(R.id.tvIRH),
            tvSeq = findViewById(R.id.tvSeq),
            tvSomb = findViewById(R.id.tvSomb)
        )

        combinedChart = findViewById(R.id.combinedChart)
        cbTemp = findViewById(R.id.cbTemp)
        cbHum = findViewById(R.id.cbHum)
        cbSoil = findViewById(R.id.cbSoil)
        cbLuz = findViewById(R.id.cbLuz)
        cbIRH = findViewById(R.id.cbIRH)

        val chartListener = CompoundButton.OnCheckedChangeListener { _, _ -> loadDataAndDrawChart() }
        cbTemp.setOnCheckedChangeListener(chartListener)
        cbHum.setOnCheckedChangeListener(chartListener)
        cbSoil.setOnCheckedChangeListener(chartListener)
        cbLuz.setOnCheckedChangeListener(chartListener)
        cbIRH.setOnCheckedChangeListener(chartListener)

        btnConnect = findViewById(R.id.btnConnect)
        btnWatering = findViewById(R.id.btnWatering)
        btnWatering.setOnClickListener {
            currentPlant?.let { plant ->
                wateringController.showWateringDialog(this, plant, ioScope) {
                    loadDataAndDrawChart()
                }
            }
        }

        findViewById<ImageButton>(R.id.btnAddPlant).setOnClickListener {
            startActivity(Intent(this, PlantSetupActivity::class.java))
        }

        findViewById<Button>(R.id.btnDataControl).setOnClickListener { showDataControlDialog() }
        findViewById<ImageButton>(R.id.btnEditNameIcon).setOnClickListener { showEditNameDialog() }
        findViewById<Button>(R.id.btnDeletePlant).setOnClickListener { showDeletePlantDialog() }

        cardAiDiagnosis = findViewById(R.id.cardAiDiagnosis)
        tvAiDiagnosisTitle = findViewById(R.id.tvAiDiagnosisTitle)
        tvAiDiagnosisBody = findViewById(R.id.tvAiDiagnosisBody)
        btnClearAiDiagnosis = findViewById(R.id.btnClearAiDiagnosis)
        btnClearAiDiagnosis.setOnClickListener { clearAiDiagnosis() }

        btnConnect.setOnClickListener {
            dashboardPresenter.showLinkingState()
            syncManager.startListening()
        }

        findViewById<Button>(R.id.btnBottomPanel).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btnBottomSensors).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        createNotificationChannel()
        requestNotificationPermission()
    }

    private fun setupPresenterAndSync() {
        syncManager = FirebaseSensorSyncManager(
            onReadingReceived = { temp, hum, luz, soil, result ->
                dashboardPresenter.updateMetrics(temp, hum, luz, soil, result.irh, result.seq, result.somb)
                loadDataAndDrawChart()
            },
            onDisconnected = {
                dashboardPresenter.showDisconnectedState()
            }
        )
        syncManager.startListening()
    }

    private fun startSensorService() {
        val intent = Intent(this, SensorForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateMenuVisibility(plantCount: Int) {
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.menu.findItem(R.id.nav_plants).isVisible = plantCount >= 2
        navView.menu.findItem(R.id.nav_comparison).isVisible = plantCount >= 2
    }

    fun showPlantsSelectionDialog() {
        lifecycleScope.launch {
            val plants = plantRepository.getAllPlants()
            PlantSelectionDialog.show(this@MainActivity, plants) { selectedPlant ->
                lifecycleScope.launch {
                    plantRepository.selectPlant(selectedPlant)
                    val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return NavigationDrawerHandler.handleNavigation(this, drawerLayout, item) {
            showPlantsSelectionDialog()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::syncManager.isInitialized) {
            syncManager.stopListening()
        }
    }
}

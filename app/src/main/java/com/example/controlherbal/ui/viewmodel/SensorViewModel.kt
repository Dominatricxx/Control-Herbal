package com.example.controlherbal.ui.viewmodel

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.controlherbal.ai.ModelLoader
import org.tensorflow.lite.Interpreter
import java.util.Calendar

data class SensorUiState(
    val temp: Double = 0.0,
    val hum: Double = 0.0,
    val luz: Double = 0.0,
    val soil: Double = 0.0,
    val analysisResult: PredictiveTheorem.AnalysisResult? = null,
    val isConnected: Boolean = false,
    val isLinking: Boolean = false,
    val isDay: Boolean = true
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SensorDatabase.getInstance(application)
    private val herbalAI = HerbalAI(application)
    private var tflite: Interpreter? = null
    
    private var startTimeWithoutSun: Long = 0
    private var lastDisplayedIrh: Double = -1.0
    private var lastDisplayedSeq: Double = -1.0
    
    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState

    companion object {
        private const val TAG = "SensorViewModel"
    }

    fun setLinking(linking: Boolean) {
        if (linking) {
            _uiState.value = SensorUiState(isLinking = true, isConnected = false)
        } else {
            _uiState.value = _uiState.value.copy(isLinking = false)
        }
    }

    init {
        loadModel()
    }

    fun observeLatestReading(plantId: Int) {
        viewModelScope.launch {
            db.sensorDao().getLatestReadingFlow(plantId).collect { reading ->
                reading?.let {
                    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val isDayByTime = currentHour in 7..19
                    val isDayByLight = it.light > 10.0
                    val estimatedIsDay = isDayByTime || isDayByLight
                    
                    _uiState.value = _uiState.value.copy(
                        temp = it.temperature,
                        hum = it.humidity,
                        luz = it.light, 
                        soil = it.soilMoisture,
                        analysisResult = PredictiveTheorem.AnalysisResult(
                            irh = it.irh,
                            seq = it.seq,
                            somb = it.somb,
                            recommendation = it.action,
                            urgency = "", 
                            wateringRecommended = it.soilMoisture < 20.0 || (it.irh > 80 && it.soilMoisture < 35.0),
                            nextWateringHours = it.seq,
                            adjustedHum = it.humidity,
                            adjustedLuz = it.light,
                            adjustedSoil = it.soilMoisture
                        ),
                        isConnected = true,
                        isLinking = false,
                        isDay = estimatedIsDay
                    )
                }
            }
        }
    }

    private fun loadModel() {
        tflite = ModelLoader.load(getApplication<Application>())
    }

    fun updateFromFirebase(
        temp: Double, 
        hum: Double, 
        luzRaw: Double, 
        soilRaw: Double, 
        currentPlant: Plant?, 
        lastReading: SensorReading?,
        hwIrh: Double = -1.0,
        hwSeq: Double = -1.0,
        hwSomb: Double = -1.0,
        hwAcc: String = "",
        isDay: Boolean = true
    ) {
        performAnalysis(temp, hum, luzRaw, soilRaw, lastReading, currentPlant, hwIrh, hwSeq, hwSomb, hwAcc, isDay)
    }

    private fun performAnalysis(
        temp: Double, 
        humRaw: Double, 
        luzRaw: Double, 
        soilRaw: Double, 
        lastReading: SensorReading?, 
        currentPlant: Plant?,
        hwIrh: Double = -1.0,
        hwSeq: Double = -1.0,
        hwSomb: Double = -1.0,
        hwAcc: String = "",
        isDay: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val tempSafe = SecurityUtils.clampValue(temp, 0.0, 100.0)
            val luzFiltrada = PredictiveTheorem.filtrarSensibilidadLuz(luzRaw)
            val humFiltrada = PredictiveTheorem.filtrarSensibilidadHumedad(humRaw)
            val soilActual = if (soilRaw > 100.0) PredictiveTheorem.filtrarSensibilidadSuelo(soilRaw) else soilRaw
            
            val currentState = _uiState.value
            
            fun stabilize(current: Double, target: Double, threshold: Double): Double {
                if (current <= 0.1) return target
                return if (Math.abs(current - target) > threshold) {
                    current * 0.8 + target * 0.2
                } else target
            }

            val sTemp = stabilize(currentState.temp, tempSafe, 5.0)
            val sHum = stabilize(currentState.hum, humFiltrada, 15.0)
            val sLuz = stabilize(currentState.luz, luzFiltrada, 25.0)
            val sSoil = stabilize(currentState.soil, soilActual, 15.0)

            var deltaTemp = 0.0
            var deltaHum = 0.0
            var deltaLuz = 0.0
            var deltaSoil = 0.0

            lastReading?.let { prev ->
                val dt = (System.currentTimeMillis() - prev.timestamp) / 3600000.0
                if (dt > 0.001) {
                    deltaTemp = (sTemp - prev.temperature) / dt
                    deltaHum = (sHum - prev.humidity) / dt
                    deltaLuz = (sLuz - prev.light) / dt
                    deltaSoil = (sSoil - prev.soilMoisture) / dt
                }
            }

            val lowLuzThreshold = 10.0
            if (sLuz < lowLuzThreshold) {
                if (startTimeWithoutSun == 0L) startTimeWithoutSun = System.currentTimeMillis()
            } else {
                startTimeWithoutSun = 0L
            }

            val hoursWithoutSun = if (startTimeWithoutSun > 0) {
                (System.currentTimeMillis() - startTimeWithoutSun) / 3600000.0
            } else 0.0

            val historySize = currentPlant?.let { db.sensorDao().getCountByPlantId(it.id) } ?: 0

            val aiDehydrationFactor = herbalAI.predictDehydrationFactor(sTemp, sHum, sLuz, sSoil, currentPlant?.type ?: "Híbrido", isDay)

            val result = PredictiveTheorem.analyze(
                sTemp, humRaw, luzRaw, soilRaw,
                deltaTemp, deltaHum, deltaLuz, deltaSoil,
                currentPlant?.lastWateringTime ?: 0,
                currentPlant?.type ?: "",
                hoursWithoutSun,
                isDay,
                aiFactor = aiDehydrationFactor,
                historySize = historySize
            )

            val finalIrh = if (hwIrh > 0) {
                if (lastDisplayedIrh < 0) hwIrh else (hwIrh * 0.3 + lastDisplayedIrh * 0.7)
            } else if (hwIrh == 0.0 && lastDisplayedIrh > 5.0) {
                lastDisplayedIrh * 0.85
            } else {
                val irhAI = herbalAI.predictRefinedIRH(sTemp, sHum, sLuz, sSoil, currentPlant?.type ?: "Híbrido", isDay)
                var combinedIrh = (result.irh + irhAI) / 2.0
                
                try {
                    tflite?.let { interpreter ->
                        val input = arrayOf(ModelLoader.buildInput(sTemp, sHum, sLuz, sSoil, isDay))
                        val output = arrayOf(floatArrayOf(0f))
                        interpreter.run(input, output)
                        ModelLoader.sanitizeIrh(output[0][0])?.let { modelIrh ->
                            combinedIrh = (combinedIrh + modelIrh) / 2.0
                        }
                    }
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Error en inferencia TFLite: ${e.message}")
                }
                
                if (lastDisplayedIrh < 0) combinedIrh else (combinedIrh * 0.3 + lastDisplayedIrh * 0.7)
            }
            
            lastDisplayedIrh = finalIrh

            val rawHwSeq = hwSeq
            val calculatedSeq = if (sSoil > 40.0) {
                result.seq
            } else if (rawHwSeq > 0 && rawHwSeq < 6.0) {
                rawHwSeq
            } else {
                result.seq
            }
            
            val finalSeq = if (lastDisplayedSeq < 0) calculatedSeq else (calculatedSeq * 0.05 + lastDisplayedSeq * 0.95)
            lastDisplayedSeq = finalSeq

            val finalResult = result.copy(
                irh = finalIrh,
                seq = finalSeq,
                somb = if (hwSomb >= 0) hwSomb else result.somb,
                recommendation = if (hwAcc.isNotEmpty()) hwAcc else result.recommendation,
                isDataReliable = result.isDataReliable,
                adjustedHum = sHum,
                adjustedLuz = sLuz,
                adjustedSoil = sSoil
            )
            
            _uiState.value = SensorUiState(
                temp = sTemp,
                hum = sHum, 
                luz = sLuz,
                soil = sSoil,
                analysisResult = finalResult,
                isConnected = true,
                isLinking = false,
                isDay = isDay
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        tflite?.close()
    }
}

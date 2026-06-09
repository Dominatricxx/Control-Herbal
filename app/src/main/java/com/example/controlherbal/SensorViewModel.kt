package com.example.controlherbal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlherbal.ai.HerbalAI
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.logic.PredictiveTheorem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.*

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
    
    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState

    fun setLinking(linking: Boolean) {
        if (linking) {
            // Cuando iniciamos vinculación, limpiamos el estado actual para no mostrar datos residuales
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
                // No actualizamos si estamos vinculando para no mostrar datos viejos
                if (_uiState.value.isLinking) return@collect
                
                reading?.let {
                    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val estimatedIsDay = currentHour in 7..19
                    
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
                            wateringRecommended = it.soilMoisture < 25.0, 
                            nextWateringHours = it.seq,
                            adjustedHum = it.humidity,
                            adjustedLuz = it.light,
                            adjustedSoil = it.soilMoisture
                        ),
                        isConnected = true,
                        isDay = estimatedIsDay
                    )
                }
            }
        }
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = getApplication<Application>().assets.openFd("herbal_model.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            tflite = Interpreter(modelBuffer)
        } catch (e: Exception) {
            // Log error
        }
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
            val luzFiltrada = PredictiveTheorem.filtrarSensibilidadLuz(luzRaw)
            val humFiltrada = PredictiveTheorem.filtrarSensibilidadHumedad(humRaw)
            val soilActual = if (soilRaw > 100.0) PredictiveTheorem.filtrarSensibilidadSuelo(soilRaw) else soilRaw
            
            val currentState = _uiState.value
            
            // FILTROS DE ESTABILIDAD (Noise Reduction)
            // No permitimos cambios bruscos de más del 20% en una sola lectura para sensores físicos
            fun stabilize(current: Double, target: Double, threshold: Double): Double {
                if (current <= 0.1) return target // Inicialización
                return if (Math.abs(current - target) > threshold) {
                    current * 0.8 + target * 0.2 // Suavizado agresivo si hay un pico
                } else target
            }

            val sTemp = stabilize(currentState.temp, temp, 5.0)
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

            // Cálculo de tiempo sin sol
            val lowLuzThreshold = 10.0
            if (sLuz < lowLuzThreshold) {
                if (startTimeWithoutSun == 0L) startTimeWithoutSun = System.currentTimeMillis()
            } else {
                startTimeWithoutSun = 0L
            }

            val hoursWithoutSun = if (startTimeWithoutSun > 0) {
                (System.currentTimeMillis() - startTimeWithoutSun) / 3600000.0
            } else 0.0

            val result = PredictiveTheorem.analyze(
                sTemp, humRaw, luzRaw, soilRaw,
                deltaTemp, deltaHum, deltaLuz, deltaSoil,
                currentPlant?.lastWateringTime ?: 0,
                currentPlant?.type ?: "",
                hoursWithoutSun,
                isDay
            )

            val finalIrh = if (hwIrh > 0) {
                // Si el hardware manda un IRH > 0, lo usamos pero lo suavizamos para evitar parpadeos
                if (lastDisplayedIrh < 0) hwIrh else (hwIrh * 0.3 + lastDisplayedIrh * 0.7)
            } else if (hwIrh == 0.0 && lastDisplayedIrh > 5.0) {
                // Si el hardware manda un 0 de repente, lo ignoramos momentáneamente (parpadeo)
                // y bajamos el valor gradualmente
                lastDisplayedIrh * 0.85
            } else {
                // Cálculo de la IA de la App
                val irhAI = herbalAI.predictRefinedIRH(sTemp, sHum, sLuz, sSoil, currentPlant?.type ?: "Híbrido", isDay)
                var combinedIrh = (result.irh + irhAI) / 2.0
                
                try {
                    tflite?.let { interpreter ->
                        val input = arrayOf(floatArrayOf(sTemp.toFloat(), sHum.toFloat(), sLuz.toFloat(), sSoil.toFloat(), if (isDay) 1f else 0f))
                        val output = arrayOf(floatArrayOf(0f))
                        interpreter.run(input, output)
                        combinedIrh = (combinedIrh + output[0][0].toDouble()) / 2.0
                    }
                } catch (e: Exception) {}
                
                // Suavizamos el resultado de la IA también
                if (lastDisplayedIrh < 0) combinedIrh else (combinedIrh * 0.3 + lastDisplayedIrh * 0.7)
            }
            
            lastDisplayedIrh = finalIrh

            val finalResult = result.copy(
                irh = finalIrh,
                seq = if (hwSeq >= 0) hwSeq else result.seq,
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

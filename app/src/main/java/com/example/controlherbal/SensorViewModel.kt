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

data class SensorUiState(
    val temp: Double = 0.0,
    val hum: Double = 0.0,
    val luz: Int = 0,
    val soil: Double = 0.0,
    val analysisResult: PredictiveTheorem.AnalysisResult? = null,
    val isConnected: Boolean = false
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SensorDatabase.getInstance(application)
    private val herbalAI = HerbalAI(application)
    private var tflite: Interpreter? = null
    
    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState

    init {
        loadModel()
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
        luz: Int, 
        soil: Double, 
        currentPlant: Plant?, 
        lastReading: SensorReading?,
        hwIrh: Double = -1.0,
        hwSeq: Double = -1.0,
        hwSomb: Double = -1.0,
        hwAcc: String = ""
    ) {
        performAnalysis(temp, hum, luz, soil, lastReading, currentPlant, hwIrh, hwSeq, hwSomb, hwAcc)
        
        // Save to DB
        currentPlant?.let { plant ->
            viewModelScope.launch(Dispatchers.IO) {
                val reading = SensorReading(
                    timestamp = System.currentTimeMillis(), 
                    plantId = plant.id, 
                    temperature = temp, 
                    humidity = hum, 
                    light = luz, 
                    soilMoisture = soil,
                    irh = _uiState.value.analysisResult?.irh ?: 0.0, 
                    seq = _uiState.value.analysisResult?.seq ?: 0.0, 
                    somb = _uiState.value.analysisResult?.somb ?: 0.0, 
                    action = _uiState.value.analysisResult?.recommendation ?: ""
                )
                db.sensorDao().insert(reading)
                db.sensorDao().pruneData(plant.id)
            }
        }
    }

    private fun performAnalysis(
        temp: Double, 
        hum: Double, 
        luz: Int, 
        soil: Double, 
        lastReading: SensorReading?, 
        currentPlant: Plant?,
        hwIrh: Double = -1.0,
        hwSeq: Double = -1.0,
        hwSomb: Double = -1.0,
        hwAcc: String = ""
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            var deltaTemp = 0.0
            var deltaHum = 0.0
            var deltaLuz = 0.0
            var deltaSoil = 0.0

            lastReading?.let { prev ->
                val dt = (System.currentTimeMillis() - prev.timestamp) / 3600000.0
                if (dt > 0.001) {
                    deltaTemp = (temp - prev.temperature) / dt
                    deltaHum = (hum - prev.humidity) / dt
                    deltaLuz = (luz - prev.light.toDouble()) / dt
                    deltaSoil = (soil - prev.soilMoisture) / dt
                }
            }

            val result = PredictiveTheorem.analyze(
                temp, hum, luz, soil,
                deltaTemp, deltaHum, deltaLuz, deltaSoil,
                currentPlant?.lastWateringTime ?: 0,
                currentPlant?.type ?: ""
            )

            // Priorizar valores de hardware si están disponibles (compatibilidad con Arduino-Herbal-Mini)
            val finalIrh = if (hwIrh >= 0) hwIrh else {
                val irhAI = herbalAI.predictRefinedIRH(temp, hum, luz, soil)
                var combinedIrh = (result.irh + irhAI) / 2.0
                
                try {
                    tflite?.let { interpreter ->
                        val input = arrayOf(floatArrayOf(temp.toFloat(), hum.toFloat(), luz.toFloat()))
                        val output = arrayOf(floatArrayOf(0f))
                        interpreter.run(input, output)
                        combinedIrh = (combinedIrh + output[0][0].toDouble()) / 2.0
                    }
                } catch (e: Exception) {}
                combinedIrh
            }

            val finalResult = result.copy(
                irh = finalIrh,
                seq = if (hwSeq >= 0) hwSeq else result.seq,
                somb = if (hwSomb >= 0) hwSomb else result.somb,
                recommendation = if (hwAcc.isNotEmpty()) hwAcc else result.recommendation
            )
            
            _uiState.value = SensorUiState(
                temp = temp,
                hum = hum,
                luz = luz,
                soil = soil,
                analysisResult = finalResult,
                isConnected = true
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        tflite?.close()
    }
}

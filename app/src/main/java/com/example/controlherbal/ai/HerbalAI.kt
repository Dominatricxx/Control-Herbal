package com.example.controlherbal.ai

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

import android.content.Context
import android.content.SharedPreferences
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import kotlin.math.exp
import kotlin.random.Random

/**
 * HerbalAI: Red Neuronal Multicapa (MLP) basada en fisiología vegetal.
 * Entradas: Temp, HumAmb, Luz, Soil, isDay, VPD -> Neuronas Ocultas -> Salida (IRH, DehydrationFactor).
 */
class HerbalAI(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(AppConstants.PREFS_WEIGHTS_NAME, Context.MODE_PRIVATE)
    
    private val inputSize = 6
    private val hiddenSize = 18
    private val outputSize = 2
    
    private var weightsInputHidden = Array(inputSize) { FloatArray(hiddenSize) }
    private var weightsHiddenOutput = Array(hiddenSize) { FloatArray(outputSize) }
    private var biasHidden = FloatArray(hiddenSize)
    private var biasOutput = FloatArray(outputSize)

    private val learningRate = 0.05f

    companion object {
        private const val TAG = "HerbalAI_MLP"
    }

    init {
        loadWeights()
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x.toDouble()).toFloat())
    private fun sigmoidDeriv(x: Float): Float = x * (1f - x)

    private fun calculateVPD(temp: Double, hum: Double): Double {
        val es = 0.6108 * exp(17.27 * temp / (temp + 237.3))
        val ea = es * (hum / 100.0)
        return (es - ea).coerceIn(0.0, 5.0)
    }

    fun predictRefinedIRH(temp: Double, hum: Double, luz: Double, soil: Double, plantType: String = "Híbrido", isDay: Boolean = true): Double {
        val input = prepareInput(temp, hum, luz, soil, plantType, isDay)
        return forward(input)[0] * 100.0
    }

    fun predictDehydrationFactor(temp: Double, hum: Double, luz: Double, soil: Double, plantType: String = "Híbrido", isDay: Boolean = true): Double {
        val input = prepareInput(temp, hum, luz, soil, plantType, isDay)
        val outputs = forward(input)
        
        val learnedFactor = if (outputs[1] < 0.5f) {
            0.2f + (outputs[1] / 0.5f) * 0.8f
        } else {
            1.0f + ((outputs[1] - 0.5f) / 0.5f) * 4.0f
        }
        
        val vpd = calculateVPD(temp, hum)
        val vpdFactor = (vpd / 1.0).coerceIn(0.5, 2.0)
        val dayNightFactor = if (isDay) 1.0 else 0.3
        
        return learnedFactor * vpdFactor * dayNightFactor
    }

    private fun prepareInput(temp: Double, hum: Double, luz: Double, soil: Double, plantType: String, isDay: Boolean): FloatArray {
        val r = when {
            plantType.contains("Luz", ignoreCase = true) || plantType.contains("Sol", ignoreCase = true) -> Triple(60.0, 100.0, 15.0) 
            plantType.contains("Sombra", ignoreCase = true) -> Triple(10.0, 40.0, 60.0)
            else -> Triple(30.0, 70.0, 40.0)
        }

        val vpd = calculateVPD(temp, hum)
        val normalizedVPD = (vpd / 2.5).coerceIn(0.0, 1.0)

        return floatArrayOf(
            PredictiveTheorem.estresTemp(temp, 18.0, 32.0).toFloat() / 100f,
            PredictiveTheorem.estresVariable(hum, 40.0, 70.0).toFloat() / 100f,
            PredictiveTheorem.estresLuz(luz, r.first).toFloat() / 100f,
            PredictiveTheorem.estresVariable(soil, r.third, 85.0).toFloat() / 100f,
            if (isDay) 1.0f else 0.0f,
            normalizedVPD.toFloat()
        )
    }

    private fun forward(input: FloatArray): FloatArray {
        val hiddenLayer = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var activation = biasHidden[j]
            for (i in 0 until inputSize) {
                activation += input[i] * weightsInputHidden[i][j]
            }
            hiddenLayer[j] = sigmoid(activation)
        }

        val outputs = FloatArray(outputSize)
        for (k in 0 until outputSize) {
            var outputActivation = biasOutput[k]
            for (j in 0 until hiddenSize) {
                outputActivation += hiddenLayer[j] * weightsHiddenOutput[j][k]
            }
            outputs[k] = sigmoid(outputActivation)
        }
        
        return outputs
    }

    fun performSelfLearning(history: List<SensorReading>) {
        if (history.size < 20) return

        SecureLogger.d(TAG, "Aprendizaje IA ejecutado con ${history.size} registros.")

        repeat(200) {
            for (idx in 1 until history.size) {
                val current = history[idx]
                val prev = history[idx-1]
                
                val dt = (current.timestamp - prev.timestamp) / 3600000.0
                val realDelta = if (dt > 0.1) (prev.soilMoisture - current.soilMoisture) / dt else 0.0
                
                val targetIrh = current.irh.toFloat() / 100f
                val theoryTasa = PredictiveTheorem.calcularTasaDeshidratacion(current.temperature, current.humidity, current.light, "Híbrido")
                val ratio = (realDelta / (theoryTasa.coerceAtLeast(0.01))).coerceIn(0.1, 5.0)
                val targetFactor = if (ratio <= 1.0) (ratio / 1.0 * 0.5).toFloat() 
                                  else (0.5 + (ratio - 1.0) / 4.0 * 0.5).toFloat()

                val isDayLocal = current.light > 15.0
                val vpd = calculateVPD(current.temperature, current.humidity)
                val normalizedVPD = (vpd / 2.5).coerceIn(0.0, 1.0)
                
                val input = floatArrayOf(
                    PredictiveTheorem.estresTemp(current.temperature, 18.0, 32.0).toFloat() / 100f,
                    PredictiveTheorem.estresVariable(current.humidity, 40.0, 70.0).toFloat() / 100f,
                    PredictiveTheorem.estresLuz(current.light, 30.0).toFloat() / 100f,
                    PredictiveTheorem.estresVariable(current.soilMoisture, 25.0, 70.0).toFloat() / 100f,
                    if (isDayLocal) 1.0f else 0.0f,
                    normalizedVPD.toFloat()
                )
                
                val targets = floatArrayOf(targetIrh, targetFactor)

                val hiddenLayer = FloatArray(hiddenSize)
                for (j in 0 until hiddenSize) {
                    var act = biasHidden[j]
                    for (i in 0 until inputSize) act += input[i] * weightsInputHidden[i][j]
                    hiddenLayer[j] = sigmoid(act)
                }

                val outputs = FloatArray(outputSize)
                for (k in 0 until outputSize) {
                    var outAct = biasOutput[k]
                    for (j in 0 until hiddenSize) outAct += hiddenLayer[j] * weightsHiddenOutput[j][k]
                    outputs[k] = sigmoid(outAct)
                }

                val outputDeltas = FloatArray(outputSize)
                for (k in 0 until outputSize) {
                    val error = targets[k] - outputs[k]
                    outputDeltas[k] = error * sigmoidDeriv(outputs[k])
                }

                val hiddenDeltas = FloatArray(hiddenSize)
                for (j in 0 until hiddenSize) {
                    var hiddenError = 0f
                    for (k in 0 until outputSize) {
                        hiddenError += outputDeltas[k] * weightsHiddenOutput[j][k]
                    }
                    hiddenDeltas[j] = hiddenError * sigmoidDeriv(hiddenLayer[j])
                }

                for (k in 0 until outputSize) {
                    biasOutput[k] += learningRate * outputDeltas[k]
                    for (j in 0 until hiddenSize) {
                        weightsHiddenOutput[j][k] += learningRate * outputDeltas[k] * hiddenLayer[j]
                    }
                }
                for (j in 0 until hiddenSize) {
                    biasHidden[j] += learningRate * hiddenDeltas[j]
                    for (i in 0 until inputSize) {
                        weightsInputHidden[i][j] += learningRate * hiddenDeltas[j] * input[i]
                    }
                }
            }
        }

        saveWeights()
    }

    private fun loadWeights() {
        if (!prefs.contains("initialized")) {
            for (i in 0 until inputSize) {
                for (j in 0 until hiddenSize) weightsInputHidden[i][j] = Random.nextFloat() * 2f - 1f
            }
            for (j in 0 until hiddenSize) {
                for (k in 0 until outputSize) weightsHiddenOutput[j][k] = Random.nextFloat() * 2f - 1f
                biasHidden[j] = 0f
            }
            for (k in 0 until outputSize) biasOutput[k] = 0f
            return
        }

        for (i in 0 until inputSize) {
            for (j in 0 until hiddenSize) {
                weightsInputHidden[i][j] = prefs.getFloat("w_ih_${i}_$j", 0f)
            }
        }
        for (j in 0 until hiddenSize) {
            for (k in 0 until outputSize) {
                weightsHiddenOutput[j][k] = prefs.getFloat("w_ho_${j}_$k", 0f)
            }
            biasHidden[j] = prefs.getFloat("b_h_$j", 0f)
        }
        for (k in 0 until outputSize) {
            biasOutput[k] = prefs.getFloat("b_o_$k", 0f)
        }
    }

    private fun saveWeights() {
        prefs.edit().apply {
            putBoolean("initialized", true)
            for (i in 0 until inputSize) {
                for (j in 0 until hiddenSize) putFloat("w_ih_${i}_$j", weightsInputHidden[i][j])
            }
            for (j in 0 until hiddenSize) {
                for (k in 0 until outputSize) putFloat("w_ho_${j}_$k", weightsHiddenOutput[j][k])
                putFloat("b_h_$j", biasHidden[j])
            }
            for (k in 0 until outputSize) putFloat("b_o_$k", biasOutput[k])
            apply()
        }
    }
}

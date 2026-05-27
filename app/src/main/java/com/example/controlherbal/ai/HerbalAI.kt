package com.example.controlherbal.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.logic.PredictiveTheorem
import kotlin.math.exp
import kotlin.random.Random

/**
 * HerbalAI: Implementación de una Red Neuronal Multicapa (MLP).
 * Arquitectura Actualizada: 4 Entradas (Temp, HumAmb, Luz, Soil) -> 12 Neuronas Ocultas -> 1 Salida (IRH).
 */
class HerbalAI(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("herbal_ai_mlp_weights_v3", Context.MODE_PRIVATE)
    
    private val inputSize = 4
    private val hiddenSize = 12
    private val outputSize = 1
    
    private var weightsInputHidden = Array(inputSize) { FloatArray(hiddenSize) }
    private var weightsHiddenOutput = FloatArray(hiddenSize)
    private var biasHidden = FloatArray(hiddenSize)
    private var biasOutput = 0f

    private val learningRate = 0.05f
    private val TAG = "HerbalAI_MLP"

    init {
        loadWeights()
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x.toDouble()).toFloat())
    private fun sigmoidDeriv(x: Float): Float = x * (1f - x)

    /**
     * Inferencia (Feed-forward): Calcula el IRH basado en los 4 parámetros.
     */
    fun predictRefinedIRH(temp: Double, hum: Double, luz: Int, soil: Double): Double {
        val input = floatArrayOf(
            PredictiveTheorem.calcularEstresVariable(temp, 20.0, 30.0).toFloat() / 100f,
            PredictiveTheorem.calcularEstresVariable(hum, 40.0, 70.0).toFloat() / 100f,
            PredictiveTheorem.calcularEstresVariable(luz.toDouble(), 30.0, 80.0).toFloat() / 100f,
            PredictiveTheorem.calcularEstresVariable(soil, 30.0, 70.0).toFloat() / 100f
        )

        // Capa Oculta
        val hiddenLayer = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var activation = biasHidden[j]
            for (i in 0 until inputSize) {
                activation += input[i] * weightsInputHidden[i][j]
            }
            hiddenLayer[j] = sigmoid(activation)
        }

        // Capa de Salida
        var outputActivation = biasOutput
        for (j in 0 until hiddenSize) {
            outputActivation += hiddenLayer[j] * weightsHiddenOutput[j]
        }
        
        val irhResult = sigmoid(outputActivation) * 100f
        return irhResult.toDouble().coerceIn(0.0, 100.0)
    }

    fun performSelfLearning(history: List<SensorReading>) {
        if (history.size < 15) return

        Log.d(TAG, "Entrenando Red Neuronal con ${history.size} registros y 4 parámetros...")

        repeat(100) {
            for (reading in history) {
                val input = floatArrayOf(
                    PredictiveTheorem.calcularEstresVariable(reading.temperature, 20.0, 30.0).toFloat() / 100f,
                    PredictiveTheorem.calcularEstresVariable(reading.humidity, 40.0, 70.0).toFloat() / 100f,
                    PredictiveTheorem.calcularEstresVariable(reading.light.toDouble(), 30.0, 80.0).toFloat() / 100f,
                    PredictiveTheorem.calcularEstresVariable(reading.soilMoisture, 30.0, 70.0).toFloat() / 100f
                )
                val target = reading.irh.toFloat() / 100f

                // Feed-forward
                val hiddenLayer = FloatArray(hiddenSize)
                for (j in 0 until hiddenSize) {
                    var act = biasHidden[j]
                    for (i in 0 until inputSize) act += input[i] * weightsInputHidden[i][j]
                    hiddenLayer[j] = sigmoid(act)
                }

                var outputAct = biasOutput
                for (j in 0 until hiddenSize) outputAct += hiddenLayer[j] * weightsHiddenOutput[j]
                val output = sigmoid(outputAct)

                // Backpropagation
                val outputError = target - output
                val outputDelta = outputError * sigmoidDeriv(output)

                val hiddenDeltas = FloatArray(hiddenSize)
                for (j in 0 until hiddenSize) {
                    val hiddenError = outputDelta * weightsHiddenOutput[j]
                    hiddenDeltas[j] = hiddenError * sigmoidDeriv(hiddenLayer[j])
                }

                // Update Weights
                for (j in 0 until hiddenSize) {
                    weightsHiddenOutput[j] += learningRate * outputDelta * hiddenLayer[j]
                    biasOutput += learningRate * outputDelta
                    for (i in 0 until inputSize) {
                        weightsInputHidden[i][j] += learningRate * hiddenDeltas[j] * input[i]
                        biasHidden[j] += learningRate * hiddenDeltas[j]
                    }
                }
            }
        }

        saveWeights()
        Log.d(TAG, "Aprendizaje 4-parámetros completado.")
    }

    private fun loadWeights() {
        if (!prefs.contains("initialized")) {
            for (i in 0 until inputSize) {
                for (j in 0 until hiddenSize) weightsInputHidden[i][j] = Random.nextFloat() * 2f - 1f
            }
            for (j in 0 until hiddenSize) {
                weightsHiddenOutput[j] = Random.nextFloat() * 2f - 1f
                biasHidden[j] = 0f
            }
            biasOutput = 0f
            return
        }

        for (i in 0 until inputSize) {
            for (j in 0 until hiddenSize) {
                weightsInputHidden[i][j] = prefs.getFloat("w_ih_${i}_$j", 0f)
            }
        }
        for (j in 0 until hiddenSize) {
            weightsHiddenOutput[j] = prefs.getFloat("w_ho_$j", 0f)
            biasHidden[j] = prefs.getFloat("b_h_$j", 0f)
        }
        biasOutput = prefs.getFloat("b_o", 0f)
    }

    private fun saveWeights() {
        prefs.edit().apply {
            putBoolean("initialized", true)
            for (i in 0 until inputSize) {
                for (j in 0 until hiddenSize) putFloat("w_ih_${i}_$j", weightsInputHidden[i][j])
            }
            for (j in 0 until hiddenSize) {
                putFloat("w_ho_$j", weightsHiddenOutput[j])
                putFloat("b_h_$j", biasHidden[j])
            }
            putFloat("b_o", biasOutput)
            apply()
        }
    }
}

package com.example.controlherbal.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.logic.PredictiveTheorem
import kotlin.math.exp
import kotlin.random.Random

/**
 * HerbalAI: Implementación de una Red Neuronal Multicapa (MLP) mejorada.
 * Arquitectura: 3 Entradas (Temp, Hum, Luz) -> 12 Neuronas Ocultas -> 1 Salida (IRH).
 * Utiliza Funciones de Activación Sigmoide y Retropropagación (Backpropagation)
 * para aprendizaje no lineal profundo optimizado para herbolaria.
 */
class HerbalAI(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("herbal_ai_mlp_weights_v2", Context.MODE_PRIVATE)
    
    // Arquitectura expandida para mayor precisión: [3 -> 12 -> 1]
    private val inputSize = 3
    private val hiddenSize = 12
    private val outputSize = 1
    
    // Pesos y Sesgos (Weights and Biases)
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
     * Inferencia (Feed-forward): Calcula el IRH basado en la red neuronal.
     */
    fun predictRefinedIRH(temp: Double, hum: Double, luz: Int): Double {
        val input = floatArrayOf(
            PredictiveTheorem.calcularEstresTemperatura(temp).toFloat() / 100f,
            PredictiveTheorem.calcularEstresHumedad(hum).toFloat() / 100f,
            PredictiveTheorem.calcularEstresLuz(luz).toFloat() / 100f
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

    /**
     * Entrenamiento (Backpropagation): Aprende de los datos históricos.
     */
    fun performSelfLearning(history: List<SensorReading>) {
        if (history.size < 15) return

        Log.d(TAG, "Entrenando Red Neuronal MLP con ${history.size} registros...")

        repeat(100) { // Épocas de entrenamiento
            for (reading in history) {
                // Preprocesamiento (Normalización 0-1)
                val input = floatArrayOf(
                    PredictiveTheorem.calcularEstresTemperatura(reading.temperature).toFloat() / 100f,
                    PredictiveTheorem.calcularEstresHumedad(reading.humidity).toFloat() / 100f,
                    PredictiveTheorem.calcularEstresLuz(reading.light).toFloat() / 100f
                )
                val target = reading.irh.toFloat() / 100f

                // --- 1. Feed-forward ---
                val hiddenLayer = FloatArray(hiddenSize)
                for (j in 0 until hiddenSize) {
                    var act = biasHidden[j]
                    for (i in 0 until inputSize) act += input[i] * weightsInputHidden[i][j]
                    hiddenLayer[j] = sigmoid(act)
                }

                var outputAct = biasOutput
                for (j in 0 until hiddenSize) outputAct += hiddenLayer[j] * weightsHiddenOutput[j]
                val output = sigmoid(outputAct)

                // --- 2. Backpropagation ---
                val outputError = target - output
                val outputDelta = outputError * sigmoidDeriv(output)

                val hiddenDeltas = FloatArray(hiddenSize)
                for (j in 0 until hiddenSize) {
                    val hiddenError = outputDelta * weightsHiddenOutput[j]
                    hiddenDeltas[j] = hiddenError * sigmoidDeriv(hiddenLayer[j])
                }

                // --- 3. Actualización de Pesos ---
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
        Log.d(TAG, "Ciclo de Deep Learning completado. Pesos actualizados.")
    }

    private fun loadWeights() {
        if (!prefs.contains("initialized")) {
            // Inicialización Xavier/Glorot aleatoria
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

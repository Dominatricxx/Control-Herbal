package com.example.controlherbal.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.logic.PredictiveTheorem
import kotlin.math.exp
import kotlin.random.Random

/**
 * HerbalAI: Red Neuronal Multicapa (MLP) Avanzada.
 * Basada en investigaciones de fisiología vegetal (Vapor Pressure Deficit - VPD).
 * 6 Entradas (Temp, HumAmb, Luz, Soil, isDay, VPD) -> 18 Neuronas Ocultas -> 1 Salida (IRH).
 */
class HerbalAI(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("herbal_ai_mlp_weights_v6", Context.MODE_PRIVATE)
    
    private val inputSize = 6
    private val hiddenSize = 18
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
     * Calcula el Déficit de Presión de Vapor (VPD).
     * El VPD es el indicador académico más preciso para el estrés hídrico.
     */
    private fun calculateVPD(temp: Double, hum: Double): Double {
        val es = 0.6108 * exp(17.27 * temp / (temp + 237.3))
        val ea = es * (hum / 100.0)
        return (es - ea).coerceIn(0.0, 5.0) // kPa
    }

    fun predictRefinedIRH(temp: Double, hum: Double, luz: Double, soil: Double, plantType: String = "Híbrido", isDay: Boolean = true): Double {
        val r = when {
            plantType.contains("Luz", ignoreCase = true) || plantType.contains("Sol", ignoreCase = true) -> Triple(60.0, 100.0, 15.0) 
            plantType.contains("Sombra", ignoreCase = true) -> Triple(10.0, 40.0, 60.0)
            else -> Triple(30.0, 70.0, 40.0)
        }

        val vpd = calculateVPD(temp, hum)
        val normalizedVPD = (vpd / 2.5).coerceIn(0.0, 1.0) // 1.2-1.5 kPa es el límite de estrés para muchas plantas

        val input = floatArrayOf(
            PredictiveTheorem.estresTemp(temp, 18.0, 32.0).toFloat() / 100f,
            PredictiveTheorem.estresVariable(hum, 40.0, 70.0).toFloat() / 100f,
            PredictiveTheorem.estresLuz(luz, r.first).toFloat() / 100f,
            PredictiveTheorem.estresVariable(soil, r.third, 85.0).toFloat() / 100f,
            if (isDay) 1.0f else 0.0f,
            normalizedVPD.toFloat()
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
        if (history.size < 20) return

        Log.d(TAG, "Aprendizaje IA (VPD + Circadiano) con ${history.size} registros...")

        repeat(150) {
            for (reading in history) {
                val isDayLocal = reading.light > 15.0
                val vpd = calculateVPD(reading.temperature, reading.humidity)
                val normalizedVPD = (vpd / 2.5).coerceIn(0.0, 1.0)
                
                val input = floatArrayOf(
                    PredictiveTheorem.estresTemp(reading.temperature, 18.0, 32.0).toFloat() / 100f,
                    PredictiveTheorem.estresVariable(reading.humidity, 40.0, 70.0).toFloat() / 100f,
                    PredictiveTheorem.estresLuz(reading.light, 30.0).toFloat() / 100f,
                    PredictiveTheorem.estresVariable(reading.soilMoisture, 25.0, 70.0).toFloat() / 100f,
                    if (isDayLocal) 1.0f else 0.0f,
                    normalizedVPD.toFloat()
                )
                val target = reading.irh.toFloat() / 100f
                // ... resto del entrenamiento ...

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
        Log.d(TAG, "Aprendizaje sincronizado completado.")
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

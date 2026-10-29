package com.example.controlherbal.ai

import android.content.Context
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import kotlin.math.exp
import kotlin.random.Random

/**
 * Interfaz pura de Kotlin para abstraer la persistencia de pesos neuronales de HerbalAI,
 * eliminando acoplamientos con SharedPreferences o Context de Android en el dominio de IA.
 */
interface WeightDataSource {
    fun isInitialized(): Boolean
    fun getWeight(key: String, defaultValue: Float): Float
    fun saveWeights(
        weightsInputHidden: FloatArray,
        weightsHiddenOutput: FloatArray,
        biasHidden: FloatArray,
        biasOutput: FloatArray
    )
}

/**
 * Implementación concreta de WeightDataSource basada en SharedPreferences de Android.
 */
class SharedPreferencesWeightDataSource(context: Context) : WeightDataSource {
    private val prefs = context.getSharedPreferences(AppConstants.PREFS_WEIGHTS_NAME, Context.MODE_PRIVATE)

    override fun isInitialized(): Boolean = prefs.contains("initialized")

    override fun getWeight(key: String, defaultValue: Float): Float = prefs.getFloat(key, defaultValue)

    override fun saveWeights(
        weightsInputHidden: FloatArray,
        weightsHiddenOutput: FloatArray,
        biasHidden: FloatArray,
        biasOutput: FloatArray
    ) {
        prefs.edit().apply {
            putBoolean("initialized", true)
            for (idx in weightsInputHidden.indices) {
                putFloat("w_ih_$idx", weightsInputHidden[idx])
            }
            for (idx in weightsHiddenOutput.indices) {
                putFloat("w_ho_$idx", weightsHiddenOutput[idx])
            }
            for (idx in biasHidden.indices) {
                putFloat("b_h_$idx", biasHidden[idx])
            }
            for (idx in biasOutput.indices) {
                putFloat("b_o_$idx", biasOutput[idx])
            }
            apply()
        }
    }
}

/**
 * HerbalAI: Red Neuronal Multicapa (MLP) basada en fisiología vegetal.
 * Optimizado con arreglos planos unidimensionales (FloatArray) y indexación matemática row*cols+col
 * para garantizar localidad de caché L1/L2 y eliminación de GC Thrashing.
 */
class HerbalAI(private val weightDataSource: WeightDataSource) {

    constructor(context: Context) : this(SharedPreferencesWeightDataSource(context))

    private val inputSize = 6
    private val hiddenSize = 18
    private val outputSize = 2
    
    // Arreglos planos unidimensionales (Flat Buffers) para localidad de caché y cero GC Thrashing
    private val weightsInputHidden = FloatArray(inputSize * hiddenSize)
    private val weightsHiddenOutput = FloatArray(hiddenSize * outputSize)
    private val biasHidden = FloatArray(hiddenSize)
    private val biasOutput = FloatArray(outputSize)

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
                activation += input[i] * weightsInputHidden[i * hiddenSize + j]
            }
            hiddenLayer[j] = sigmoid(activation)
        }

        val outputs = FloatArray(outputSize)
        for (k in 0 until outputSize) {
            var outputActivation = biasOutput[k]
            for (j in 0 until hiddenSize) {
                outputActivation += hiddenLayer[j] * weightsHiddenOutput[j * outputSize + k]
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
                    for (i in 0 until inputSize) act += input[i] * weightsInputHidden[i * hiddenSize + j]
                    hiddenLayer[j] = sigmoid(act)
                }

                val outputs = FloatArray(outputSize)
                for (k in 0 until outputSize) {
                    var outAct = biasOutput[k]
                    for (j in 0 until hiddenSize) outAct += hiddenLayer[j] * weightsHiddenOutput[j * outputSize + k]
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
                        hiddenError += outputDeltas[k] * weightsHiddenOutput[j * outputSize + k]
                    }
                    hiddenDeltas[j] = hiddenError * sigmoidDeriv(hiddenLayer[j])
                }

                for (k in 0 until outputSize) {
                    biasOutput[k] += learningRate * outputDeltas[k]
                    for (j in 0 until hiddenSize) {
                        weightsHiddenOutput[j * outputSize + k] += learningRate * outputDeltas[k] * hiddenLayer[j]
                    }
                }
                for (j in 0 until hiddenSize) {
                    biasHidden[j] += learningRate * hiddenDeltas[j]
                    for (i in 0 until inputSize) {
                        weightsInputHidden[i * hiddenSize + j] += learningRate * hiddenDeltas[j] * input[i]
                    }
                }
            }
        }

        saveWeights()
    }

    private fun loadWeights() {
        if (!weightDataSource.isInitialized()) {
            for (idx in weightsInputHidden.indices) weightsInputHidden[idx] = Random.nextFloat() * 2f - 1f
            for (idx in weightsHiddenOutput.indices) weightsHiddenOutput[idx] = Random.nextFloat() * 2f - 1f
            for (idx in biasHidden.indices) biasHidden[idx] = 0f
            for (idx in biasOutput.indices) biasOutput[idx] = 0f
            return
        }

        for (idx in weightsInputHidden.indices) {
            weightsInputHidden[idx] = weightDataSource.getWeight("w_ih_$idx", 0f)
        }
        for (idx in weightsHiddenOutput.indices) {
            weightsHiddenOutput[idx] = weightDataSource.getWeight("w_ho_$idx", 0f)
        }
        for (idx in biasHidden.indices) {
            biasHidden[idx] = weightDataSource.getWeight("b_h_$idx", 0f)
        }
        for (idx in biasOutput.indices) {
            biasOutput[idx] = weightDataSource.getWeight("b_o_$idx", 0f)
        }
    }

    private fun saveWeights() {
        weightDataSource.saveWeights(weightsInputHidden, weightsHiddenOutput, biasHidden, biasOutput)
    }
}

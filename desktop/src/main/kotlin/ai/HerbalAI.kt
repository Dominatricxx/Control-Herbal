package ai

import logic.PredictiveTheorem
import java.io.File
import java.util.Properties
import kotlin.math.exp
import kotlin.random.Random

/**
 * HerbalAI: Versión Desktop con persistencia y 4 parámetros de entrada.
 */
class HerbalAI {

    private val weightsFile = File(System.getProperty("user.home"), ".herbal_ai_weights_v2.properties")
    
    private val inputSize = 4
    private val hiddenSize = 12
    private val outputSize = 1
    
    private var weightsInputHidden = Array(inputSize) { FloatArray(hiddenSize) }
    private var weightsHiddenOutput = FloatArray(hiddenSize)
    private var biasHidden = FloatArray(hiddenSize)
    private var biasOutput = 0f

    init {
        loadWeights()
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x.toDouble()).toFloat())

    fun predictRefinedIRH(temp: Double, hum: Double, luz: Int, soil: Double): Double {
        val input = floatArrayOf(
            PredictiveTheorem.calcularEstresVariable(temp, 20.0, 30.0).toFloat() / 100f,
            PredictiveTheorem.calcularEstresVariable(hum, 40.0, 70.0).toFloat() / 100f,
            PredictiveTheorem.calcularEstresVariable(luz.toDouble(), 30.0, 80.0).toFloat() / 100f,
            PredictiveTheorem.calcularEstresVariable(soil, 30.0, 70.0).toFloat() / 100f
        )

        val hiddenLayer = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var activation = biasHidden[j]
            for (i in 0 until inputSize) {
                activation += input[i] * weightsInputHidden[i][j]
            }
            hiddenLayer[j] = sigmoid(activation)
        }

        var outputActivation = biasOutput
        for (j in 0 until hiddenSize) {
            outputActivation += hiddenLayer[j] * weightsHiddenOutput[j]
        }
        
        val irhResult = sigmoid(outputActivation) * 100f
        return irhResult.toDouble().coerceIn(0.0, 100.0)
    }

    private fun loadWeights() {
        val props = Properties()
        if (!weightsFile.exists()) {
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

        try {
            weightsFile.inputStream().use { props.load(it) }
            for (i in 0 until inputSize) {
                for (j in 0 until hiddenSize) {
                    weightsInputHidden[i][j] = props.getProperty("w_ih_${i}_$j", "0").toFloat()
                }
            }
            for (j in 0 until hiddenSize) {
                weightsHiddenOutput[j] = props.getProperty("w_ho_$j", "0").toFloat()
                biasHidden[j] = props.getProperty("b_h_$j", "0").toFloat()
            }
            biasOutput = props.getProperty("b_o", "0").toFloat()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

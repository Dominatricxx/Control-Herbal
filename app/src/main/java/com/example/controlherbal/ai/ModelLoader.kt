package com.example.controlherbal.ai

import android.content.Context
import com.example.controlherbal.common.AppConstants
import com.example.controlherbal.common.SecureLogger
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel

/**
 * ModelLoader: carga el modelo TFLite embebido aplicando comprobaciones defensivas.
 *
 * La integridad del archivo en el APK la garantiza la firma del APK; en tiempo de compilación,
 * la tarea Gradle `verifyModelIntegrity` exige que el .tflite coincida con su huella SHA-256
 * versionada. Aquí se limita el tamaño y se valida la FORMA de los tensores, de modo que un
 * modelo incompatible o manipulado se descarta en lugar de producir resultados arbitrarios.
 */
object ModelLoader {

    private const val TAG = "ModelLoader"
    private const val MAX_MODEL_BYTES = 1L * 1024 * 1024

    const val EXPECTED_INPUT_FEATURES = 5
    const val EXPECTED_OUTPUTS = 1

    fun load(context: Context): Interpreter? {
        return try {
            context.assets.openFd(AppConstants.TFLITE_MODEL_ASSET).use { afd ->
                if (afd.declaredLength <= 0 || afd.declaredLength > MAX_MODEL_BYTES) {
                    SecureLogger.w(TAG, "Modelo TFLite descartado: tamaño fuera de rango")
                    return null
                }
                FileInputStream(afd.fileDescriptor).use { input ->
                    val buffer = input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                    val interpreter = Interpreter(buffer)
                    if (!hasExpectedShape(interpreter)) {
                        interpreter.close()
                        SecureLogger.w(TAG, "Modelo TFLite descartado: forma de tensores inesperada")
                        return null
                    }
                    interpreter
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "No se pudo cargar el modelo TFLite: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun hasExpectedShape(interpreter: Interpreter): Boolean {
        if (interpreter.inputTensorCount != 1 || interpreter.outputTensorCount != 1) return false
        val inShape = interpreter.getInputTensor(0).shape()
        val outShape = interpreter.getOutputTensor(0).shape()
        return inShape.size == 2 && inShape[0] == 1 && inShape[1] == EXPECTED_INPUT_FEATURES &&
            outShape.size == 2 && outShape[0] == 1 && outShape[1] == EXPECTED_OUTPUTS
    }

    /**
     * Normaliza por rangos físicos FIJOS (los mismos que usa ml/train_herbal_model.py):
     * temp -40..85, hum/luz/soil 0..100, y la bandera día/noche. Entradas fuera de rango se acotan.
     */
    fun buildInput(temp: Double, hum: Double, luz: Double, soil: Double, isDay: Boolean): FloatArray {
        fun norm(v: Double, lo: Double, hi: Double): Float =
            (((if (v.isNaN()) lo else v) - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
        return floatArrayOf(
            norm(temp, -40.0, 85.0), norm(hum, 0.0, 100.0), norm(luz, 0.0, 100.0), norm(soil, 0.0, 100.0),
            if (isDay) 1f else 0f
        )
    }

    /** Valida la salida del modelo: descarta NaN/infinitos y la acota a la escala del IRH (0..100). */
    fun sanitizeIrh(raw: Float): Double? {
        if (raw.isNaN() || raw.isInfinite()) return null
        return raw.toDouble().coerceIn(0.0, 100.0)
    }
}

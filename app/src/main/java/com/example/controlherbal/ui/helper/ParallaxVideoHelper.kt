package com.example.controlherbal.ui.helper

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View

/**
 * Helper para aplicar efecto Parallax reactivo a los sensores de movimiento
 * con Low-Pass Filter y Bounding Box estricto para evitar bordes negros.
 */
class ParallaxVideoHelper(
    private val sensorManager: SensorManager,
    private val targetView: View,
    private val scaleFactor: Float = 1.15f // Video escalado al 115% para permitir movimiento de paralaje sin bordes negros
) : SensorEventListener {

    private var rotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val orientation = FloatArray(3)
    private val rotationMatrix = FloatArray(9)

    private var smoothedPitch = 0f
    private var smoothedRoll = 0f
    private val alpha = 0.12f // Filtro de paso bajo (suavizado)

    init {
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor == null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
        // Aplicar escala inicial para que el video sea más grande que la pantalla
        targetView.scaleX = scaleFactor
        targetView.scaleY = scaleFactor
    }

    fun registerSensors() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            return
        }
        accelerometerSensor?.let { acc ->
            magnetometerSensor?.let { mag ->
                sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
                sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val screenWidth = targetView.width.toFloat()
        val screenHeight = targetView.height.toFloat()

        if (screenWidth <= 0f || screenHeight <= 0f) return

        // Máxima traslación permitida basada en la escala extra del video
        val maxTranslationX = (screenWidth * (scaleFactor - 1f)) / 2f
        val maxTranslationY = (screenHeight * (scaleFactor - 1f)) / 2f

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)

                val rawPitch = orientation[1] // Inclinación vertical
                val rawRoll = orientation[2]  // Inclinación horizontal

                smoothedPitch = smoothedPitch + alpha * (rawPitch - smoothedPitch)
                smoothedRoll = smoothedRoll + alpha * (rawRoll - smoothedRoll)

                val targetX = -smoothedRoll * (screenWidth * 0.3f)
                val targetY = -smoothedPitch * (screenHeight * 0.3f)

                targetView.translationX = targetX.coerceIn(-maxTranslationX, maxTranslationX)
                targetView.translationY = targetY.coerceIn(-maxTranslationY, maxTranslationY)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                calculateOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
                calculateOrientation()
            }
        }
    }

    private fun calculateOrientation() {
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            val rawPitch = orientation[1]
            val rawRoll = orientation[2]

            smoothedPitch = smoothedPitch + alpha * (rawPitch - smoothedPitch)
            smoothedRoll = smoothedRoll + alpha * (rawRoll - smoothedRoll)

            val screenWidth = targetView.width.toFloat()
            val screenHeight = targetView.height.toFloat()

            val maxTranslationX = (screenWidth * (scaleFactor - 1f)) / 2f
            val maxTranslationY = (screenHeight * (scaleFactor - 1f)) / 2f

            val targetX = -smoothedRoll * (screenWidth * 0.3f)
            val targetY = -smoothedPitch * (screenHeight * 0.3f)

            targetView.translationX = targetX.coerceIn(-maxTranslationX, maxTranslationX)
            targetView.translationY = targetY.coerceIn(-maxTranslationY, maxTranslationY)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

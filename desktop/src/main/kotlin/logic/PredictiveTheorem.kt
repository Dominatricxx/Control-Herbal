package logic

import kotlin.math.abs

object PredictiveTheorem {

    data class Range(
        val tempMin: Double, val tempMax: Double,
        val humMin: Double, val humMax: Double,
        val luzMin: Int, val luzMax: Int,
        val soilMin: Double, val soilMax: Double
    )

    private val ranges = mapOf(
        "Luz" to Range(20.0, 30.0, 40.0, 60.0, 60, 100, 30.0, 60.0),
        "Híbrido" to Range(18.0, 28.0, 50.0, 70.0, 30, 70, 40.0, 70.0),
        "Sombra" to Range(15.0, 25.0, 40.0, 60.0, 10, 40, 50.0, 80.0)
    )

    const val COEF_HUM_AMB = 0.25
    const val COEF_TEMP = 0.25
    const val COEF_LUZ = 0.15
    const val COEF_SUELO = 0.35

    const val IRH_OPTIMO = 25.0
    const val IRH_ADVERTENCIA = 50.0
    const val IRH_RIESGO = 75.0
    const val IRH_CRITICO = 100.0

    const val PREDICCION_MIN_HORAS = 2.0
    const val PREDICCION_MAX_HORAS = 6.0

    private fun getPlantTypeKey(plantType: String): String {
        return when {
            plantType.contains("Luz", ignoreCase = true) -> "Luz"
            plantType.contains("Sombra", ignoreCase = true) -> "Sombra"
            else -> "Híbrido"
        }
    }

    fun calcularEstresVariable(valor: Double, min: Double, max: Double): Double {
        return when {
            valor < min -> ((min - valor) / min * 100.0).coerceIn(0.0, 100.0)
            valor <= max -> 0.0
            else -> ((valor - max) / (100.0 - max) * 100.0).coerceIn(0.0, 150.0)
        }
    }

    fun generarRecomendacion(temp: Double, hum: Double, luz: Int, soil: Double, plantType: String): String {
        val key = getPlantTypeKey(plantType)
        val r = ranges[key] ?: ranges["Híbrido"]!!

        val eSuelo = calcularEstresVariable(soil, r.soilMin, r.soilMax)
        if (eSuelo > 80) return "💧 ALERTA SUELO: " + (if (soil < r.soilMin) "RIEGO INMEDIATO" else "DRENAJE URGENTE")
        
        val eTemp = calcularEstresVariable(temp, r.tempMin, r.tempMax)
        if (eTemp > 80) return "🔥 TEMPERATURA EXTREMA: Proporcione sombra y humedad"
        
        val eLuz = calcularEstresVariable(luz.toDouble(), r.luzMin.toDouble(), r.luzMax.toDouble())
        if (eLuz > 80) return "☀️ LUZ EXTREMA: Mueva a la sombra urgente"
        
        if (eSuelo > 40) return if (soil < r.soilMin) "🌿 Suelo seco, aumentar frecuencia de riego" else "🌊 Suelo saturado, reducir riego"
        if (eTemp > 40) return "🌡️ Estrés térmico detectado, proporcione sombra parcial"
        if (eLuz > 40) return "☀️ Exceso de luz solar, filtrar exposición"
        
        if (hum < r.humMin) return "💧 Baja humedad ambiente, rocíe las hojas"
        if (hum > r.humMax) return "💨 Alta humedad ambiente, mejore la ventilación"
        if (temp < r.tempMin) return "❄️ Ambiente frío, proteja la planta del clima"
        if (luz < r.luzMin) return "🌑 Poca luz detectada, acerque la planta a una ventana"
        
        return "✅ Condiciones óptimas"
    }

    data class AnalysisResult(
        val irh: Double,
        val seq: Double,
        val somb: Double,
        val recommendation: String,
        val urgency: String,
        val wateringRecommended: Boolean,
        val nextWateringHours: Double
    )

    fun analyze(
        temp: Double, 
        hum: Double, 
        luz: Int,
        soil: Double,
        deltaTemp: Double = 0.0,
        deltaHum: Double = 0.0,
        deltaLuz: Double = 0.0,
        deltaSoil: Double = 0.0,
        lastWateringTime: Long = 0,
        plantType: String = ""
    ): AnalysisResult {
        val key = getPlantTypeKey(plantType)
        val r = ranges[key] ?: ranges["Híbrido"]!!

        val estresH = calcularEstresVariable(hum, r.humMin, r.humMax)
        val estresT = calcularEstresVariable(temp, r.tempMin, r.tempMax)
        val estresL = calcularEstresVariable(luz.toDouble(), r.luzMin.toDouble(), r.luzMax.toDouble())
        val estresS = calcularEstresVariable(soil, r.soilMin, r.soilMax)

        var tendH = 0.0
        var tendT = 0.0
        var tendL = 0.0
        var tendS = 0.0

        if (deltaHum < -5.0) tendH = 15.0
        else if (deltaHum < -2.0) tendH = 8.0
        if (deltaTemp > 2.0) tendT = 15.0
        else if (deltaTemp > 1.0) tendT = 8.0
        if (deltaLuz > 10.0) tendL = 15.0
        if (deltaSoil < -2.0) tendS = 15.0
        else if (deltaSoil > 5.0) tendS = 5.0

        var irh = (estresH * COEF_HUM_AMB) + (estresT * COEF_TEMP) + (estresL * COEF_LUZ) + (estresS * COEF_SUELO)
        irh += (tendH * 0.05) + (tendT * 0.05) + (tendL * 0.05) + (tendS * 0.1)
        irh = irh.coerceIn(0.0, 100.0)

        val recommendation = generarRecomendacion(temp, hum, luz, soil, plantType)

        val wateringRecommended = soil < r.soilMin || (soil < r.soilMin + 5.0 && deltaSoil < -1.0)
        
        val nextWateringHours = if (wateringRecommended) 0.0 else {
            val h = (soil - r.soilMin) / (abs(deltaSoil) + 0.1)
            h.coerceIn(0.0, 48.0)
        }

        val seq = if (deltaSoil < -2.0 && soil < r.soilMin) {
            val h = (r.soilMin - soil) / (abs(deltaSoil) + 0.001)
            h.coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
        } else if (deltaTemp > 0.5 && luz > 70) 3.0
        else PREDICCION_MAX_HORAS

        val somb = if (deltaLuz > 10.0 && deltaTemp > 0) {
            val h = (90.0 - luz) / (deltaLuz + 0.001)
            h.coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
        } else if (luz > 80 && temp > r.tempMax) 2.0
        else PREDICCION_MAX_HORAS

        val urgency = when {
            irh > IRH_RIESGO -> "ALTA"
            irh > IRH_ADVERTENCIA -> "MODERADA"
            irh > IRH_OPTIMO -> "BAJA"
            else -> "ÓPTIMA"
        }

        return AnalysisResult(irh, seq, somb, recommendation, urgency, wateringRecommended, nextWateringHours)
    }
}

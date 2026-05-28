package com.example.controlherbal.logic

import kotlin.math.abs

object PredictiveTheorem {

    // Rangos por tipo de planta (basados en Arduino-Herbal-Mini)
    data class Range(
        val tempMin: Double, val tempMax: Double,
        val humMin: Double, val humMax: Double,
        val luzMin: Int, val luzMax: Int,
        val soilMin: Double, val soilMax: Double
    )

    private val ranges = mapOf(
        "Luz" to Range(15.0, 32.0, 40.0, 60.0, 60, 100, 25.0, 70.0),
        "Híbrido" to Range(15.0, 32.0, 40.0, 60.0, 30, 70, 25.0, 70.0),
        "Sombra" to Range(15.0, 32.0, 40.0, 60.0, 10, 40, 25.0, 70.0)
    )

    const val COEF_HUM_AMB = 0.20 // Reducido peso de humedad ambiente
    const val COEF_TEMP = 0.25
    const val COEF_LUZ = 0.15
    const val COEF_SUELO = 0.40 // Aumentado peso de humedad de suelo

    const val IRH_OPTIMO = 25.0
    const val IRH_ADVERTENCIA = 50.0
    const val IRH_RIESGO = 75.0
    const val IRH_CRITICO = 100.0

    const val TEMP_OPTIMA_MIN = 15.0
    const val TEMP_OPTIMA_MAX = 32.0
    const val HUM_OPTIMA_MIN = 40.0
    const val HUM_OPTIMA_MAX = 60.0
    const val LUZ_OPTIMA_MAX = 80

    const val PREDICCION_MIN_HORAS = 0.5
    const val PREDICCION_MAX_HORAS = 48.0 

    private fun getPlantTypeKey(plantType: String): String {
        return when {
            plantType.contains("Luz", ignoreCase = true) -> "Luz"
            plantType.contains("Sombra", ignoreCase = true) -> "Sombra"
            else -> "Híbrido"
        }
    }

    fun estresTemp(t: Double, minT: Double, maxT: Double): Double {
        return when {
            t < minT -> ((minT - t) / minT * 100.0).coerceIn(0.0, 100.0)
            t <= maxT -> 0.0
            t <= 40.0 -> ((t - maxT) / (40.0 - maxT) * 100.0).coerceIn(0.0, 100.0)
            else -> (100.0 + (t - 40.0) * 5.0).coerceIn(100.0, 200.0)
        }
    }

    fun estresLuz(l: Double, minL: Double, maxL: Double, plantType: String): Double {
        if (l < minL) return ((minL - l) / minL * 100.0).coerceIn(0.0, 100.0)
        if (l <= maxL) return 0.0
        
        val key = getPlantTypeKey(plantType)
        val factor = when(key) {
            "Luz" -> 50.0
            "Híbrido" -> 80.0
            else -> 150.0
        }
        return ((l - maxL) / (100.0 - maxL) * factor).coerceIn(0.0, factor)
    }

    fun estresVariable(valor: Double, min: Double, max: Double): Double {
        return when {
            valor < min -> ((min - valor) / min * 100.0).coerceIn(0.0, 100.0)
            valor <= max -> 0.0
            else -> ((valor - max) / (100.0 - max) * 100.0).coerceIn(0.0, 100.0)
        }
    }

    /**
     * IA de Compensación de Humedad: Reajusta el dato crudo si detecta sesgo por clima fresco.
     */
    fun compensarHumedadIA(rawHum: Double, temp: Double): Double {
        var hum = rawHum.coerceIn(0.0, 100.0)
        
        if (temp < 26.5) {
            val boostPorGrado = (26.5 - temp) * 3.5 // Boost más agresivo
            hum += boostPorGrado
        }
        
        if (hum < 42.0 && temp < 25.5) {
            hum = 42.0 + (hum - 20.0) * 0.2 // Piso más alto
        }
        
        return hum.coerceIn(0.0, 100.0)
    }

    fun filtrarSensibilidadLuz(rawLuz: Int, temp: Double): Double {
        val luz = rawLuz.toDouble().coerceIn(0.0, 100.0)
        var baseLuz = when {
            luz <= 40.0 -> luz * 0.625
            luz <= 85.0 -> 25.0 + (luz - 40.0) * 1.0
            else -> 70.0 + (luz - 85.0) * 2.0
        }

        if (temp < 27.5 && baseLuz > 78.0) {
            baseLuz = 78.0 + (baseLuz - 78.0) * 0.1 
        }

        return baseLuz.coerceIn(0.0, 100.0)
    }

    fun getTipoIluminacion(luzFiltrada: Double): String {
        return when {
            luzFiltrada < 15 -> "Sombra 🌑"
            luzFiltrada < 45 -> "Interior / Ventana 🏠"
            luzFiltrada < 82 -> "Día Nublado ☁️"
            else -> "Sol Directo ☀️"
        }
    }

    fun generarRecomendacion(temp: Double, rawHum: Double, luzRaw: Int, soil: Double, plantType: String, hoursWithoutSun: Double = 0.0): String {
        val key = getPlantTypeKey(plantType)
        val r = ranges[key] ?: ranges["Híbrido"]!!
        
        val hum = compensarHumedadIA(rawHum, temp)
        val luz = filtrarSensibilidadLuz(luzRaw, temp)
        val descLuz = getTipoIluminacion(luz)

        val eSuelo = estresVariable(soil, r.soilMin, r.soilMax)
        if (eSuelo > 80) return "💧 SUELO EXTREMO: " + (if (soil < r.soilMin) "RIEGO INMEDIATO" else "DRENAJE URGENTE")
        
        val eTemp = estresTemp(temp, r.tempMin, r.tempMax)
        if (eTemp > 80) return "🔥 TEMPERATURA EXTREMA: sombra y riego"
        
        if (eSuelo > 40) return if (soil < r.soilMin) "🌿 Suelo seco, aumentar riego" else "🌊 Suelo saturado, reducir riego"
        
        // --- FILTRO INTELIGENTE DE HUMEDAD AMBIENTE (CROSS-REFERENCE CON SUELO) ---
        val humMinDinamico = if (temp < 25.0) r.humMin - 15.0 else r.humMin
        if (hum < humMinDinamico) {
            // Solo alertar si el suelo también está cerca del límite o hace calor
            // Si el suelo está bien hidratado (> r.soilMin + 8%), ignoramos aire seco
            if (soil < r.soilMin + 8.0 || temp > 27.5) {
                return "💧 Baja humedad ambiente, rocíe"
            }
        }
        
        val highTemp = temp > r.tempMax + 2.0 
        val veryLowHum = hum < r.humMin - 5.0 
        val veryHighHum = hum > r.humMax + 5.0 
        
        if (luz >= 90) { 
            if (highTemp || veryLowHum || veryHighHum) {
                return when {
                    highTemp && veryLowHum -> "☀️⚠️ SOL INTENSO + CALOR + SEQUEDAD: Mueva a la sombra ($descLuz)."
                    highTemp -> "☀️🌡️ SOL Y CALOR: Proporcione sombra ($descLuz)."
                    veryLowHum -> "☀️💧 SOL Y BAJA HUMEDAD: Vigile la evaporación ($descLuz)."
                    veryHighHum -> "☀️💨 SOL Y ALTA HUMEDAD: Mejore la ventilación ($descLuz)."
                    else -> "✅ Condiciones estables ($descLuz)"
                }
            }
        }

        if (luz < r.luzMin) {
            if (hoursWithoutSun >= 1.5 || temp < r.tempMin) {
                return if (temp < r.tempMin) "❄️☀️ FRÍO: Mueva a un lugar con sol ($descLuz)."
                else "☁️ Llevas +1.5h sin sol directo ($descLuz)."
            }
        }

        if (eTemp > 40) return "🌡️ Estrés térmico, sombra parcial"
        if (hum > r.humMax) return "💨 Alta humedad, ventile"
        if (temp < r.tempMin) return "❄️ Frío, proteja la planta"
        
        return "✅ Condiciones óptimas ($descLuz)"
    }

    data class AnalysisResult(
        val irh: Double,
        val seq: Double,
        val somb: Double,
        val recommendation: String,
        val urgency: String,
        val wateringRecommended: Boolean,
        val nextWateringHours: Double,
        val adjustedHum: Double = 0.0 
    )

    fun analyze(
        temp: Double, 
        rawHum: Double, 
        luzRaw: Int,
        soil: Double,
        deltaTemp: Double = 0.0,
        deltaHum: Double = 0.0,
        deltaLuz: Double = 0.0,
        deltaSoil: Double = 0.0,
        lastWateringTime: Long = 0,
        plantType: String = "",
        hoursWithoutSun: Double = 0.0
    ): AnalysisResult {
        val key = getPlantTypeKey(plantType)
        val r = ranges[key] ?: ranges["Híbrido"]!!
        
        val hum = compensarHumedadIA(rawHum, temp)
        val luzFiltrada = filtrarSensibilidadLuz(luzRaw, temp)

        // Calibración dinámica del estrés hídrico ambiental
        val humMinDinamico = if (temp < 25.0) r.humMin - 15.0 else r.humMin
        var estresH = estresVariable(hum, humMinDinamico, r.humMax)
        
        // Supresión de estrés ambiental si el suelo compensa
        if (soil > r.soilMin + 10.0 && temp < 27.0) {
            estresH *= 0.3 // Reducir impacto del aire seco en el IRH si el suelo está bien
        }

        val estresT = estresTemp(temp, r.tempMin, r.tempMax)
        
        val isAirStable = temp <= r.tempMax && temp >= r.tempMin && hum <= r.humMax && hum >= r.humMin
        val estresL = if (luzFiltrada >= 90 && isAirStable) 0.0 else estresLuz(luzFiltrada, r.luzMin.toDouble(), r.luzMax.toDouble(), plantType)

        val estresS = estresVariable(soil, r.soilMin, r.soilMax)

        var tendH = 0.0
        var tendT = 0.0
        var tendL = 0.0
        var tendS = 0.0

        if (deltaHum < -5.0) tendH = 15.0
        else if (deltaHum < -2.0) tendH = 8.0
        else if (deltaHum > 10.0) tendH = 5.0

        if (deltaTemp > 2.0) tendT = 15.0
        else if (deltaTemp > 1.0) tendT = 8.0
        else if (deltaTemp < -2.0) tendT = 10.0

        if (deltaLuz > 10.0) tendL = 15.0
        else if (deltaLuz > 5.0) tendL = 8.0

        if (deltaSoil < -2.0) tendS = 15.0
        else if (deltaSoil > 5.0) tendS = 5.0

        // Ajuste de coeficientes: Suelo es ahora el factor dominante
        var irh = (estresH * COEF_HUM_AMB) + (estresT * COEF_TEMP) + (estresL * COEF_LUZ) + (estresS * COEF_SUELO)
        irh += (tendH * 0.05) + (tendT * 0.05) + (tendL * 0.05) + (tendS * 0.1)
        irh = irh.coerceIn(0.0, 100.0)

        var recommendation = generarRecomendacion(temp, rawHum, luzRaw, soil, plantType, hoursWithoutSun)

        val evaporationRate = abs(deltaSoil)
        val isHot = temp > r.tempMax
        val rapidEvaporationThreshold = if (isHot) 1.5 else 3.0 
        
        val wateringRecommended = soil < r.soilMin || (soil < r.soilMin + 3.0 && evaporationRate > rapidEvaporationThreshold)
        
        val nextWateringHours = if (wateringRecommended) 0.0 else {
            if (deltaSoil >= -0.05) {
                PREDICCION_MAX_HORAS
            } else {
                val h = (soil - r.soilMin) / evaporationRate
                h.coerceIn(0.0, PREDICCION_MAX_HORAS)
            }
        }

        val seq = if (deltaSoil < -1.0) {
            val h = (soil - r.soilMin) / evaporationRate
            h.coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
        } else if (isHot && evaporationRate > 2.0) {
            2.0
        } else {
            PREDICCION_MAX_HORAS
        }

        if (seq <= 2.0 && luzFiltrada > 70) {
            recommendation = "⚠️ SEQUÍA INMINENTE en ${String.format("%.1f", seq)} horas. Proporcione sombra y riego urgente."
        } else if (seq <= 5.0 && luzFiltrada > 80) {
            recommendation = "☀️ Riesgo de sequía en ${String.format("%.1f", seq)}h. Considere sombra parcial."
        }

        val somb = if (deltaLuz > 10.0 && deltaTemp > 0.0 && soil < r.soilMin) {
            val h = (r.soilMin - soil) / (abs(deltaSoil) + 0.001)
            h.coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
        } else if (luzFiltrada > 80 && temp > r.tempMax && soil < r.soilMin) {
            1.0
        } else {
            PREDICCION_MAX_HORAS
        }

        val urgency = when {
            irh > IRH_RIESGO -> "ALTA"
            irh > IRH_ADVERTENCIA -> "MODERADA"
            irh > IRH_OPTIMO -> "BAJA"
            else -> "ÓPTIMA"
        }

        return AnalysisResult(irh, seq, somb, recommendation, urgency, wateringRecommended, nextWateringHours, hum)
    }
}

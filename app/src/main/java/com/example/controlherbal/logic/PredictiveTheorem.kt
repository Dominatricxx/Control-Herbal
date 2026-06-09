package com.example.controlherbal.logic

import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * Teorema Predictivo - Sincronizado con Arduino-Herbal-Mini (Proyecto A Final)
 */
object PredictiveTheorem {

    // Rangos óptimos validados con fuentes académicas (Sincronizados con Proyecto A)
    data class Range(
        val tempMin: Double, val tempMax: Double,
        val humMin: Double, val humMax: Double,
        val luzMin: Double, val luzMax: Double,
        val soilMin: Double, val soilMax: Double
    )

    private val ranges = mapOf(
        "Luz" to Range(20.0, 35.0, 20.0, 50.0, 60.0, 100.0, 15.0, 40.0),
        "Híbrido" to Range(18.0, 30.0, 40.0, 70.0, 30.0, 70.0, 40.0, 70.0),
        "Sombra" to Range(15.0, 28.0, 50.0, 80.0, 10.0, 40.0, 60.0, 85.0)
    )

    // Coeficientes IRH
    const val COEF_HUM_AMB = 0.25 
    const val COEF_TEMP = 0.25
    const val COEF_LUZ = 0.15
    const val COEF_SUELO = 0.35 

    const val IRH_OPTIMO = 25.0
    const val IRH_ADVERTENCIA = 50.0
    const val IRH_RIESGO = 75.0
    const val IRH_CRITICO = 100.0

    const val TEMP_OPTIMA_MIN = 18.0
    const val TEMP_OPTIMA_MAX = 30.0
    const val HUM_OPTIMA_MIN = 40.0
    const val HUM_OPTIMA_MAX = 70.0

    const val PREDICCION_MIN_HORAS = 0.5
    const val PREDICCION_MAX_HORAS = 6.0 

    private fun getPlantTypeKey(plantType: String): String {
        return when {
            plantType.contains("Luz", ignoreCase = true) || plantType.contains("Sol", ignoreCase = true) -> "Luz"
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

    fun estresLuz(l: Double, minL: Double): Double {
        // Solo calculamos estrés por falta de luz.
        if (l < minL) return ((minL - l) / minL * 100.0).coerceIn(0.0, 100.0)
        // El exceso de luz no genera estrés directo en el IRH (petición de usuario),
        // pero influye en las predicciones de sequía.
        return 0.0
    }

    fun estresVariable(valor: Double, min: Double, max: Double): Double {
        return when {
            valor < min -> ((min - valor) / min * 100.0).coerceIn(0.0, 100.0)
            valor <= max -> 0.0
            else -> ((valor - max) / (100.0 - max) * 100.0).coerceIn(0.0, 100.0)
        }
    }

    /**
     * CORRECCIÓN DE LUZ AGRESIVA (Arduino Sync):
     * Aplica una curva de potencia 2.5 para comprimir valores altos.
     */
    fun corregirLuz(pct: Double): Double {
        return (pct / 100.0).pow(2.5) * 100.0
    }

    fun filtrarSensibilidadLuz(rawLuz: Double): Double {
        val pctLin = if (rawLuz > 100.0) {
            // Si es un valor crudo de ADC (e.g. 0-4095), lo normalizamos e invertimos
            val normalized = rawLuz / (if (rawLuz > 1023.0) 40.95 else 10.23)
            (100.0 - normalized).coerceIn(0.0, 100.0)
        } else {
            // Si ya es un porcentaje (0-100) del Arduino (0=oscuro, 100=luz), lo usamos directo
            rawLuz
        }
        return corregirLuz(pctLin)
    }

    fun filtrarSensibilidadSuelo(rawSoil: Double): Double {
        return if (rawSoil > 100.0) {
            val valorNormalizado = rawSoil / (if (rawSoil > 1023.0) 40.95 else 10.23)
            (100.0 - valorNormalizado).coerceIn(0.0, 100.0)
        } else {
            // Si el valor ya viene como porcentaje (0-100) del Arduino, 
            // lo tomamos directamente para evitar doble inversión (Sync con .ino)
            rawSoil.coerceIn(0.0, 100.0)
        }
    }

    fun filtrarSensibilidadHumedad(rawHum: Double): Double {
        val valorNormalizado = if (rawHum > 100.0) {
            rawHum / (if (rawHum > 1023.0) 40.95 else 10.23)
        } else {
            rawHum
        }
        return valorNormalizado.coerceIn(0.0, 100.0)
    }

    fun getEstadoIluminacion(luz: Double, temp: Double, isDay: Boolean = true): String {
        if (!isDay) return "Sombra / Noche 🌑"
        return when {
            luz > 60.0 && temp > 32.0 -> "Sol Directo ☀️"
            luz > 40.0 -> "Despejado 🌤️"
            luz > 15.0 -> "Nublado ☁️"
            else -> "Sombra ☁️"
        }
    }

    fun generarRecomendacion(temp: Double, hum: Double, luz: Double, soil: Double, plantType: String, isDay: Boolean = true): String {
        val key = getPlantTypeKey(plantType)
        val r = ranges[key] ?: ranges["Híbrido"]!!
        
        val eSuelo = estresVariable(soil, r.soilMin, r.soilMax)
        if (eSuelo > 80) return "💧 SUELO EXTREMO: " + (if (soil < r.soilMin) "RIEGO INMEDIATO" else "DRENAJE URGENTE")
        
        val eTemp = estresTemp(temp, r.tempMin, r.tempMax)
        if (eTemp > 80) return "🔥 TEMPERATURA EXTREMA: sombra y riego"
        
        if (eSuelo > 40) return if (soil < r.soilMin) "🌿 Suelo seco, aumentar riego" else "🌊 Suelo saturado, reducir riego"
        if (eTemp > 40) return "🌡️ Estrés térmico, sombra parcial"
        
        if (hum < r.humMin) return "💧 Baja humedad ambiente, rocíe"
        if (hum > r.humMax) return "💨 Alta humedad, ventile"
        if (temp < r.tempMin) return "❄️ Frío, proteja la planta"
        
        if (isDay) {
            if (luz < r.luzMin) return "🌑 Poca luz, acerque a ventana"
        } else {
            // En la noche no recomendamos acercar a ventana por falta de luz natural
            if (luz < r.luzMin) return "🌙 Noche - sin necesidad de luz adicional"
        }
        
        return "✅ Condiciones óptimas"
    }

    data class AnalysisResult(
        val irh: Double,
        val seq: Double,
        val somb: Double,
        val recommendation: String,
        val urgency: String,
        val wateringRecommended: Boolean,
        val nextWateringHours: Double,
        val adjustedHum: Double = 0.0,
        val adjustedLuz: Double = 0.0,
        val adjustedSoil: Double = 0.0,
        val isDataReliable: Boolean = true
    )

    fun analyze(
        temp: Double, 
        rawHum: Double, 
        luzRaw: Double,
        soilRaw: Double,
        deltaTemp: Double = 0.0,
        deltaHum: Double = 0.0,
        deltaLuz: Double = 0.0,
        deltaSoil: Double = 0.0,
        lastWateringTime: Long = 0,
        plantType: String = "",
        hoursWithoutSun: Double = 0.0,
        isDay: Boolean = true
    ): AnalysisResult {
        val key = getPlantTypeKey(plantType)
        val r = ranges[key] ?: ranges["Híbrido"]!!
        
        // 1. VERIFICACIÓN DE INTEGRIDAD (Anti-Fuga/Fallo)
        // Si el delta de suelo es una caída masiva (> 40% en un periodo corto), el sensor falló.
        val isDataReliable = !(abs(deltaSoil) > 40.0) && soilRaw in 0.1..99.9

        val hum = filtrarSensibilidadHumedad(rawHum)
        val luzFiltrada = filtrarSensibilidadLuz(luzRaw)
        val soil = soilRaw

        val eH = estresVariable(hum, r.humMin, r.humMax)
        val eT = estresTemp(temp, r.tempMin, r.tempMax)
        val eL = estresLuz(luzFiltrada, r.luzMin)
        val eS = estresVariable(soil, r.soilMin, r.soilMax)

        var tendH = 0.0; var tendT = 0.0; var tendL = 0.0; var tendS = 0.0
        if (deltaHum < -5.0) tendH = 15.0 else if (deltaHum < -2.0) tendH = 8.0
        if (deltaTemp > 2.0) tendT = 15.0 else if (deltaTemp > 1.0) tendT = 8.0
        if (deltaLuz > 10.0) tendL = 15.0
        if (deltaSoil < -5.0) tendS = 15.0

        var irh = (eH * COEF_HUM_AMB) + (eT * COEF_TEMP) + (eL * COEF_LUZ) + (eS * COEF_SUELO)
        irh += (tendH * 0.05) + (tendT * 0.05) + (tendL * 0.05) + (tendS * 0.1)
        irh = irh.coerceIn(0.0, 100.0)

        var recommendation = generarRecomendacion(temp, hum, luzFiltrada, soil, plantType, isDay)

        var seq = PREDICCION_MAX_HORAS; var somb = PREDICCION_MAX_HORAS

        if (isDay && isDataReliable) {
            seq = if (deltaSoil < -2.0 && soil < r.soilMin) {
                ((r.soilMin - soil) / (abs(deltaSoil) + 0.001)).coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
            } else if (deltaTemp > 0.5 && luzFiltrada > 70 && soil < r.soilMin) 1.0 else PREDICCION_MAX_HORAS

            if (seq <= 1.0 && luzFiltrada > 70) {
                recommendation = "⚠️ SEQUÍA INMINENTE en ${String.format("%.1f", seq)} horas. Sombra y riego urgente."
            }
            
            somb = if (deltaLuz > 10.0 && deltaTemp > 0.0 && soil < r.soilMin) {
                ((r.soilMin - soil) / (abs(deltaSoil) + 0.001)).coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
            } else if (luzFiltrada > 80 && temp > r.tempMax && soil < r.soilMin) 1.0 else PREDICCION_MAX_HORAS
        }

        // REGLAS SEVERAS DE RIEGO (Anti-Ahogo):
        // 1. El suelo debe estar bajo el mínimo.
        // 2. Si es noche, solo se riega si es CRÍTICO (< 5%) para evitar hongos/asfixia.
        // 3. Los datos deben ser fiables.
        val wateringRecommended = isDataReliable && (
            (soil < r.soilMin && isDay) || 
            (soil < 5.0) // Emergencia absoluta
        )
        
        val urgency = when {
            !isDataReliable -> "FALLO"
            irh > IRH_RIESGO -> "ALTA"
            irh > IRH_ADVERTENCIA -> "MODERADA"
            else -> "ÓPTIMA"
        }

        if (!isDataReliable) recommendation = "❌ ERROR: Sensor de suelo inestable. Riego bloqueado por seguridad."

        return AnalysisResult(irh, seq, somb, recommendation, urgency, wateringRecommended, seq, hum, luzFiltrada, soil, isDataReliable)
    }
}

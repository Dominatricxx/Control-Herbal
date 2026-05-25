package com.example.controlherbal.logic

import kotlin.math.abs
import kotlin.math.min

object PredictiveTheorem {

    // Thresholds
    const val TEMP_OPTIMA_MIN = 7.0
    const val TEMP_OPTIMA_MAX = 30.0
    const val TEMP_CRITICO_ALTA = 40.0

    const val HUM_OPTIMA_MIN = 25.0
    const val HUM_OPTIMA_MAX = 75.0
    const val HUM_ALTA_MEDIA = 90.0

    const val LUZ_OPTIMA_MAX = 65
    const val LUZ_ALTA = 80
    const val LUZ_EXCESIVA = 90

    const val COEF_HUMEDAD = 0.45
    const val COEF_TEMP = 0.35
    const val COEF_LUZ = 0.20

    const val IRH_OPTIMO = 25.0
    const val IRH_ADVERTENCIA = 50.0
    const val IRH_RIESGO = 75.0
    const val IRH_CRITICO = 100.0

    const val PREDICCION_MIN_HORAS = 2.0
    const val PREDICCION_MAX_HORAS = 6.0

    fun calcularEstresHumedad(h: Double): Double {
        return when {
            h < HUM_OPTIMA_MIN -> ((HUM_OPTIMA_MIN - h) / HUM_OPTIMA_MIN * 100.0).coerceIn(0.0, 100.0)
            h <= HUM_OPTIMA_MAX -> 0.0
            h <= HUM_ALTA_MEDIA -> ((h - HUM_OPTIMA_MAX) / (HUM_ALTA_MEDIA - HUM_OPTIMA_MAX) * 100.0).coerceIn(0.0, 100.0)
            else -> (100.0 + (h - HUM_ALTA_MEDIA) * 2.0).coerceIn(100.0, 150.0)
        }
    }

    fun calcularEstresTemperatura(t: Double): Double {
        return when {
            t < TEMP_OPTIMA_MIN -> ((TEMP_OPTIMA_MIN - t) / TEMP_OPTIMA_MIN * 100.0).coerceIn(0.0, 100.0)
            t <= TEMP_OPTIMA_MAX -> 0.0
            t <= TEMP_CRITICO_ALTA -> ((t - TEMP_OPTIMA_MAX) / (TEMP_CRITICO_ALTA - TEMP_OPTIMA_MAX) * 100.0).coerceIn(0.0, 100.0)
            else -> (100.0 + (t - TEMP_CRITICO_ALTA) * 2.0).coerceIn(100.0, 150.0)
        }
    }

    fun calcularEstresLuz(l: Int): Double {
        return when {
            l <= LUZ_OPTIMA_MAX -> 0.0
            l <= LUZ_ALTA -> ((l.toDouble() - LUZ_OPTIMA_MAX) / (LUZ_ALTA - LUZ_OPTIMA_MAX) * 100.0).coerceIn(0.0, 100.0)
            l <= LUZ_EXCESIVA -> (100.0 + (l - LUZ_ALTA) * 2.0).coerceIn(100.0, 120.0)
            else -> (100.0 + (l - LUZ_EXCESIVA) * 3.0).coerceIn(100.0, 150.0)
        }
    }

    fun generarRecomendacion(temp: Double, hum: Double, luz: Int, hoursSinceLastWatering: Double, plantType: String): String {
        val isCactus = plantType.contains("Suculentas", ignoreCase = true) || plantType.contains("Cactus", ignoreCase = true)
        val isMedicinal = plantType.contains("Medicinales", ignoreCase = true)
        
        // Umbrales de sobre-riego basados en botánica
        val overWateringThreshold = when {
            isCactus -> 48.0 // No regar más de una vez cada 2 días en condiciones extremas, idealmente mucho menos
            isMedicinal -> 8.0 // Las medicinales necesitan ciclos de humedad
            else -> 4.0 // 4 horas mínimo para cualquier planta
        }

        if (hoursSinceLastWatering < overWateringThreshold) {
            return "🛑 AVISO DE SOBRE-RIEGO: La planta fue hidratada hace poco (${String.format("%.1f", hoursSinceLastWatering)}h). El riego constante impide la oxigenación de las raíces (hipoxia) y causa pudrición. ¡Deje que el suelo seque un poco!"
        }

        val needsWater = hoursSinceLastWatering > 12.0 && hum < HUM_OPTIMA_MIN + 10.0
        
        return when {
            hum > HUM_ALTA_MEDIA -> "⚠️ HUMEDAD CRÍTICA (>90%): Riesgo de hongos y asfixia. Aumente ventilación, suspenda riego totalmente."
            temp > TEMP_CRITICO_ALTA -> "🔥 TEMPERATURA EXTREMA (>40°C): Estrés térmico severo. Proporcione sombra inmediata." + (if (needsWater) " Riego urgente para enfriar raíces." else " No riegue si la tierra ya está húmeda.")
            luz > LUZ_EXCESIVA -> "☀️ LUZ EXTREMA (>90%): Necesita sombra urgente. Mueva la planta para evitar quemaduras foliares."
            hum < HUM_OPTIMA_MIN -> "💧 BAJA HUMEDAD (<25%): Riesgo de deshidratación. Necesita riego controlado."
            temp < TEMP_OPTIMA_MIN -> "❄️ TEMPERATURA BAJA (<7°C): El metabolismo se detiene. Proteja del frío y reduzca el riego al mínimo."
            hum in (HUM_OPTIMA_MAX + 0.01)..HUM_ALTA_MEDIA -> "💦 EXCESO DE HUMEDAD (75-90%): Mejore drenaje o ventilación. El exceso de agua atrae plagas y hongos."
            temp in (TEMP_OPTIMA_MAX + 0.01)..TEMP_CRITICO_ALTA -> "🌡️ ESTRÉS POR CALOR (30-40°C): Proporcione sombra parcial." + (if (needsWater) " Considere riego ligero al atardecer." else "")
            else -> "✅ Condiciones saludables. La planta tiene un equilibrio hídrico óptimo."
        }
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
        deltaTemp: Double = 0.0,
        deltaHum: Double = 0.0,
        deltaLuz: Double = 0.0,
        lastWateringTime: Long = 0,
        plantType: String = ""
    ): AnalysisResult {
        val estresH = calcularEstresHumedad(hum)
        val estresT = calcularEstresTemperatura(temp)
        val estresL = calcularEstresLuz(luz)

        val hoursSinceLastWatering = if (lastWateringTime > 0) (System.currentTimeMillis() - lastWateringTime) / 3600000.0 else 100.0

        var tendenciaH = 0.0
        var tendenciaT = 0.0
        var tendenciaL = 0.0

        if (deltaHum < -5.0) tendenciaH = 15.0
        else if (deltaHum < -2.0) tendenciaH = 8.0
        else if (deltaHum > 10.0) tendenciaH = 5.0

        if (deltaTemp > 2.0) tendenciaT = 15.0
        else if (deltaTemp > 1.0) tendenciaT = 8.0
        else if (deltaTemp < -2.0) tendenciaT = 10.0

        if (deltaLuz > 10.0) tendenciaL = 15.0
        else if (deltaLuz > 5.0) tendenciaL = 8.0

        // Ajuste de IRH según riego reciente
        var irh = (estresH * COEF_HUMEDAD) + (estresT * COEF_TEMP) + (estresL * COEF_LUZ)
        
        // Si se regó hace menos de 4 horas, reducimos artificialmente el peso del estrés hídrico
        // para evitar falsas alarmas mientras el suelo se estabiliza.
        if (hoursSinceLastWatering < 4.0) {
            irh *= (hoursSinceLastWatering / 4.0).coerceAtLeast(0.2)
        }

        irh += (tendenciaH * 0.1) + (tendenciaT * 0.1) + (tendenciaL * 0.05)
        irh = irh.coerceIn(0.0, 100.0)

        val recommendation = generarRecomendacion(temp, hum, luz, hoursSinceLastWatering, plantType)

        // Lógica de necesidad de riego basada en agricultura y herbolaria
        // Factores: Humedad del suelo (simulada por hum ambiental aquí), tiempo, temperatura y tipo
        val isMedicinal = plantType.contains("Medicinales", ignoreCase = true)
        val isCactus = plantType.contains("Suculentas", ignoreCase = true) || plantType.contains("Cactus", ignoreCase = true)
        
        // Intervalos base recomendados (fuentes: FAO, manuales botánicos)
        val intervalBase = when {
            isCactus -> 168.0 // 1 semana aprox
            isMedicinal -> 48.0 // 2 días
            else -> 24.0 // Diario base
        }
        
        // Ajuste por calor
        val adjustedInterval = if (temp > 30.0) intervalBase * 0.7 else intervalBase
        
        val wateringRecommended = (hoursSinceLastWatering >= adjustedInterval && hum < 40.0) || (hum < HUM_OPTIMA_MIN)
        val nextWateringHours = (adjustedInterval - hoursSinceLastWatering).coerceAtLeast(0.0)

        val seq = when {
            deltaTemp > 0 && deltaHum < 0 -> {
                val horas = (HUM_OPTIMA_MIN - hum) / (abs(deltaHum) + 0.001)
                horas.coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
            }
            deltaTemp > 0.5 && luz > LUZ_ALTA -> 3.0
            else -> PREDICCION_MAX_HORAS
        }

        val somb = when {
            deltaLuz > 10 && deltaTemp > 0 -> {
                val horas = (LUZ_EXCESIVA - luz) / (deltaLuz + 0.001)
                horas.coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
            }
            luz > LUZ_ALTA && temp > TEMP_OPTIMA_MAX -> 2.0
            else -> PREDICCION_MAX_HORAS
        }

        val urgency = when {
            irh > IRH_RIESGO -> "ALTA"
            irh > IRH_ADVERTENCIA -> "MODERADA"
            irh > IRH_OPTIMO -> "BAJA"
            else -> "ÓPTIMA"
        }

        return AnalysisResult(irh, seq, somb, recommendation, urgency, wateringRecommended, nextWateringHours)
    }
}

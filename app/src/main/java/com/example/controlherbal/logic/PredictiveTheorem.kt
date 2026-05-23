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

    fun generarRecomendacion(temp: Double, hum: Double, luz: Int): String {
        return when {
            hum > HUM_ALTA_MEDIA -> "⚠️ HUMEDAD CRÍTICA (>90%): Riesgo de hongos. Aumente ventilación drásticamente, suspenda riego."
            temp > TEMP_CRITICO_ALTA -> "🔥 TEMPERATURA EXTREMA (>40°C): Estrés térmico severo. Proporcione sombra inmediata y riego abundante."
            luz > LUZ_EXCESIVA -> "☀️ LUZ EXTREMA (>90%): Necesita sombra urgente. Mueva la planta o coloque malla sombra."
            hum < HUM_OPTIMA_MIN -> "💧 BAJA HUMEDAD (<25%): Riesgo de deshidratación. Aumente riego o añada mulch para retener humedad."
            temp < TEMP_OPTIMA_MIN -> "❄️ TEMPERATURA BAJA (<7°C): Proteja del frío, cubra la planta o trasládela al interior."
            hum in (HUM_OPTIMA_MAX + 0.01)..HUM_ALTA_MEDIA -> "💦 EXCESO DE HUMEDAD (75-90%): Mejore ventilación o reduzca riego para evitar hongos."
            temp in (TEMP_OPTIMA_MAX + 0.01)..TEMP_CRITICO_ALTA -> "🌡️ ESTRÉS POR CALOR (30-40°C): Aumente riego y proporcione sombra parcial."
            luz in (LUZ_OPTIMA_MAX + 1)..LUZ_ALTA -> "☀️ EXCESO DE LUZ (65-80%): Considere sombra parcial o mover la planta a un lugar menos iluminado."
            else -> "✅ Condiciones saludables. Mantenga el cuidado actual."
        }
    }

    data class AnalysisResult(
        val irh: Double,
        val seq: Double,
        val somb: Double,
        val recommendation: String,
        val urgency: String
    )

    fun analyze(
        temp: Double, 
        hum: Double, 
        luz: Int,
        deltaTemp: Double = 0.0,
        deltaHum: Double = 0.0,
        deltaLuz: Double = 0.0
    ): AnalysisResult {
        val estresH = calcularEstresHumedad(hum)
        val estresT = calcularEstresTemperatura(temp)
        val estresL = calcularEstresLuz(luz)

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

        var irh = (estresH * COEF_HUMEDAD) + (estresT * COEF_TEMP) + (estresL * COEF_LUZ)
        irh += (tendenciaH * 0.1) + (tendenciaT * 0.1) + (tendenciaL * 0.05)
        irh = irh.coerceIn(0.0, 100.0)

        val recommendation = generarRecomendacion(temp, hum, luz)

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

        return AnalysisResult(irh, seq, somb, recommendation, urgency)
    }
}

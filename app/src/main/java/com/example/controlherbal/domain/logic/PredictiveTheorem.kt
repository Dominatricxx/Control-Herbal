package com.example.controlherbal.domain.logic

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.security.SecurityUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * PredictiveTheorem: Motor de cálculo teóricamente validado y sincronizado con firmware.
 * Calcula el Índice de Riesgo Hídrico (IRH), tiempo de sequía (SEQ) y sombra recomendada.
 */
object PredictiveTheorem {

    data class Range(
        val tempMin: Double, val tempMax: Double,
        val humMin: Double, val humMax: Double,
        val luzMin: Double, val luzMax: Double,
        val soilMin: Double, val soilMax: Double
    )

    private val ranges = mapOf(
        "Luz" to Range(18.0, 32.0, 20.0, 60.0, 40.0, 100.0, 10.0, 45.0),
        "Híbrido" to Range(16.0, 28.0, 30.0, 75.0, 20.0, 70.0, 25.0, 65.0),
        "Sombra" to Range(14.0, 26.0, 40.0, 85.0, 5.0, 40.0, 40.0, 80.0)
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

    const val PREDICCION_MIN_HORAS = 0.5
    const val PREDICCION_MAX_HORAS = 720.0 // 30 días

    /**
     * Tasa de deshidratación teórica (Evapotranspiración)
     */
    fun calcularTasaDeshidratacion(temp: Double, humAmb: Double, luz: Double, plantType: String): Double {
        val tempCorregida = if (luz > 50.0) temp - ((luz - 50.0) / 10.0) else temp
        
        val tasaBase = when (getPlantTypeKey(plantType)) {
            "Luz" -> 0.4
            "Sombra" -> 0.1 
            else -> 0.2      
        }

        val fTemp = (tempCorregida.coerceAtLeast(10.0) / 22.0).pow(1.8).coerceIn(0.01, 4.0)
        val fHum = (1.0 - (humAmb / 100.0)).pow(2.0).coerceIn(0.001, 2.0)
        val fLuz = (1.0 + (luz / 100.0) * 0.3).coerceIn(1.0, 1.3)
        val cycleCorrection = if (luz < 8.0) 0.15 else 1.0

        return tasaBase * fTemp * fHum * fLuz * cycleCorrection
    }

    fun getPlantTypeKey(plantType: String): String {
        return when {
            plantType.contains("Luz", ignoreCase = true) || plantType.contains("Sol", ignoreCase = true) -> "Luz"
            plantType.contains("Sombra", ignoreCase = true) -> "Sombra"
            else -> "Híbrido"
        }
    }

    fun estresTemp(t: Double, minT: Double, maxT: Double): Double {
        return when {
            t < minT -> ((minT - t) / minT.coerceAtLeast(1.0) * 100.0).coerceIn(0.0, 100.0)
            t <= maxT -> 0.0
            t <= 40.0 -> ((t - maxT) / (40.0 - maxT).coerceAtLeast(1.0) * 100.0).coerceIn(0.0, 100.0)
            else -> (100.0 + (t - 40.0) * 5.0).coerceIn(100.0, 200.0)
        }
    }

    fun estresLuz(l: Double, minL: Double): Double {
        if (l < minL) return ((minL - l) / minL.coerceAtLeast(1.0) * 100.0).coerceIn(0.0, 100.0)
        return 0.0
    }

    fun estresVariable(valor: Double, min: Double, max: Double): Double {
        return when {
            valor < min -> ((min - valor) / min.coerceAtLeast(1.0) * 100.0).coerceIn(0.0, 100.0)
            valor <= max -> 0.0
            else -> ((valor - max) / (100.0 - max).coerceAtLeast(1.0) * 100.0).coerceIn(0.0, 100.0)
        }
    }

    fun corregirLuz(pct: Double): Double {
        return (pct / 100.0).pow(2.5) * 100.0
    }

    fun filtrarSensibilidadLuz(rawLuz: Double): Double {
        val pctLin = if (rawLuz > 100.0) {
            val normalized = rawLuz / (if (rawLuz > 1023.0) 40.95 else 10.23)
            (100.0 - normalized).coerceIn(0.0, 100.0)
        } else {
            rawLuz
        }
        return corregirLuz(pctLin)
    }

    fun filtrarSensibilidadSuelo(rawSoil: Double): Double {
        return if (rawSoil > 100.0) {
            val valorNormalizado = rawSoil / (if (rawSoil > 1023.0) 40.95 else 10.23)
            (100.0 - valorNormalizado).coerceIn(0.0, 100.0)
        } else {
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

    fun generarRecomendacion(temp: Double, hum: Double, luz: Double, soil: Double, plantType: String, isDay: Boolean = true): String {
        val key = getPlantTypeKey(plantType)
        val r = ranges[key] ?: ranges["Híbrido"]!!
        
        val eSuelo = estresVariable(soil, r.soilMin, r.soilMax)
        if (eSuelo > 80) return "💧 SUELO EXTREMO: " + (if (soil < r.soilMin) "RIEGO INMEDIATO" else "DRENAJE URGENTE")
        
        val eTemp = estresTemp(temp, r.tempMin, r.tempMax)
        if (eTemp > 80) return "🔥 TEMPERATURA EXTREMA: sombra y riego"
        
        if (eSuelo > 40) return if (soil < r.soilMin) "Suelo seco, aumentar riego" else "Suelo saturado, reducir riego"
        if (eTemp > 40) return "Estres termico, sombra parcial"
        
        if (hum < r.humMin) return "Baja humedad ambiente, rocie"
        if (hum > r.humMax) return "Alta humedad, ventile"
        if (temp < r.tempMin) return "Frio, proteja la planta"
        
        if (isDay) {
            if (luz < r.luzMin) return "Poca luz, acerque a ventana"
        } else {
            if (luz < r.luzMin) return "Noche - sin necesidad de luz adicional"
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
        val isDataReliable: Boolean = true,
        val confidence: Double = 1.0
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
        isDay: Boolean = true,
        aiFactor: Double = 1.0,
        historySize: Int = 0
    ): AnalysisResult {
        val key = getPlantTypeKey(plantType)
        val r = ranges[key] ?: ranges["Híbrido"]!!
        
        val calibratedTemp = if (luzRaw > 20.0) {
            (temp - ((luzRaw - 20.0) * 0.15)).coerceAtLeast(18.0)
        } else temp

        val isDataReliable = !(abs(deltaSoil) > 40.0) && soilRaw in 0.1..99.9
        
        val confidence = when {
            !isDataReliable -> 0.0
            historySize < 15 -> 0.4 
            historySize < 60 -> 0.7 
            else -> 1.0 
        }

        val hum = filtrarSensibilidadHumedad(rawHum)
        val luzFiltrada = filtrarSensibilidadLuz(luzRaw)
        val soil = SecurityUtils.clampValue(soilRaw, 0.0, AppConstants.MAX_SOIL_NORMAL)

        val eH = estresVariable(hum, r.humMin, r.humMax)
        val eT = estresTemp(calibratedTemp, r.tempMin, r.tempMax)
        val eL = estresLuz(luzFiltrada, r.luzMin)
        val eS = estresVariable(soil, r.soilMin, r.soilMax)

        var tendH = 0.0; var tendT = 0.0; var tendS = 0.0
        if (deltaHum < -8.0) tendH = 10.0
        if (deltaTemp > 3.0) tendT = 10.0
        if (deltaSoil < -3.0) tendS = 12.0

        var irh = (eH * COEF_HUM_AMB) + (eT * COEF_TEMP) + (eL * COEF_LUZ) + (eS * COEF_SUELO)
        irh += (tendH * 0.05) + (tendT * 0.05) + (tendS * 0.1)
        irh = SecurityUtils.clampValue(irh, 0.0, 100.0)

        var recommendation = generarRecomendacion(calibratedTemp, hum, luzFiltrada, soil, plantType, isDay)

        var seq = PREDICCION_MAX_HORAS
        var somb = PREDICCION_MAX_HORAS

        if (isDataReliable) {
            val deficitAgua = (soil - r.soilMin).coerceAtLeast(0.0)
            
            val tasaDiaTeorica = calcularTasaDeshidratacion(calibratedTemp, hum, 80.0, plantType) * aiFactor
            val tasaNocheTeorica = calcularTasaDeshidratacion(calibratedTemp, hum, 0.0, plantType) * aiFactor
            
            val tasaCicloPromedio = (tasaDiaTeorica * 13.0 + tasaNocheTeorica * 11.0) / 24.0

            val tasaRealMedida = if (deltaSoil < -0.05) abs(deltaSoil) else 0.0
            val tasaEstable = (tasaCicloPromedio * 0.85) + (tasaRealMedida.coerceAtMost(tasaDiaTeorica * 1.1) * 0.15)

            seq = (deficitAgua / (tasaEstable.coerceAtLeast(0.0001)))
            
            if (soil > 70.0 && seq < 168.0) {
                 val factorEstabilidad = if (calibratedTemp < 30.0) 1.2 else 1.0
                 seq = (168.0 * factorEstabilidad).coerceAtLeast(seq)
            }

            seq = SecurityUtils.clampValue(seq, 0.0, PREDICCION_MAX_HORAS)

            if (isDay && seq <= 3.0 && soil < (r.soilMin + 2)) {
                recommendation = "NIVEL BAJO: Proximo riego estimado en ${String.format(Locale.getDefault(), "%.1f", seq)}h."
            }
            
            if (isDay) {
                somb = if (deltaLuz > 10.0 && deltaTemp > 0.0 && soil < r.soilMin) {
                    ((r.soilMin - soil) / (abs(deltaSoil) + 0.001)).coerceIn(PREDICCION_MIN_HORAS, PREDICCION_MAX_HORAS)
                } else if (luzFiltrada > 80 && calibratedTemp > r.tempMax && soil < r.soilMin) 1.0 else PREDICCION_MAX_HORAS
            }
        }

        val wateringRecommended = isDataReliable && (
            (soil < r.soilMin && isDay) || 
            (soil < AppConstants.MIN_SOIL_CRITICAL)
        )
        
        val urgency = when {
            !isDataReliable -> "FALLO"
            irh > IRH_RIESGO -> "ALTA"
            irh > IRH_ADVERTENCIA -> "MODERADA"
            else -> "ÓPTIMA"
        }

        if (!isDataReliable) recommendation = "❌ ERROR: Sensor de suelo inestable. Riego bloqueado por seguridad."

        return AnalysisResult(irh, seq, somb, recommendation, urgency, wateringRecommended, seq, hum, luzFiltrada, soil, isDataReliable, confidence)
    }
}

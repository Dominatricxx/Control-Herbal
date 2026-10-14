package com.example.controlherbal.ui.components

import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.ui.viewmodel.SensorUiState

/**
 * Constants for sensor data mapping
 */
const val LIGHT_MAX_LUX = 1000.0
const val TEMP_MIN_C = 0.0
const val TEMP_MAX_C = 50.0

data class HerbalRingValues(
    val water: Float,
    val sun: Float,
    val plant: Float,
    val temperature: Float,
    val hasData: Boolean
)

/**
 * Maps raw sensor readings or UI state into 0..100 percentage values for the herbal status widget.
 */
fun mapSensorReadingToHerbalValues(reading: SensorReading?): HerbalRingValues {
    if (reading == null) {
        return HerbalRingValues(0f, 0f, 0f, 0f, false)
    }

    // Agua (drop) = humidity (%), direct from reading.humidity
    val water = if (reading.humidity.isNaN() || reading.humidity.isInfinite()) 0f
    else reading.humidity.toFloat().coerceIn(0f, 100f)

    // Sol = light: percentage = light / LIGHT_MAX_LUX * 100, from reading.light (lux)
    val sunRaw = (reading.light / LIGHT_MAX_LUX) * 100.0
    val sun = if (sunRaw.isNaN() || sunRaw.isInfinite()) 0f
    else sunRaw.toFloat().coerceIn(0f, 100f)

    // Planta (brote) = soilMoisture (%), direct from reading.soilMoisture
    val plant = if (reading.soilMoisture.isNaN() || reading.soilMoisture.isInfinite()) 0f
    else reading.soilMoisture.toFloat().coerceIn(0f, 100f)

    // Temperatura = temperature (°C): percentage = (temperature - TEMP_MIN_C) / (TEMP_MAX_C - TEMP_MIN_C) * 100, from reading.temperature (°C)
    val tempRaw = ((reading.temperature - TEMP_MIN_C) / (TEMP_MAX_C - TEMP_MIN_C)) * 100.0
    val temperature = if (tempRaw.isNaN() || tempRaw.isInfinite()) 0f
    else tempRaw.toFloat().coerceIn(0f, 100f)

    return HerbalRingValues(
        water = water,
        sun = sun,
        plant = plant,
        temperature = temperature,
        hasData = true
    )
}

/**
 * Overload to map from SensorUiState.
 */
fun mapSensorUiStateToHerbalValues(state: SensorUiState?, hasConnection: Boolean): HerbalRingValues {
    if (state == null || (!hasConnection && state.temp == 0.0 && state.hum == 0.0 && state.luz == 0.0 && state.soil == 0.0)) {
        return HerbalRingValues(0f, 0f, 0f, 0f, false)
    }

    // Agua (drop) = hum (%), from SensorUiState.hum
    val water = if (state.hum.isNaN() || state.hum.isInfinite()) 0f
    else state.hum.toFloat().coerceIn(0f, 100f)

    // Sol = luz: percentage = luz / LIGHT_MAX_LUX * 100, from SensorUiState.luz (lux)
    val sunRaw = (state.luz / LIGHT_MAX_LUX) * 100.0
    val sun = if (sunRaw.isNaN() || sunRaw.isInfinite()) 0f
    else sunRaw.toFloat().coerceIn(0f, 100f)

    // Planta (brote) = soil (%), from SensorUiState.soil
    val plant = if (state.soil.isNaN() || state.soil.isInfinite()) 0f
    else state.soil.toFloat().coerceIn(0f, 100f)

    // Temperatura = temp (°C): percentage = (temp - TEMP_MIN_C) / (TEMP_MAX_C - TEMP_MIN_C) * 100, from SensorUiState.temp (°C)
    val tempRaw = ((state.temp - TEMP_MIN_C) / (TEMP_MAX_C - TEMP_MIN_C)) * 100.0
    val temperature = if (tempRaw.isNaN() || tempRaw.isInfinite()) 0f
    else tempRaw.toFloat().coerceIn(0f, 100f)

    return HerbalRingValues(
        water = water,
        sun = sun,
        plant = plant,
        temperature = temperature,
        hasData = true
    )
}

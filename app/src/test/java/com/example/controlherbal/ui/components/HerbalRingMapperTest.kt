package com.example.controlherbal.ui.components

import com.example.controlherbal.data.database.SensorReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HerbalRingMapperTest {

    @Test
    fun testNullReading() {
        val result = mapSensorReadingToHerbalValues(null)
        assertFalse(result.hasData)
        assertEquals(0f, result.water)
        assertEquals(0f, result.sun)
        assertEquals(0f, result.plant)
        assertEquals(0f, result.temperature)
    }

    @Test
    fun testTemperatureMapping() {
        // TEMP_MIN_C = 0.0, TEMP_MAX_C = 50.0
        // 0 °C -> 0%
        val r0 = createReading(temperature = 0.0)
        assertEquals(0f, mapSensorReadingToHerbalValues(r0).temperature)

        // 25 °C -> 50%
        val r25 = createReading(temperature = 25.0)
        assertEquals(50f, mapSensorReadingToHerbalValues(r25).temperature)

        // 50 °C -> 100%
        val r50 = createReading(temperature = 50.0)
        assertEquals(100f, mapSensorReadingToHerbalValues(r50).temperature)

        // 80 °C -> clamps to 100%
        val r80 = createReading(temperature = 80.0)
        assertEquals(100f, mapSensorReadingToHerbalValues(r80).temperature)

        // -10 °C -> clamps to 0%
        val rNeg = createReading(temperature = -10.0)
        assertEquals(0f, mapSensorReadingToHerbalValues(rNeg).temperature)
    }

    @Test
    fun testLightMapping() {
        // LIGHT_MAX_LUX = 1000.0
        // 500 lux -> 50%
        val r500 = createReading(light = 500.0)
        assertEquals(50f, mapSensorReadingToHerbalValues(r500).sun)

        // 2000 lux -> clamps to 100%
        val r2000 = createReading(light = 2000.0)
        assertEquals(100f, mapSensorReadingToHerbalValues(r2000).sun)
    }

    @Test
    fun testNanAndInfinityMapping() {
        val rNan = createReading(
            temperature = Double.NaN,
            humidity = Double.NaN,
            light = Double.NaN,
            soilMoisture = Double.NaN
        )
        val result = mapSensorReadingToHerbalValues(rNan)
        assertTrue(result.hasData)
        assertEquals(0f, result.water)
        assertEquals(0f, result.sun)
        assertEquals(0f, result.plant)
        assertEquals(0f, result.temperature)
    }

    private fun createReading(
        temperature: Double = 25.0,
        humidity: Double = 50.0,
        light: Double = 500.0,
        soilMoisture: Double = 60.0
    ): SensorReading {
        return SensorReading(
            timestamp = System.currentTimeMillis(),
            plantId = 1,
            temperature = temperature,
            humidity = humidity,
            light = light,
            soilMoisture = soilMoisture,
            irh = 50.0,
            seq = 10.0,
            somb = 0.0,
            action = "Test"
        )
    }
}

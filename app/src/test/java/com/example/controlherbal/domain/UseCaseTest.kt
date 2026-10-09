package com.example.controlherbal.domain

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
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.repository.PlantRepository
import com.example.controlherbal.domain.repository.SensorDataRepository
import com.example.controlherbal.domain.usecase.ExecuteWateringUseCase
import com.example.controlherbal.domain.usecase.GetSelectedPlantUseCase
import com.example.controlherbal.domain.usecase.GetSensorHistoryUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCaseTest {

    private class FakePlantRepository : PlantRepository {
        var selectedPlant: Plant? = Plant(id = 1, name = "Menta", type = "Menta", environment = "Luz 🌞", isSelected = true)
        override suspend fun getSelectedPlant(): Plant? = selectedPlant
        override suspend fun getAllPlants(): List<Plant> = listOfNotNull(selectedPlant)
        override suspend fun getPlantCount(): Int = if (selectedPlant != null) 1 else 0
        override suspend fun selectPlant(plant: Plant) { selectedPlant = plant }
        override suspend fun insertPlant(plant: Plant) { selectedPlant = plant }
        override suspend fun updatePlant(plant: Plant) { selectedPlant = plant }
        override suspend fun deletePlant(plant: Plant) { selectedPlant = null }
    }

    private class FakeSensorDataRepository : SensorDataRepository {
        var wateringSent = false
        override suspend fun getRecentReadings(plantId: Int, limit: Int): List<SensorReading> {
            return listOf(
                SensorReading(
                    timestamp = System.currentTimeMillis(),
                    plantId = plantId,
                    temperature = 22.0,
                    humidity = 60.0,
                    light = 80.0,
                    soilMoisture = 55.0,
                    irh = 80.0,
                    seq = 0.0,
                    somb = 0.0,
                    action = "OPT"
                )
            )
        }
        override suspend fun sendWateringCommand(seconds: Int, issuerUid: String?): Boolean {
            wateringSent = true
            return true
        }
        override suspend fun updatePlantTypeInCloud(tipoInt: Int): Boolean = true
    }

    @Test
    fun getSelectedPlantUseCase_returnsActivePlant() = runBlocking {
        val repo = FakePlantRepository()
        val useCase = GetSelectedPlantUseCase(repo)
        val plant = useCase()
        assertNotNull(plant)
        assertEquals("Menta", plant?.name)
    }

    @Test
    fun getSensorHistoryUseCase_returnsReadings() = runBlocking {
        val repo = FakeSensorDataRepository()
        val useCase = GetSensorHistoryUseCase(repo)
        val readings = useCase(1, 100)
        assertEquals(1, readings.size)
        assertEquals(22.0, readings[0].temperature, 0.1)
    }

    @Test
    fun executeWateringUseCase_validDuration_succeeds() = runBlocking {
        val repo = FakeSensorDataRepository()
        val useCase = ExecuteWateringUseCase(repo)
        val result = useCase(10, "uid123")
        assertTrue(result.isSuccess)
        assertTrue(repo.wateringSent)
    }

    @Test
    fun executeWateringUseCase_invalidDuration_fails() = runBlocking {
        val repo = FakeSensorDataRepository()
        val useCase = ExecuteWateringUseCase(repo)
        val result = useCase(AppConstants.MAX_WATERING_SECONDS + 10, "uid123")
        assertTrue(result.isFailure)
        assertFalse(repo.wateringSent)
    }
}

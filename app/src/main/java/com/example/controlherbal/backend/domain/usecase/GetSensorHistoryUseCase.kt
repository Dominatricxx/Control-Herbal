package com.example.controlherbal.backend.domain.usecase


import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.backend.domain.repository.SensorDataRepository

/**
 * GetSensorHistoryUseCase: Obtiene y procesa las lecturas históricas de los sensores para una planta dada.
 */
class GetSensorHistoryUseCase(
    private val sensorDataRepository: SensorDataRepository
) {
    suspend operator fun invoke(plantId: Int, limit: Int = 2000): List<SensorReading> {
        if (plantId <= 0) return emptyList()
        return sensorDataRepository.getRecentReadings(plantId, limit)
    }
}

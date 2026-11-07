package com.example.controlherbal.domain.usecase

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.frontend.activities.main.*
import com.example.controlherbal.frontend.activities.auth.*
import com.example.controlherbal.frontend.activities.privacy.*
import com.example.controlherbal.frontend.activities.plant.*
import com.example.controlherbal.frontend.style.*

import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.domain.repository.SensorDataRepository

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

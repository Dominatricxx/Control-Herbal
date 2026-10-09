package com.example.controlherbal.domain.usecase

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

import com.example.controlherbal.data.database.SensorReading
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

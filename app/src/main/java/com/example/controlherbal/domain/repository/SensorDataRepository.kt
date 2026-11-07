package com.example.controlherbal.domain.repository

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

/**
 * SensorDataRepository: Contrato abstracto para la consulta de datos de sensores y comandos de riego.
 */
interface SensorDataRepository {
    suspend fun getRecentReadings(plantId: Int, limit: Int = 2000): List<SensorReading>
    suspend fun sendWateringCommand(seconds: Int, issuerUid: String?): Boolean
    suspend fun updatePlantTypeInCloud(tipoInt: Int): Boolean
}

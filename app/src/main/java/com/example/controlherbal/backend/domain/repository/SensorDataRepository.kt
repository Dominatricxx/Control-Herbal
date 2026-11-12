package com.example.controlherbal.backend.domain.repository


import com.example.controlherbal.database.SensorReading

/**
 * SensorDataRepository: Contrato abstracto para la consulta de datos de sensores y comandos de riego.
 */
interface SensorDataRepository {
    suspend fun getRecentReadings(plantId: Int, limit: Int = 2000): List<SensorReading>
    suspend fun sendWateringCommand(seconds: Int, issuerUid: String?): Boolean
    suspend fun updatePlantTypeInCloud(tipoInt: Int): Boolean
}

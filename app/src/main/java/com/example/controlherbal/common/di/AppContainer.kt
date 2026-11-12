package com.example.controlherbal.common.di

import android.content.Context
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.backend.repository.PlantRepositoryImpl
import com.example.controlherbal.backend.repository.SensorDataRepositoryImpl
import com.example.controlherbal.backend.domain.repository.PlantRepository
import com.example.controlherbal.backend.domain.repository.SensorDataRepository

/**
 * AppContainer: Contenedor de Inyección de Dependencias manual (Service Locator).
 * Inicializa de forma perezosa (by lazy) las dependencias globales compartidas del proyecto.
 */
class AppContainer(private val context: Context) {

    val database: SensorDatabase by lazy {
        SensorDatabase.getInstance(context)
    }

    val plantRepository: PlantRepository by lazy {
        PlantRepositoryImpl(database.plantDao())
    }

    val sensorDataRepository: SensorDataRepository by lazy {
        SensorDataRepositoryImpl(database.sensorDao())
    }
}

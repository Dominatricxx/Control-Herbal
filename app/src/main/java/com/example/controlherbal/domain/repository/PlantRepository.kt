package com.example.controlherbal.domain.repository

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

import com.example.controlherbal.data.database.Plant

/**
 * PlantRepository: Contrato abstracto para las operaciones de lectura y escritura de plantas.
 * Permite desacoplar las fuentes de datos (Room / Firebase) de la lógica de negocio.
 */
interface PlantRepository {
    suspend fun getSelectedPlant(): Plant?
    suspend fun getAllPlants(): List<Plant>
    suspend fun getPlantCount(): Int
    suspend fun selectPlant(plant: Plant)
    suspend fun insertPlant(plant: Plant)
    suspend fun updatePlant(plant: Plant)
    suspend fun deletePlant(plant: Plant)
}

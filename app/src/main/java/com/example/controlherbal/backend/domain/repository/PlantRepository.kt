package com.example.controlherbal.backend.domain.repository


import com.example.controlherbal.database.Plant

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

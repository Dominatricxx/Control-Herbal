package com.example.controlherbal.data.repository

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
import com.example.controlherbal.data.database.PlantDao
import com.example.controlherbal.domain.repository.PlantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PlantRepositoryImpl: Implementación concreta de PlantRepository basada en Room Database.
 */
class PlantRepositoryImpl(
    private val plantDao: PlantDao
) : PlantRepository {

    override suspend fun getSelectedPlant(): Plant? = withContext(Dispatchers.IO) {
        plantDao.getSelectedPlant()
    }

    override suspend fun getAllPlants(): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getAll()
    }

    override suspend fun getPlantCount(): Int = withContext(Dispatchers.IO) {
        plantDao.getPlantCount()
    }

    override suspend fun selectPlant(plant: Plant): Unit = withContext(Dispatchers.IO) {
        plantDao.deselectAll()
        plantDao.update(plant.copy(isSelected = true))
    }

    override suspend fun insertPlant(plant: Plant): Unit = withContext(Dispatchers.IO) {
        plantDao.insert(plant)
    }

    override suspend fun updatePlant(plant: Plant): Unit = withContext(Dispatchers.IO) {
        plantDao.update(plant)
    }

    override suspend fun deletePlant(plant: Plant): Unit = withContext(Dispatchers.IO) {
        plantDao.delete(plant)
    }
}

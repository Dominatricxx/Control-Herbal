package com.example.controlherbal.database.repository

import com.example.controlherbal.data.database.PlantTask
import com.example.controlherbal.data.database.PlantTaskDao
import com.example.controlherbal.domain.repository.PlantTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PlantTaskRepositoryImpl(
    private val plantTaskDao: PlantTaskDao
) : PlantTaskRepository {

    override suspend fun insertTask(task: PlantTask): Long = withContext(Dispatchers.IO) {
        plantTaskDao.insertTask(task)
    }

    override suspend fun completeTask(taskId: Long, completedAt: Long) = withContext(Dispatchers.IO) {
        plantTaskDao.completeTask(taskId, completedAt)
    }

    override suspend fun skipTask(taskId: Long) = withContext(Dispatchers.IO) {
        plantTaskDao.skipTask(taskId)
    }

    override fun observePendingTasks(plantId: Int): Flow<List<PlantTask>> {
        return plantTaskDao.observePendingTasks(plantId)
    }

    override fun observeTasksForRange(plantId: Int, startMillis: Long, endMillis: Long): Flow<List<PlantTask>> {
        return plantTaskDao.observeTasksForRange(plantId, startMillis, endMillis)
    }
}

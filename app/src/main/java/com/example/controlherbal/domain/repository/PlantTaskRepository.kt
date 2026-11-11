package com.example.controlherbal.domain.repository

import com.example.controlherbal.data.database.PlantTask
import kotlinx.coroutines.flow.Flow

interface PlantTaskRepository {
    suspend fun insertTask(task: PlantTask): Long
    suspend fun completeTask(taskId: Long, completedAt: Long)
    suspend fun skipTask(taskId: Long)
    fun observePendingTasks(plantId: Int): Flow<List<PlantTask>>
    fun observeTasksForRange(plantId: Int, startMillis: Long, endMillis: Long): Flow<List<PlantTask>>
}

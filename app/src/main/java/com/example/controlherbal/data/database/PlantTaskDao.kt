package com.example.controlherbal.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantTaskDao {

    @Insert
    suspend fun insertTask(task: PlantTask): Long

    @Query("UPDATE plant_tasks SET status = 'COMPLETED', completedAt = :completedAt WHERE id = :taskId")
    suspend fun completeTask(taskId: Long, completedAt: Long)

    @Query("UPDATE plant_tasks SET status = 'SKIPPED' WHERE id = :taskId")
    suspend fun skipTask(taskId: Long)

    @Query("SELECT * FROM plant_tasks WHERE plantId = :plantId AND status = 'PENDING' ORDER BY dueAt ASC")
    fun observePendingTasks(plantId: Int): Flow<List<PlantTask>>

    @Query("SELECT * FROM plant_tasks WHERE plantId = :plantId AND dueAt BETWEEN :startMillis AND :endMillis ORDER BY dueAt ASC")
    fun observeTasksForRange(plantId: Int, startMillis: Long, endMillis: Long): Flow<List<PlantTask>>

    @Query("SELECT * FROM plant_tasks WHERE dueAt BETWEEN :startMillis AND :endMillis ORDER BY dueAt ASC")
    fun observeAllTasksForRange(startMillis: Long, endMillis: Long): Flow<List<PlantTask>>

    @Query("SELECT * FROM plant_tasks ORDER BY dueAt ASC")
    fun observeAllTasks(): Flow<List<PlantTask>>
}

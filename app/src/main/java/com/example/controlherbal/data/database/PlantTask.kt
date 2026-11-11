package com.example.controlherbal.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus {
    PENDING,
    COMPLETED,
    SKIPPED
}

@Entity(tableName = "plant_tasks")
data class PlantTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Int,
    val title: String,
    val description: String? = null,
    val dueAt: Long,
    val status: TaskStatus = TaskStatus.PENDING,
    val completedAt: Long? = null
)

package com.example.controlherbal.backend.domain.usecase

import com.example.controlherbal.database.entity.PlantTask
import com.example.controlherbal.backend.domain.repository.PlantTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

data class NextTask(
    val task: PlantTask,
    val isOverdue: Boolean,
    val daysUntil: Int
)

class GetNextTaskUseCase(
    private val repository: PlantTaskRepository
) {
    operator fun invoke(plantId: Int, withinDays: Int = 7): Flow<NextTask?> {
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return repository.observePendingTasks(plantId).map { tasks ->
            if (tasks.isEmpty()) return@map null

            val overdueTasks = tasks.filter { it.dueAt < todayStart }.sortedBy { it.dueAt }
            if (overdueTasks.isNotEmpty()) {
                val oldest = overdueTasks.first()
                val diffDays = ((todayStart - oldest.dueAt) / (24 * 3600 * 1000L)).toInt()
                return@map NextTask(oldest, isOverdue = true, daysUntil = -diffDays)
            }

            val windowEnd = todayStart + (withinDays * 24 * 3600 * 1000L)
            val upcomingTasks = tasks.filter { it.dueAt in todayStart..windowEnd }.sortedBy { it.dueAt }
            if (upcomingTasks.isNotEmpty()) {
                val next = upcomingTasks.first()
                val diffDays = ((next.dueAt - todayStart) / (24 * 3600 * 1000L)).toInt()
                return@map NextTask(next, isOverdue = false, daysUntil = diffDays)
            }

            val firstPending = tasks.sortedBy { it.dueAt }.firstOrNull() ?: return@map null
            val diffDays = ((firstPending.dueAt - todayStart) / (24 * 3600 * 1000L)).toInt()
            NextTask(firstPending, isOverdue = diffDays < 0, daysUntil = diffDays)
        }
    }
}

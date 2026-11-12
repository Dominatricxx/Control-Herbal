package com.example.controlherbal.backend.domain.usecase

import com.example.controlherbal.backend.domain.repository.PlantTaskRepository

class CompleteTaskUseCase(
    private val repository: PlantTaskRepository,
    private val scheduler: TaskReminderScheduler = NoOpTaskReminderScheduler()
) {
    suspend operator fun invoke(taskId: Long, completedAt: Long = System.currentTimeMillis()) {
        repository.completeTask(taskId, completedAt)
        scheduler.cancelReminder(taskId)
    }
}

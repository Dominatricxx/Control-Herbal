package com.example.controlherbal.domain.usecase

import com.example.controlherbal.domain.repository.PlantTaskRepository

class CompleteTaskUseCase(
    private val repository: PlantTaskRepository,
    private val scheduler: TaskReminderScheduler = NoOpTaskReminderScheduler()
) {
    suspend operator fun invoke(taskId: Long, completedAt: Long = System.currentTimeMillis()) {
        repository.completeTask(taskId, completedAt)
        scheduler.cancelReminder(taskId)
    }
}

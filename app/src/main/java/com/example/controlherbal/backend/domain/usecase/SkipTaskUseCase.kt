package com.example.controlherbal.backend.domain.usecase

import com.example.controlherbal.backend.domain.repository.PlantTaskRepository

class SkipTaskUseCase(
    private val repository: PlantTaskRepository,
    private val scheduler: TaskReminderScheduler = NoOpTaskReminderScheduler()
) {
    suspend operator fun invoke(taskId: Long) {
        repository.skipTask(taskId)
        scheduler.cancelReminder(taskId)
    }
}

package com.example.controlherbal.domain.usecase

import com.example.controlherbal.domain.repository.PlantTaskRepository

class SkipTaskUseCase(
    private val repository: PlantTaskRepository,
    private val scheduler: TaskReminderScheduler = NoOpTaskReminderScheduler()
) {
    suspend operator fun invoke(taskId: Long) {
        repository.skipTask(taskId)
        scheduler.cancelReminder(taskId)
    }
}

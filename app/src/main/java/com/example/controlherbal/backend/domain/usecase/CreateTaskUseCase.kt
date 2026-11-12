package com.example.controlherbal.backend.domain.usecase

import com.example.controlherbal.database.entity.PlantTask
import com.example.controlherbal.backend.domain.repository.PlantTaskRepository

class CreateTaskUseCase(
    private val repository: PlantTaskRepository,
    private val scheduler: TaskReminderScheduler = NoOpTaskReminderScheduler()
) {
    suspend operator fun invoke(task: PlantTask): Long {
        val id = repository.insertTask(task)
        if (task.status.name == "PENDING") {
            scheduler.scheduleReminder(task.copy(id = id))
        }
        return id
    }
}

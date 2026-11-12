package com.example.controlherbal.backend.domain.usecase

import com.example.controlherbal.database.entity.PlantTask

interface TaskReminderScheduler {
    fun scheduleReminder(task: PlantTask)
    fun cancelReminder(taskId: Long)
}

class NoOpTaskReminderScheduler : TaskReminderScheduler {
    override fun scheduleReminder(task: PlantTask) {}
    override fun cancelReminder(taskId: Long) {}
}

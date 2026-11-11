package com.example.controlherbal.domain.usecase

import com.example.controlherbal.data.database.PlantTask

interface TaskReminderScheduler {
    fun scheduleReminder(task: PlantTask)
    fun cancelReminder(taskId: Long)
}

class NoOpTaskReminderScheduler : TaskReminderScheduler {
    override fun scheduleReminder(task: PlantTask) {}
    override fun cancelReminder(taskId: Long) {}
}

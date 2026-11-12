package com.example.controlherbal.backend.domain.usecase

import com.example.controlherbal.database.entity.PlantTask
import com.example.controlherbal.backend.domain.repository.PlantTaskRepository
import kotlinx.coroutines.flow.Flow

class ObserveTasksForRangeUseCase(
    private val repository: PlantTaskRepository
) {
    operator fun invoke(plantId: Int, startMillis: Long, endMillis: Long): Flow<List<PlantTask>> {
        return repository.observeTasksForRange(plantId, startMillis, endMillis)
    }
}

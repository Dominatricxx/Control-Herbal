package com.example.controlherbal.domain.usecase

import com.example.controlherbal.data.database.PlantTask
import com.example.controlherbal.domain.repository.PlantTaskRepository
import kotlinx.coroutines.flow.Flow

class ObserveTasksForRangeUseCase(
    private val repository: PlantTaskRepository
) {
    operator fun invoke(plantId: Int, startMillis: Long, endMillis: Long): Flow<List<PlantTask>> {
        return repository.observeTasksForRange(plantId, startMillis, endMillis)
    }
}

package com.example.controlherbal.backend.domain.usecase


import com.example.controlherbal.database.Plant
import com.example.controlherbal.backend.domain.repository.PlantRepository

/**
 * GetSelectedPlantUseCase: Encapsula la obtención de la planta actualmente seleccionada por el usuario.
 */
class GetSelectedPlantUseCase(
    private val plantRepository: PlantRepository
) {
    suspend operator fun invoke(): Plant? {
        return plantRepository.getSelectedPlant()
    }
}

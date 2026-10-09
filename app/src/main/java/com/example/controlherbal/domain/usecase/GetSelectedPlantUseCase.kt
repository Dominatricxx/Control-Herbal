package com.example.controlherbal.domain.usecase

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.domain.repository.PlantRepository

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

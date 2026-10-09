package com.example.controlherbal.ui.viewmodel

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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.repository.PlantRepository
import com.example.controlherbal.domain.repository.SensorDataRepository
import com.example.controlherbal.domain.usecase.GetSensorHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComparisonUiState(
    val plant1: Plant? = null,
    val plant2: Plant? = null,
    val readingsP1: List<SensorReading> = emptyList(),
    val readingsP2: List<SensorReading> = emptyList(),
    val allPlants: List<Plant> = emptyList()
)

/**
 * ComparisonViewModel: Gestiona el estado de comparación entre dos plantas.
 */
class ComparisonViewModel(
    private val plantRepository: PlantRepository,
    sensorDataRepository: SensorDataRepository
) : ViewModel() {

    private val getSensorHistoryUseCase = GetSensorHistoryUseCase(sensorDataRepository)

    private val _uiState = MutableStateFlow(ComparisonUiState())
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    fun loadAllPlants() {
        viewModelScope.launch {
            val plants = plantRepository.getAllPlants()
            _uiState.value = _uiState.value.copy(allPlants = plants)
        }
    }

    fun selectPlantForSlot(plant: Plant, slot: Int) {
        viewModelScope.launch {
            val readings = getSensorHistoryUseCase(plant.id, 2000)
            if (slot == 1) {
                _uiState.value = _uiState.value.copy(plant1 = plant, readingsP1 = readings)
            } else {
                _uiState.value = _uiState.value.copy(plant2 = plant, readingsP2 = readings)
            }
        }
    }
}

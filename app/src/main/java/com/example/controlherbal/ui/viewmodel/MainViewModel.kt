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
import com.example.controlherbal.domain.usecase.ExecuteWateringUseCase
import com.example.controlherbal.domain.usecase.GetSelectedPlantUseCase
import com.example.controlherbal.domain.usecase.GetSensorHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Success(
        val plant: Plant?,
        val plantCount: Int,
        val readings: List<SensorReading>,
        val lastReading: SensorReading?
    ) : MainUiState
    data class Error(val message: String) : MainUiState
}

/**
 * MainViewModel: Gestiona el estado de UI para la pantalla principal (MVI/MVVM Clean Architecture).
 */
class MainViewModel(
    private val plantRepository: PlantRepository,
    private val sensorDataRepository: SensorDataRepository
) : ViewModel() {

    private val getSelectedPlantUseCase = GetSelectedPlantUseCase(plantRepository)
    private val getSensorHistoryUseCase = GetSensorHistoryUseCase(sensorDataRepository)
    private val executeWateringUseCase = ExecuteWateringUseCase(sensorDataRepository)

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                val plant = getSelectedPlantUseCase()
                val count = plantRepository.getPlantCount()
                val readings = if (plant != null) getSensorHistoryUseCase(plant.id, 2000) else emptyList()
                val last = readings.lastOrNull()

                _uiState.value = MainUiState.Success(
                    plant = plant,
                    plantCount = count,
                    readings = readings,
                    lastReading = last
                )
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.localizedMessage ?: "Error al cargar datos")
            }
        }
    }

    fun executeWatering(seconds: Int, issuerUid: String?, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val result = executeWateringUseCase(seconds, issuerUid)
            onResult(result)
        }
    }

    fun selectPlant(plant: Plant) {
        viewModelScope.launch {
            plantRepository.selectPlant(plant)
            loadData()
        }
    }
}

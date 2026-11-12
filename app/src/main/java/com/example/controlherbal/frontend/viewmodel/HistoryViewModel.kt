package com.example.controlherbal.frontend.viewmodel

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.frontend.activities.main.*
import com.example.controlherbal.frontend.activities.auth.*
import com.example.controlherbal.frontend.activities.privacy.*
import com.example.controlherbal.frontend.activities.plant.*
import com.example.controlherbal.frontend.style.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.backend.domain.repository.PlantRepository
import com.example.controlherbal.backend.domain.repository.SensorDataRepository
import com.example.controlherbal.backend.domain.usecase.GetSelectedPlantUseCase
import com.example.controlherbal.backend.domain.usecase.GetSensorHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(
        val plant: Plant?,
        val readings: List<SensorReading>,
        val historyType: String
    ) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

/**
 * HistoryViewModel: Encapsula el estado y filtrado del historial de lectura de sensores.
 */
class HistoryViewModel(
    private val plantRepository: PlantRepository,
    sensorDataRepository: SensorDataRepository
) : ViewModel() {

    private val getSelectedPlantUseCase = GetSelectedPlantUseCase(plantRepository)
    private val getSensorHistoryUseCase = GetSensorHistoryUseCase(sensorDataRepository)

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun loadHistory(historyType: String) {
        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading
            try {
                val plant = getSelectedPlantUseCase()
                val readings = if (plant != null) getSensorHistoryUseCase(plant.id, 2000) else emptyList()
                _uiState.value = HistoryUiState.Success(plant, readings, historyType)
            } catch (e: Exception) {
                _uiState.value = HistoryUiState.Error(e.localizedMessage ?: "Error al cargar historial")
            }
        }
    }
}

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

    fun updateLiveMetrics(temp: Double, hum: Double, light: Double, soil: Double, irh: Double, seq: Double, somb: Double) {
        val currentState = _uiState.value
        if (currentState is MainUiState.Success) {
            val plantId = currentState.plant?.id ?: 0
            val newReading = SensorReading(
                timestamp = System.currentTimeMillis(),
                plantId = plantId,
                temperature = temp,
                humidity = hum,
                light = light,
                soilMoisture = soil,
                irh = irh,
                seq = seq,
                somb = somb,
                action = "FirebaseSync"
            )
            _uiState.value = currentState.copy(
                readings = currentState.readings + newReading,
                lastReading = newReading
            )
        }
    }
}

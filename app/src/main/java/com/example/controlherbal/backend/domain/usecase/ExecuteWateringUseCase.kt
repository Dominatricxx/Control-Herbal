package com.example.controlherbal.backend.domain.usecase


import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.backend.domain.repository.SensorDataRepository

/**
 * ExecuteWateringUseCase: Valida y ejecuta una orden de riego verificando topes de seguridad.
 */
class ExecuteWateringUseCase(
    private val sensorDataRepository: SensorDataRepository
) {
    suspend operator fun invoke(seconds: Int, issuerUid: String?): Result<Boolean> {
        if (seconds !in 1..AppConstants.MAX_WATERING_SECONDS) {
            return Result.failure(IllegalArgumentException("La duración del riego debe estar entre 1 y ${AppConstants.MAX_WATERING_SECONDS} segundos."))
        }
        val success = sensorDataRepository.sendWateringCommand(seconds, issuerUid)
        return if (success) {
            Result.success(true)
        } else {
            Result.failure(RuntimeException("No se pudo enviar la orden de riego. Verifica tus permisos o la conexión."))
        }
    }
}

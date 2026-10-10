package com.example.controlherbal.domain.repository

import com.example.controlherbal.data.database.entity.UserEntity

/**
 * UserRepository: Contrato abstracto para la gestión de usuarios, registro con clave hasheada y perfil.
 */
interface UserRepository {
    suspend fun registerUser(
        userId: String,
        nombre: String,
        apPaterno: String,
        apMaterno: String?,
        correo: String,
        plainPassword: String?
    ): UserEntity

    suspend fun getUserById(userId: String): UserEntity?
    suspend fun syncUserProfileToCloud(user: UserEntity): Boolean
}

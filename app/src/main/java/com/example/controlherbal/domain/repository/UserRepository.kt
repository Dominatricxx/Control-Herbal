package com.example.controlherbal.domain.repository

import com.example.controlherbal.data.database.entity.UserEntity

/**
 * Perfil de usuario local + sincronización a `/users/{uid}`.
 * Las credenciales viven solo en Firebase Auth: aquí nunca se persisten contraseñas.
 */
interface UserRepository {
    suspend fun saveVerifiedProfile(
        firstName: String?,
        paternalSurname: String?,
        maternalSurname: String?,
        fallbackDisplayName: String? = null,
    ): ProfileSyncResult

    suspend fun getUserById(userId: String): UserEntity?
}

data class ProfileSyncResult(
    val profile: UserEntity,
    val cloudSynced: Boolean,
)

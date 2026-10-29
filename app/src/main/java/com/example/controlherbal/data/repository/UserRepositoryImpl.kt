package com.example.controlherbal.data.repository

import android.content.Context
import com.example.controlherbal.common.auth.awaitResult
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.data.database.dao.UserDao
import com.example.controlherbal.data.database.entity.UserEntity
import com.example.controlherbal.data.sync.ProfileSyncWorker
import com.example.controlherbal.domain.repository.ProfileSyncResult
import com.example.controlherbal.domain.repository.UserRepository
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepositoryImpl(
    context: Context,
    private val userDao: UserDao,
) : UserRepository {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "UserRepositoryImpl"
    }

    override suspend fun saveVerifiedProfile(
        firstName: String?,
        paternalSurname: String?,
        maternalSurname: String?,
        fallbackDisplayName: String?,
    ): ProfileSyncResult = withContext(Dispatchers.IO) {
        val userId = AuthManager.uid()
            ?: throw IllegalStateException("No se puede guardar un perfil sin una sesión autenticada.")
        if (!AuthManager.isEmailVerified()) {
            throw IllegalStateException("No se puede sincronizar un perfil antes de verificar el correo.")
        }
        val correo = AuthManager.email()
            ?.takeIf { it.length <= 254 && SecurityUtils.isValidEmailDomain(it) }
            ?: throw IllegalStateException("La sesión autenticada no tiene un correo válido.")
        val existing = userDao.getUserById(userId)
        val fallbackParts = SecurityUtils.sanitizeText(
            fallbackDisplayName ?: AuthManager.displayName(),
            240,
        ).split(' ').filter(String::isNotBlank)
        val cleanName = SecurityUtils.sanitizeText(
            firstName?.takeIf(String::isNotBlank) ?: existing?.nombre ?: fallbackParts.firstOrNull(),
            80,
        )
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("El nombre del perfil no puede estar vacío.")
        }
        val cleanPaternalName = SecurityUtils.sanitizeText(
            paternalSurname?.takeIf(String::isNotBlank)
                ?: existing?.apPaterno?.takeIf { it != "—" }
                ?: fallbackParts.drop(1).joinToString(" ").ifBlank { "—" },
            80,
        ).ifBlank { "—" }
        val cleanMaternalName = SecurityUtils.sanitizeText(
            maternalSurname?.takeIf(String::isNotBlank) ?: existing?.apMaterno,
            80,
        )
        val user = UserEntity(
            userId = userId,
            nombre = cleanName,
            apPaterno = cleanPaternalName,
            apMaterno = cleanMaternalName.ifBlank { null },
            correo = correo,
            fechaRegistro = existing?.fechaRegistro ?: System.currentTimeMillis(),
            passwordHash = null,
            passwordSalt = null,
            fotoUrl = existing?.fotoUrl,
            mfaEnabled = !AuthManager.needsMfaEnrollment(),
            ultimoAcceso = System.currentTimeMillis(),
        )
        userDao.insertUser(user)
        val synced = syncVerifiedProfileToCloud(user)
        if (!synced) ProfileSyncWorker.enqueue(appContext, userId)
        ProfileSyncResult(user, synced)
    }

    override suspend fun getUserById(userId: String): UserEntity? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)
    }

    suspend fun syncVerifiedProfileToCloud(user: UserEntity): Boolean {
        if (AuthManager.uid() != user.userId || !AuthManager.isEmailVerified() || AuthManager.email() != user.correo) {
            SecureLogger.w(TAG, "Se rechazó sincronización de perfil fuera de la sesión verificada")
            return false
        }
        val cloudProfile = mapOf(
            "nombre" to user.nombre,
            "apPaterno" to user.apPaterno,
            "apMaterno" to (user.apMaterno ?: ""),
            "correo" to user.correo,
            "fechaRegistro" to user.fechaRegistro,
        )
        val result = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference("users")
            .child(user.userId)
            .setValue(cloudProfile)
            .awaitResult()
        if (result.isFailure) {
            SecureLogger.e(TAG, "Error sincronizando perfil: ${result.exceptionOrNull()?.javaClass?.simpleName}")
        }
        return result.isSuccess
    }
}

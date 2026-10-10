package com.example.controlherbal.data.repository

import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.data.database.dao.UserDao
import com.example.controlherbal.data.database.entity.UserEntity
import com.example.controlherbal.domain.repository.UserRepository
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * UserRepositoryImpl: Implementación del repositorio de usuarios con hasheo PBKDF2 y sincronización.
 */
class UserRepositoryImpl(
    private val userDao: UserDao
) : UserRepository {

    companion object {
        private const val TAG = "UserRepositoryImpl"
    }

    override suspend fun registerUser(
        userId: String,
        nombre: String,
        apPaterno: String,
        apMaterno: String?,
        correo: String,
        plainPassword: String?
    ): UserEntity = withContext(Dispatchers.IO) {
        val saltBytes = SecurityUtils.generateSalt()
        val saltHex = SecurityUtils.bytesToHex(saltBytes)
        val hashHex = if (!plainPassword.isNullOrEmpty()) {
            SecurityUtils.hashPasswordWithPbkdf2(plainPassword, saltBytes)
        } else null

        val user = UserEntity(
            userId = userId,
            nombre = SecurityUtils.sanitizeText(nombre),
            apPaterno = SecurityUtils.sanitizeText(apPaterno),
            apMaterno = apMaterno?.let { SecurityUtils.sanitizeText(it) },
            correo = SecurityUtils.sanitizeText(correo),
            fechaRegistro = System.currentTimeMillis(),
            passwordHash = hashHex,
            passwordSalt = saltHex
        )

        userDao.insertUser(user)
        syncUserProfileToCloud(user)
        user
    }

    override suspend fun getUserById(userId: String): UserEntity? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)
    }

    override suspend fun syncUserProfileToCloud(user: UserEntity): Boolean = suspendCancellableCoroutine { cont ->
        val ref = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference("users")
            .child(user.userId)

        val cloudProfile = mapOf(
            "nombre" to user.nombre,
            "apPaterno" to user.apPaterno,
            "apMaterno" to (user.apMaterno ?: ""),
            "correo" to user.correo,
            "fechaRegistro" to user.fechaRegistro
            // La contraseña NUNCA se sube a Firebase RTDB (la gestiona Firebase Auth con scrypt)
        )

        ref.setValue(cloudProfile).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                SecureLogger.e(TAG, "Error sincronizando perfil a Firebase: ${task.exception?.javaClass?.simpleName}")
            }
            if (cont.isActive) {
                cont.resume(task.isSuccessful)
            }
        }
    }
}

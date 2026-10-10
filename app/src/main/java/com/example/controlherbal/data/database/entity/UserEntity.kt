package com.example.controlherbal.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * UserEntity: Representa la tabla T1 'usuarios' en la base de datos relacional Room.
 * Las contraseñas NUNCA se guardan en texto plano: se almacena el hash PBKDF2 y su sal.
 */
@Entity(tableName = "usuarios")
data class UserEntity(
    @PrimaryKey val userId: String, // Firebase Auth UID o ID de usuario
    val nombre: String,
    val apPaterno: String,
    val apMaterno: String? = null,
    val correo: String,
    val fechaRegistro: Long = System.currentTimeMillis(),
    val passwordHash: String? = null,
    val passwordSalt: String? = null,
    val fotoUrl: String? = null,
    val mfaEnabled: Boolean = false,
    val ultimoAcceso: Long = System.currentTimeMillis()
)

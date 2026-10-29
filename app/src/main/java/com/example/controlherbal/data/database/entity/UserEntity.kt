package com.example.controlherbal.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Perfil local de identidad. Las credenciales pertenecen a Firebase Auth y no se guardan en Room.
 */
@Entity(tableName = "usuarios")
data class UserEntity(
    @PrimaryKey     val userId: String, // Firebase Auth UID
    val nombre: String,
    val apPaterno: String,
    val apMaterno: String? = null,
    val correo: String,
    val fechaRegistro: Long = System.currentTimeMillis(),
    val passwordHash: String? = null, // Campos heredados; siempre null para cuentas Firebase.
    val passwordSalt: String? = null,
    val fotoUrl: String? = null, // Dato local; no forma parte del perfil remoto.
    val mfaEnabled: Boolean = false, // Indicador local de enrolamiento, no sustituye el claim de Firebase.
    val ultimoAcceso: Long = System.currentTimeMillis() // Metadato local; no se sincroniza.
)

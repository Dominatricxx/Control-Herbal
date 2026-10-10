package com.example.controlherbal.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.controlherbal.data.database.entity.UserEntity

/**
 * UserDao: Operaciones de acceso a datos para la tabla T1 'usuarios'.
 */
@Dao
interface UserDao {

    @Query("SELECT * FROM usuarios WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM usuarios WHERE correo = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM usuarios WHERE userId = :userId")
    suspend fun deleteUser(userId: String)
}

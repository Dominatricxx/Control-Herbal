package com.example.controlherbal.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlantDao {
    @Insert
    suspend fun insert(plant: Plant): Long

    @Query("SELECT * FROM plants")
    suspend fun getAll(): List<Plant>

    @Query("SELECT * FROM plants WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedPlant(): Plant?

    @Query("UPDATE plants SET isSelected = 0")
    suspend fun deselectAll()

    @Update
    suspend fun update(plant: Plant)

    @Query("SELECT COUNT(*) FROM plants")
    suspend fun getPlantCount(): Int

    @Query("SELECT * FROM plants WHERE pendingSync = 1")
    suspend fun getPendingSyncPlants(): List<Plant>

    @androidx.room.Delete
    suspend fun delete(plant: Plant)
}

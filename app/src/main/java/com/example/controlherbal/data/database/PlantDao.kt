package com.example.controlherbal.data.database

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import androidx.room.Dao
import androidx.room.Delete
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

    @Delete
    suspend fun delete(plant: Plant)
}

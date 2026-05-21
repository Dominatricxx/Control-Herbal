package com.example.controlherbal.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorDao {
    @Insert
    suspend fun insert(reading: SensorReading)

    @Query("SELECT * FROM sensor_readings ORDER BY timestamp ASC LIMIT 2000")
    suspend fun getLast2000Asc(): List<SensorReading>

    @Query("DELETE FROM sensor_readings WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldReadings(beforeTimestamp: Long)
}
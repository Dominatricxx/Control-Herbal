package com.example.controlherbal.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SensorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: SensorReading)

    @Query("SELECT * FROM sensor_readings ORDER BY timestamp ASC LIMIT 2000")
    suspend fun getLast2000Asc(): List<SensorReading>

    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC")
    suspend fun getAllOrderByTimestampDesc(): List<SensorReading>

    @Query("SELECT * FROM sensor_readings WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getReadingsBetween(startTime: Long, endTime: Long): List<SensorReading>

    @Query("DELETE FROM sensor_readings WHERE timestamp NOT IN (SELECT timestamp FROM sensor_readings ORDER BY timestamp DESC LIMIT 3000)")
    suspend fun pruneData()

    @Query("DELETE FROM sensor_readings")
    suspend fun deleteAll()
}

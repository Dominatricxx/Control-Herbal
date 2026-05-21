package com.example.controlherbal.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_readings")
data class SensorReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,          // milliseconds
    val temperature: Double,
    val humidity: Double,
    val light: Int,
    val irh: Double,
    val seq: Double,
    val somb: Double,
    val action: String
)
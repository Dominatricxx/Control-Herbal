package com.example.controlherbal.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_readings")
data class SensorReading(
    @PrimaryKey val timestamp: Long,
    val plantId: Int,
    val temperature: Double,
    val humidity: Double,
    val light: Double,
    val soilMoisture: Double, // Nuevo campo
    val irh: Double,
    val seq: Double,
    val somb: Double,
    val action: String
)

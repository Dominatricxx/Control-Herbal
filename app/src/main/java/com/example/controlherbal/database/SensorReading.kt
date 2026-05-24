package com.example.controlherbal.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_readings")
data class SensorReading(
    @PrimaryKey val timestamp: Long,
    val plantId: Int, // Enlazado a la planta específica
    val temperature: Double,
    val humidity: Double,
    val light: Int,
    val irh: Double,
    val seq: Double,
    val somb: Double,
    val action: String
)

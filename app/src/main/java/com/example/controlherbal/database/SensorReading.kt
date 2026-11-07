package com.example.controlherbal.database

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.frontend.activities.main.*
import com.example.controlherbal.frontend.activities.auth.*
import com.example.controlherbal.frontend.activities.privacy.*
import com.example.controlherbal.frontend.activities.plant.*
import com.example.controlherbal.frontend.style.*

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

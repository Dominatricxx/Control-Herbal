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

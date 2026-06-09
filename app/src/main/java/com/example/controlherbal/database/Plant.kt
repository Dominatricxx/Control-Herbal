package com.example.controlherbal.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,
    val environment: String,
    val isSelected: Boolean = false,
    val lastWateringTime: Long = 0,
    val pendingSync: Boolean = false,
    val aiDiagnosis: String? = null,
    val aiRecommendation: String? = null
)

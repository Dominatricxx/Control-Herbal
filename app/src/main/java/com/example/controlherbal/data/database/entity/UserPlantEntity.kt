package com.example.controlherbal.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * UserPlantEntity: Representa la tabla T2 'user_plantas' relacionando usuarios y catálogo.
 * Sin nombre personalizado ni emojis.
 */
@Entity(
    tableName = "user_plantas",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlantCatalogEntity::class,
            parentColumns = ["idPlanta"],
            childColumns = ["idPlantaCatalogo"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("userId"), Index("idPlantaCatalogo")]
)
data class UserPlantEntity(
    @PrimaryKey(autoGenerate = true) val idUserPlant: Int = 0,
    val userId: String,
    val idPlantaCatalogo: Int,
    val cantidad: Int = 1,
    val fechaIngreso: Long = System.currentTimeMillis(),
    val isSelected: Boolean = false,
    val ultimoRiego: Long = 0,
    val aiDiagnosis: String? = null,
    val aiRecommendation: String? = null
)

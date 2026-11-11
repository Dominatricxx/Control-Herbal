package com.example.controlherbal.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PlantCatalogEntity: Representa la tabla T3 'plantas' (Catálogo Botánico Maestro).
 * Incluye cuidados, tips e imagenUrl para fotos reales por especie.
 */
@Entity(tableName = "plantas")
data class PlantCatalogEntity(
    @PrimaryKey(autoGenerate = true) val idPlanta: Int = 0,
    val nombreComun: String,
    val nombreCientifico: String,
    val idTipo: Int = 1,
    val nombreTipo: String,
    val idCategoria: Int = 1,
    val nombreCategoria: String,
    val idAmbiente: Int = 1,
    val tipoAmbiente: String, // Luz / Sombra / Híbrido
    val tempMin: Double = 18.0,
    val tempMax: Double = 30.0,
    val humMin: Double = 30.0,
    val humMax: Double = 70.0,
    val sueloMin: Double = 20.0,
    val sueloMax: Double = 60.0,
    val cuidados: String? = null,
    val tips: String? = null,
    val imagenUrl: String? = null
)

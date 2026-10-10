package com.example.controlherbal.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.controlherbal.data.database.entity.PlantCatalogEntity

/**
 * PlantCatalogDao: Operaciones de acceso al Catálogo Botánico Maestro T3 'plantas'.
 */
@Dao
interface PlantCatalogDao {

    @Query("SELECT * FROM plantas")
    suspend fun getAllSpecies(): List<PlantCatalogEntity>

    @Query("SELECT * FROM plantas WHERE idPlanta = :id LIMIT 1")
    suspend fun getSpeciesById(id: Int): PlantCatalogEntity?

    @Query("SELECT * FROM plantas WHERE nombreComun LIKE '%' || :query || '%' OR nombreCientifico LIKE '%' || :query || '%'")
    suspend fun searchSpecies(query: String): List<PlantCatalogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalog(species: List<PlantCatalogEntity>)
}

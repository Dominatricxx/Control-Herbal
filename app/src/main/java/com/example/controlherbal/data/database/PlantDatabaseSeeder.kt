package com.example.controlherbal.data.database

import com.example.controlherbal.common.utils.PlantCategoriesCatalog
import com.example.controlherbal.data.database.entity.PlantCatalogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PlantDatabaseSeeder: Organiza y siembra el Catálogo Botánico Maestro (Tabla 3 'plantas'),
 * correlacionando estrictamente cada planta por ID con su categoría, nombre científico y tipo de ambiente.
 */
object PlantDatabaseSeeder {

    private data class Thresholds(
        val tempMin: Double, val tempMax: Double,
        val humMin: Double, val humMax: Double,
        val sueloMin: Double, val sueloMax: Double
    )

    suspend fun seedCatalogIfEmpty(database: SensorDatabase) = withContext(Dispatchers.IO) {
        val dao = database.plantCatalogDao()
        val existing = dao.getAllSpecies()
        if (existing.isNotEmpty()) return@withContext

        val catalogList = mutableListOf<PlantCatalogEntity>()
        var plantIdCounter = 1
        var categoryIdCounter = 1

        for ((categoryKey, plants) in PlantCategoriesCatalog.categoriesMap) {
            val catId = categoryIdCounter++
            val catName = categoryKey

            for (plant in plants) {
                val t = when {
                    plant.environment.contains("Luz", ignoreCase = true) -> Thresholds(20.0, 32.0, 30.0, 65.0, 20.0, 50.0)
                    plant.environment.contains("Sombra", ignoreCase = true) -> Thresholds(16.0, 26.0, 45.0, 80.0, 35.0, 70.0)
                    else -> Thresholds(18.0, 29.0, 35.0, 75.0, 25.0, 60.0)
                }

                catalogList.add(
                    PlantCatalogEntity(
                        idPlanta = plantIdCounter++,
                        nombreComun = plant.name,
                        nombreCientifico = plant.scientificName,
                        idTipo = catId,
                        nombreTipo = plant.name,
                        idCategoria = catId,
                        nombreCategoria = catName,
                        idAmbiente = if (plant.environment.contains("Luz")) 1 else if (plant.environment.contains("Sombra")) 2 else 3,
                        tipoAmbiente = plant.environment,
                        tempMin = t.tempMin,
                        tempMax = t.tempMax,
                        humMin = t.humMin,
                        humMax = t.humMax,
                        sueloMin = t.sueloMin,
                        sueloMax = t.sueloMax
                    )
                )
            }
        }

        dao.insertCatalog(catalogList)
    }
}

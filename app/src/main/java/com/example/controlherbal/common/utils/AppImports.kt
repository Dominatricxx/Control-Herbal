package com.example.controlherbal.common.utils

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem

/**
 * AppImports: Contenedor unificado de definiciones de tipos, alias y modelo central.
 */

typealias AppSensorReading = SensorReading
typealias AppPlant = Plant
typealias AppAnalysisResult = PredictiveTheorem.AnalysisResult

/**
 * Planta Predefinida usada en diálogos y sugerencias de UI
 */
data class PredefinedPlantInfo(
    val name: String,
    val scientificName: String,
    val environment: String
)

/**
 * Categorías predefinidas de plantas de la aplicación (Sin emojis)
 */
object PlantCategoriesCatalog {

    val categoriesMap: Map<String, List<PredefinedPlantInfo>> = mapOf(
        "Flores" to listOf(
            PredefinedPlantInfo("Girasol", "Helianthus annuus", "Luz"),
            PredefinedPlantInfo("Girasol de Sombra", "Helianthus decapetalus", "Sombra"),
            PredefinedPlantInfo("Tulipán", "Tulipa", "Híbrido"),
            PredefinedPlantInfo("Rosa", "Rosa", "Luz"),
            PredefinedPlantInfo("Lavanda", "Lavandula", "Luz"),
            PredefinedPlantInfo("Caléndula", "Calendula officinalis", "Luz"),
            PredefinedPlantInfo("Orquídea", "Orchidaceae", "Sombra")
        ),
        "Hierbas y Especias" to listOf(
            PredefinedPlantInfo("Menta", "Mentha", "Sombra"),
            PredefinedPlantInfo("Albahaca", "Ocimum basilicum", "Luz"),
            PredefinedPlantInfo("Romero", "Salvia rosmarinus", "Luz"),
            PredefinedPlantInfo("Tomillo", "Thymus", "Luz"),
            PredefinedPlantInfo("Perejil", "Petroselinum crispum", "Híbrido"),
            PredefinedPlantInfo("Cilantro", "Coriandrum sativum", "Híbrido"),
            PredefinedPlantInfo("Hierbabuena", "Mentha spicata", "Híbrido")
        ),
        "Medicinales" to listOf(
            PredefinedPlantInfo("Áloe Vera", "Aloe barbadensis", "Luz"),
            PredefinedPlantInfo("Manzanilla", "Chamaemelum nobile", "Luz"),
            PredefinedPlantInfo("Diente de León", "Taraxacum officinale", "Luz"),
            PredefinedPlantInfo("Salvia", "Salvia officinalis", "Híbrido"),
            PredefinedPlantInfo("Eucalipto", "Eucalyptus", "Luz"),
            PredefinedPlantInfo("Ruda", "Ruta graveolens", "Híbrido")
        ),
        "Huerto y Frutales" to listOf(
            PredefinedPlantInfo("Tomate", "Solanum lycopersicum", "Luz"),
            PredefinedPlantInfo("Chile", "Capsicum", "Luz"),
            PredefinedPlantInfo("Limón", "Citrus limon", "Luz"),
            PredefinedPlantInfo("Aguacate", "Persea americana", "Luz"),
            PredefinedPlantInfo("Fresa", "Fragaria", "Híbrido"),
            PredefinedPlantInfo("Naranjo", "Citrus sinensis", "Luz")
        ),
        "Suculentas y Otros" to listOf(
            PredefinedPlantInfo("Nopal", "Opuntia ficus-indica", "Luz"),
            PredefinedPlantInfo("Suculenta", "Crassulaceae", "Luz"),
            PredefinedPlantInfo("Lengua de Suegra", "Sansevieria trifasciata", "Sombra"),
            PredefinedPlantInfo("Bambú", "Bambusoideae", "Sombra"),
            PredefinedPlantInfo("Helecho", "Filicopsida", "Sombra")
        )
    )
}

package ui

import logic.PredictiveTheorem
import storage.PlantRecord
import storage.PlantStorage

data class DesktopPlant(
    val name: String,
    val type: String,
    val environment: String,
    val isSelected: Boolean = false,
    val lastWateringTime: Long = 0
)

data class SensorState(
    val temp: Double = 24.0,
    val hum: Double = 50.0,
    val luz: Int = 70,
    val soil: Double = 45.0,
    val irh: Double = 0.0,
    val analysis: PredictiveTheorem.AnalysisResult? = null
)

fun savePlants(plants: List<DesktopPlant>) =
    PlantStorage.save(plants.map { PlantRecord(it.name, it.type, it.environment, it.isSelected, it.lastWateringTime) })

fun loadPlants(): List<DesktopPlant> =
    PlantStorage.load().map { DesktopPlant(it.name, it.type, it.environment, it.isSelected, it.lastWateringTime) }

data class PredefinedPlant(
    val name: String,
    val scientificName: String,
    val emoji: String,
    val environment: String
)

val plantCategories = mapOf(
    "Flores 🌸" to listOf(
        PredefinedPlant("Girasol", "Helianthus annuus", "🌻", "Luz 🌞"),
        PredefinedPlant("Girasol de Sombra", "Helianthus decapetalus", "🌻", "Sombra 🌥️"),
        PredefinedPlant("Tulipán", "Tulipa", "🌷", "Híbrido ⛅"),
        PredefinedPlant("Rosa", "Rosa", "🌹", "Luz 🌞"),
        PredefinedPlant("Lavanda", "Lavandula", "🌿", "Luz 🌞"),
        PredefinedPlant("Caléndula", "Calendula officinalis", "🧡", "Luz 🌞"),
        PredefinedPlant("Orquídea", "Orchidaceae", "🌸", "Sombra 🌥️")
    ),
    "Hierbas y Especias 🌿" to listOf(
        PredefinedPlant("Menta", "Mentha", "🍃", "Sombra 🌥️"),
        PredefinedPlant("Albahaca", "Ocimum basilicum", "🌿", "Luz 🌞"),
        PredefinedPlant("Romero", "Salvia rosmarinus", "🌿", "Luz 🌞"),
        PredefinedPlant("Tomillo", "Thymus", "🌿", "Luz 🌞"),
        PredefinedPlant("Perejil", "Petroselinum crispum", "🌿", "Híbrido ⛅"),
        PredefinedPlant("Cilantro", "Coriandrum sativum", "🌿", "Híbrido ⛅"),
        PredefinedPlant("Hierbabuena", "Mentha spicata", "🌿", "Híbrido ⛅")
    ),
    "Medicinales 💊" to listOf(
        PredefinedPlant("Áloe Vera", "Aloe barbadensis", "🌵", "Luz 🌞"),
        PredefinedPlant("Manzanilla", "Chamaemelum nobile", "🌼", "Luz 🌞"),
        PredefinedPlant("Diente de León", "Taraxacum officinale", "🌼", "Luz 🌞"),
        PredefinedPlant("Salvia", "Salvia officinalis", "🌿", "Híbrido ⛅"),
        PredefinedPlant("Eucalipto", "Eucalyptus", "🌿", "Luz 🌞"),
        PredefinedPlant("Ruda", "Ruta graveolens", "🌿", "Híbrido ⛅")
    ),
    "Huerto y Frutales 🍅" to listOf(
        PredefinedPlant("Tomate", "Solanum lycopersicum", "🍅", "Luz 🌞"),
        PredefinedPlant("Chile", "Capsicum", "🌶️", "Luz 🌞"),
        PredefinedPlant("Limón", "Citrus limon", "🍋", "Luz 🌞"),
        PredefinedPlant("Aguacate", "Persea americana", "🥑", "Luz 🌞"),
        PredefinedPlant("Fresa", "Fragaria", "🍓", "Híbrido ⛅"),
        PredefinedPlant("Naranjo", "Citrus sinensis", "🍊", "Luz 🌞")
    ),
    "Suculentas y Otros 🌵" to listOf(
        PredefinedPlant("Nopal", "Opuntia ficus-indica", "🌵", "Luz 🌞"),
        PredefinedPlant("Suculenta", "Crassulaceae", "🌵", "Luz 🌞"),
        PredefinedPlant("Lengua de Suegra", "Sansevieria trifasciata", "🌿", "Sombra 🌥️"),
        PredefinedPlant("Bambú", "Bambusoideae", "🎍", "Sombra 🌥️"),
        PredefinedPlant("Helecho", "Filicopsida", "🌿", "Sombra 🌥️")
    )
)

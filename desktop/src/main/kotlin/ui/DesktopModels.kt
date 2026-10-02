package ui

import java.io.File
import logic.PredictiveTheorem

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

private val persistenceFile = File(System.getProperty("user.home"), ".controlherbal_plants.txt")

fun savePlants(plants: List<DesktopPlant>) {
    try {
        val lines = plants.map { "${it.name}|${it.type}|${it.environment}|${it.isSelected}|${it.lastWateringTime}" }
        persistenceFile.writeText(lines.joinToString("\n"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadPlants(): List<DesktopPlant> {
    if (!persistenceFile.exists()) return emptyList()
    return try {
        persistenceFile.readLines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 4) {
                val lastWatering = if (parts.size >= 5) parts[4].toLong() else 0L
                DesktopPlant(parts[0], parts[1], parts[2], parts[3].toBoolean(), lastWatering)
            } else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

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

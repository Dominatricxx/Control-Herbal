import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import java.io.File
import logic.PredictiveTheorem
import ai.HerbalAI
import network.FirebaseService
import kotlinx.coroutines.delay

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

private fun savePlants(plants: List<DesktopPlant>) {
    try {
        val lines = plants.map { "${it.name}|${it.type}|${it.environment}|${it.isSelected}|${it.lastWateringTime}" }
        persistenceFile.writeText(lines.joinToString("\n"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun loadPlants(): List<DesktopPlant> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopDashboard() {
    val herbalAI = remember { HerbalAI() }
    val firebaseService = remember { FirebaseService() }
    
    val plants = remember { 
        val loaded = loadPlants()
        val list = mutableStateListOf<DesktopPlant>()
        list.addAll(loaded)
        list
    }
    var currentScreen by remember { mutableStateOf(if (plants.isEmpty()) "create_plant" else "dashboard") }
    var isConnected by remember { mutableStateOf(false) }
    var sensorState by remember { mutableStateOf(SensorState()) }
    
    val currentPlant = plants.find { it.isSelected }

    // Ciclo de sincronización para Desktop
    LaunchedEffect(currentPlant, isConnected) {
        if (currentPlant != null && isConnected) {
            while (true) {
                val data = firebaseService.fetchSensorData()
                if (data != null) {
                    val aiIrh = herbalAI.predictRefinedIRH(data.temp, data.hum, data.luz, data.soil)
                    val analysis = PredictiveTheorem.analyze(
                        data.temp, data.hum, data.luz, data.soil,
                        lastWateringTime = currentPlant.lastWateringTime, 
                        plantType = currentPlant.type
                    )
                    sensorState = SensorState(data.temp, data.hum, data.luz, data.soil, aiIrh, analysis)
                }
                delay(5000) // Actualizar cada 5 segundos
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2E7D32),
            primaryContainer = Color(0xFFC8E6C9),
            secondary = Color(0xFF1E88E5),
            background = Color(0xFFF5F5F5),
            surface = Color.White,
            surfaceVariant = Color(0xFFE0E0E0),
            onSurface = Color(0xFF212121)
        )
    ) {
        if (plants.isEmpty()) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F5F5)) {
                CreatePlantScreen(
                    onSave = { name, type, environment ->
                        plants.add(DesktopPlant(name, type, environment, true))
                        savePlants(plants)
                        currentScreen = "dashboard"
                    },
                    onBack = { },
                    isInitialSetup = true
                )
            }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource("logo_desktop.png"),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Control Herbal", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), style = MaterialTheme.typography.headlineMedium)
                            }
                        },
                        actions = {
                            Text("Sincronizando...", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )
                }
            ) { paddingValues ->
                Row(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFF5F5F5))) {
                    Sidebar(onNavigate = { currentScreen = it }, showPlantsItem = plants.size >= 2)
                    
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        when (currentScreen) {
                            "dashboard" -> MainContent(
                                currentPlant, 
                                sensorState, 
                                isConnected = isConnected,
                                onConnect = { isConnected = !isConnected },
                                onAddPlant = { currentScreen = "create_plant" }, 
                                onWatering = {
                                    currentPlant?.let { p ->
                                        val idx = plants.indexOf(p)
                                        if (idx != -1) {
                                            plants[idx] = p.copy(lastWateringTime = System.currentTimeMillis())
                                            savePlants(plants)
                                        }
                                    }
                                }
                            )
                            "plants" -> PlantsScreen(plants, 
                                onSelect = { plant ->
                                    plants.indices.forEach { plants[it] = plants[it].copy(isSelected = (plants[it].name == plant.name)) }
                                    savePlants(plants)
                                    currentScreen = "dashboard"
                                },
                                onAdd = { currentScreen = "create_plant" }
                            )
                            "create_plant" -> CreatePlantScreen(
                                onSave = { name, type, environment ->
                                    plants.indices.forEach { plants[it] = plants[it].copy(isSelected = false) }
                                    plants.add(DesktopPlant(name, type, environment, true))
                                    savePlants(plants)
                                    currentScreen = "dashboard"
                                },
                                onBack = { currentScreen = "dashboard" }
                            )
                            "daily" -> HistoryScreen("Diario", currentPlant, sensorState)
                            "weekly" -> HistoryScreen("Semanal", currentPlant, sensorState)
                            "monthly" -> HistoryScreen("Mensual", currentPlant, sensorState)
                            "delete" -> DeletePlantScreen(currentPlant) {
                                plants.remove(currentPlant)
                                if (plants.isNotEmpty()) {
                                    plants[0] = plants[0].copy(isSelected = true)
                                }
                                savePlants(plants)
                                currentScreen = "dashboard"
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Sidebar(onNavigate: (String) -> Unit, showPlantsItem: Boolean) {
    Surface(modifier = Modifier.width(260.dp).fillMaxHeight(), color = Color.White, shadowElevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("MENÚ", style = MaterialTheme.typography.labelLarge, color = Color.Gray, modifier = Modifier.padding(8.dp))
            SidebarItem(Icons.Filled.Dashboard, "Panel Principal") { onNavigate("dashboard") }
            if (showPlantsItem) {
                SidebarItem(Icons.Filled.Eco, "Mis plantas") { onNavigate("plants") }
            }
            SidebarItem(Icons.Default.DateRange, "Registro Diario") { onNavigate("daily") }
            SidebarItem(Icons.Default.DateRange, "Registro Semanal") { onNavigate("weekly") }
            SidebarItem(Icons.Default.DateRange, "Registro Mensual") { onNavigate("monthly") }
            
            Spacer(Modifier.weight(1f))
            
            HorizontalDivider()
            SidebarItem(Icons.Default.Delete, "Eliminar planta actual", textColor = Color.Red) { onNavigate("delete") }
        }
    }
}

@Composable
fun SidebarItem(icon: ImageVector, text: String, textColor: Color = Color.Black, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (textColor == Color.Red) Color.Red else Color(0xFF2E7D32))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = textColor)
    }
}

@Composable
fun MainContent(
    currentPlant: DesktopPlant?, 
    sensorState: SensorState, 
    isConnected: Boolean,
    onConnect: () -> Unit,
    onAddPlant: () -> Unit, 
    onWatering: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    currentPlant?.let {
                        Text(it.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text(it.type + "   |   " + it.environment, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } ?: Text("Sin planta seleccionada", style = MaterialTheme.typography.titleLarge, color = Color.Gray)
                }
                IconButton(onClick = onAddPlant, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir planta", tint = Color(0xFF2E7D32))
                }
            }
        }

        Button(
            onClick = onConnect, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color.Gray else Color(0xFF2E7D32)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(if (isConnected) "DESCONECTAR DISPOSITIVO" else "VINCULAR DISPOSITIVO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(), 
            shape = RoundedCornerShape(28.dp), 
            color = if (!isConnected) Color(0xFFE0E0E0) else if (sensorState.analysis?.urgency == "ÓPTIMA") Color(0xFFC8E6C9) else Color(0xFFFFF9C4)
        ) {
            Text(
                text = when {
                    !isConnected -> "Desincronizado"
                    currentPlant == null -> "Esperando datos..."
                    else -> "Estado: ${sensorState.analysis?.urgency ?: "Analizando..."}"
                },
                modifier = Modifier.padding(12.dp), 
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, 
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("📊 Datos actuales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricCard("Temperatura", "${sensorState.temp} °C", "🌡️", Modifier.weight(1f))
                            MetricCard("Humedad", "${sensorState.hum} %", "💧", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricCard("Humedad Tierra", "${sensorState.soil} %", "🪴", Modifier.weight(1f))
                            MetricCard("IRH (IA)", String.format("%.2f", sensorState.irh), "🧠", Modifier.weight(1f), valueColor = if (sensorState.irh > 50) Color.Red else Color(0xFF2E7D32))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onWatering, 
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("REGISTRAR RIEGO 💧", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("🌱 Predicción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Spacer(Modifier.height(16.dp))
                        Text("Riego recomendado: ${if (sensorState.analysis?.wateringRecommended == true) "SÍ" else "NO"}", 
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                        Text("Próximo riego estimado: ${String.format("%.1f", sensorState.analysis?.nextWateringHours ?: 0.0)} h", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        PredictionRow("⏰ Sequía estimada:", "${String.format("%.1f", sensorState.analysis?.seq ?: 0.0)} h")
                        PredictionRow("🌥️ Sombra necesaria:", "${String.format("%.1f", sensorState.analysis?.somb ?: 0.0)} h")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFFEEEEEE))
                        Text("📋 Acción:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(sensorState.analysis?.recommendation ?: "--", 
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth().height(350.dp).padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📈 Evolución de sensores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Spacer(Modifier.height(8.dp))
                SimpleHistoryChart()
            }
        }
    }
}

@Composable
fun CreatePlantScreen(onSave: (String, String, String) -> Unit, onBack: () -> Unit, isInitialSetup: Boolean = false) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Seleccionar Categoría...") }
    var selectedPlant by remember { mutableStateOf<PredefinedPlant?>(null) }
    var customType by remember { mutableStateOf("") }
    var selectedEnvironment by remember { mutableStateOf("Luz 🌞") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showPlantMenu by remember { mutableStateOf(false) }
    var showEnvMenu by remember { mutableStateOf(false) }

    val categories = listOf("Seleccionar Categoría...") + plantCategories.keys + listOf("Otro 🌱")
    val environments = listOf("Luz 🌞", "Sombra 🌥️", "Híbrido ⛅")

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isInitialSetup) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Atrás") }
            }
            Text("Configuración de Planta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        }

        Spacer(Modifier.height(24.dp))

        Text("Nombre de la planta", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        OutlinedTextField(
            value = name, 
            onValueChange = { name = it }, 
            modifier = Modifier.fillMaxWidth(), 
            placeholder = { Text("Ej: Mi Girasol", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            shape = RoundedCornerShape(28.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
        )

        Spacer(Modifier.height(16.dp))

        Text("Categoría", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showCategoryMenu = true }, 
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) { 
                Text(selectedCategory) 
            }
            Box(Modifier.align(Alignment.Center)) {
                DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, 
                            onClick = {
                                selectedCategory = cat
                                selectedPlant = null
                                showCategoryMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (plantCategories.containsKey(selectedCategory)) {
            Spacer(Modifier.height(16.dp))
            Text("Tipo de Planta", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showPlantMenu = true }, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)
                ) { 
                    Text(selectedPlant?.let { "${it.name} ${it.emoji}" } ?: "Seleccionar Planta...") 
                }
                Box(Modifier.align(Alignment.Center)) {
                    DropdownMenu(expanded = showPlantMenu, onDismissRequest = { showPlantMenu = false }) {
                        plantCategories[selectedCategory]?.forEach { plant ->
                            DropdownMenuItem(
                                text = { Text("${plant.name} ${plant.emoji}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, 
                                onClick = {
                                    selectedPlant = plant
                                    selectedEnvironment = plant.environment
                                    showPlantMenu = false
                                }
                            )
                        }
                    }
                }
            }
        } else if (selectedCategory == "Otro 🌱") {
            Spacer(Modifier.height(16.dp))
            Text("Tipo de planta personalizado", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            OutlinedTextField(
                value = customType, 
                onValueChange = { customType = it }, 
                modifier = Modifier.fillMaxWidth(), 
                placeholder = { Text("Ej: Planta de Interior", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                shape = RoundedCornerShape(28.dp),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("Ambiente", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (selectedPlant == null) showEnvMenu = true }, 
                modifier = Modifier.fillMaxWidth(), 
                enabled = selectedPlant == null,
                shape = RoundedCornerShape(28.dp)
            ) { 
                Text(selectedEnvironment) 
            }
            Box(Modifier.align(Alignment.Center)) {
                DropdownMenu(expanded = showEnvMenu, onDismissRequest = { showEnvMenu = false }) {
                    environments.forEach { env ->
                        DropdownMenuItem(
                            text = { Text(env, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, 
                            onClick = {
                                selectedEnvironment = env
                                showEnvMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                val typeStr = when {
                    selectedPlant != null -> {
                        val categoryName = selectedCategory.split(" ").first()
                        "Categoría: $categoryName | Tipo: ${selectedPlant!!.name} ${selectedPlant!!.emoji} (${selectedPlant!!.scientificName})"
                    }
                    selectedCategory == "Otro 🌱" -> "$customType 🌿"
                    else -> ""
                }
                if (name.isNotBlank() && typeStr.isNotBlank()) {
                    onSave(name, typeStr, selectedEnvironment)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            enabled = name.isNotBlank() && (selectedPlant != null || (selectedCategory == "Otro 🌱" && customType.isNotBlank()))
        ) {
            Text("GUARDAR PLANTA", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, emoji: String, modifier: Modifier = Modifier, valueColor: Color = Color(0xFF212121)) {
    Column(modifier = modifier) {
        Text("$emoji $title", style = MaterialTheme.typography.labelMedium, color = Color(0xFF757575))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun PredictionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF757575))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SimpleHistoryChart() {
    val points = listOf(0.2f, 0.5f, 0.3f, 0.8f, 0.6f, 0.9f, 0.4f)
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val path = Path()
        val stepX = size.width / (points.size - 1)
        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = size.height - (p * size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFF2E7D32), style = Stroke(width = 4.dp.toPx()))
    }
}

@Composable
fun PlantsScreen(plants: List<DesktopPlant>, onSelect: (DesktopPlant) -> Unit, onAdd: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Mis Plantas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        Spacer(Modifier.height(16.dp))
        plants.forEach { plant ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onSelect(plant) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = if (plant.isSelected) Color(0xFFC8E6C9) else Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Eco, contentDescription = null, tint = Color(0xFF2E7D32))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(plant.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(plant.type, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    if (plant.isSelected) {
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Check, contentDescription = "Seleccionada", tint = Color(0xFF2E7D32))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("AÑADIR PLANTA")
        }
    }
}

@Composable
fun HistoryScreen(type: String, currentPlant: DesktopPlant?, sensorState: SensorState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Registro $type", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        Spacer(Modifier.height(16.dp))

        // Tarjeta de datos promediados (Simulados con la lógica implementada)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("📊 Datos promediados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricCard("Temp. media", "${sensorState.temp} °C", "🌡️", Modifier.weight(1f))
                    MetricCard("Hum. media", "${sensorState.hum} %", "💧", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricCard("Luz media", "${sensorState.luz} %", "☀️", Modifier.weight(1f))
                    MetricCard("IRH medio", String.format("%.2f", sensorState.irh), "⚠️", Modifier.weight(1f), valueColor = Color(0xFFD32F2F))
                }
            }
        }

        // Tarjeta de la gráfica
        Card(
            modifier = Modifier.fillMaxWidth().height(400.dp).padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(currentPlant?.name ?: "Sin planta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Spacer(Modifier.height(8.dp))
                SimpleHistoryChart()
            }
        }
    }
}

@Composable
fun DeletePlantScreen(currentPlant: DesktopPlant?, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(modifier = Modifier.width(400.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Eliminar planta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("¿Estás seguro de que quieres eliminar a '${currentPlant?.name}'? Se perderán todos sus datos.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Eliminar", color = Color.White) }
                }
            }
        }
    }
}

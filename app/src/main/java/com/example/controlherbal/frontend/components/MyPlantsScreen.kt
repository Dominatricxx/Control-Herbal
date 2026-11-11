package com.example.controlherbal.frontend.components

import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.controlherbal.common.utils.PlantCategoriesCatalog
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun getPlantPhotoForCategory(category: String?, plantName: String?): String {
    for ((_, plants) in PlantCategoriesCatalog.categoriesMap) {
        val match = plants.find { it.name.equals(plantName, ignoreCase = true) }
        val url = match?.imagenUrl
        if (!url.isNullOrBlank()) return url
    }

    return when (category?.lowercase()) {
        "flores" -> "https://upload.wikimedia.org/wikipedia/commons/7/7b/Red_rose_transparent.png"
        "hierbas y especias", "hierbas", "especias" -> "https://upload.wikimedia.org/wikipedia/commons/8/87/Basil_leaves_transparent.png"
        "medicinales" -> "https://upload.wikimedia.org/wikipedia/commons/9/91/Aloe_Vera_transparent.png"
        "huerto y frutales", "huerto", "frutales" -> "https://upload.wikimedia.org/wikipedia/commons/8/89/Tomato_transparent.png"
        "suculentas y otros", "suculentas" -> "https://upload.wikimedia.org/wikipedia/commons/5/5e/Succulent_transparent.png"
        else -> "https://upload.wikimedia.org/wikipedia/commons/7/7b/Red_rose_transparent.png"
    }
}

/**
 * Extracted plant item fields parsed from database Plant.type
 */
data class DisplayPlantDetails(
    val plant: Plant,
    val category: String,
    val typeName: String,
    val scientificName: String,
    val environment: String
)

fun parsePlantForDisplay(plant: Plant): DisplayPlantDetails {
    val rawType = plant.type
    var category = "FLORES"
    var typeName = plant.name
    var scientificName = "Especie botánica"

    if (!rawType.isNullOrBlank()) {
        if (rawType.contains("Categoría:")) {
            val parts = rawType.split("|")
            category = parts.getOrNull(0)?.substringAfter("Categoría:")?.trim()?.uppercase() ?: "FLORES"
            val typePart = parts.getOrNull(1)?.substringAfter("Tipo:")?.trim() ?: rawType

            if (typePart.contains("(") && typePart.contains(")")) {
                typeName = typePart.substringBefore("(").trim()
                scientificName = typePart.substringAfter("(").substringBefore(")").trim()
            } else {
                typeName = typePart
            }
        } else if (rawType.contains("(") && rawType.contains(")")) {
            typeName = rawType.substringBefore("(").trim()
            scientificName = rawType.substringAfter("(").substringBefore(")").trim()
        } else {
            typeName = rawType
        }
    }

    return DisplayPlantDetails(
        plant = plant,
        category = category,
        typeName = typeName,
        scientificName = scientificName,
        environment = plant.environment ?: "Luz 🌞"
    )
}

fun getLocalPlantDrawableId(context: Context, scientificName: String?): Int {
    if (scientificName.isNullOrBlank()) return 0
    val sanitized = scientificName.lowercase().replace(" ", "_").replace("[", "").replace("]", "")
    val resName = "plant_$sanitized"
    return try {
        context.resources.getIdentifier(resName, "drawable", context.packageName)
    } catch (e: Exception) {
        0
    }
}

enum class SortOrderOption { NEWEST, OLDEST, NAME_AZ }

/**
 * Production-ready MyPlantsScreen matching the exact design mockup.
 * Displays user's registered plants with real-time text search, filtering modal,
 * and auto-populated botanical species data.
 */
@Composable
fun MyPlantsScreen(
    onAddPlantClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { SensorDatabase.getInstance(context) }

    var rawPlantsList by remember { mutableStateOf<List<Plant>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    var selectedSortOrder by remember { mutableStateOf(SortOrderOption.NEWEST) }
    var selectedCategoryFilter by remember { mutableStateOf("TODAS") }
    var selectedEnvFilter by remember { mutableStateOf("TODOS") }

    // Load registered plants from Room DB
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val plants = db.plantDao().getAll()
            if (plants.isEmpty()) {
                // Seed sample plant if database is empty so user immediately sees registered cards
                val defaultPlant = Plant(
                    name = "Áloe Vera",
                    type = "Categoría: Medicinales | Tipo: Áloe Vera (Aloe barbadensis)",
                    environment = "Luz 🌞",
                    isSelected = true
                )
                db.plantDao().insert(defaultPlant)
            }
            rawPlantsList = db.plantDao().getAll()
        }
    }

    val displayPlants = remember(rawPlantsList, searchQuery, selectedSortOrder, selectedCategoryFilter, selectedEnvFilter) {
        var list = rawPlantsList.map { parsePlantForDisplay(it) }

        // Filter by text search (custom name or scientific name)
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.plant.name.lowercase().contains(q) ||
                        it.scientificName.lowercase().contains(q) ||
                        it.typeName.lowercase().contains(q) ||
                        it.category.lowercase().contains(q)
            }
        }

        // Filter by category
        if (selectedCategoryFilter != "TODAS") {
            list = list.filter { it.category.contains(selectedCategoryFilter, ignoreCase = true) }
        }

        // Filter by environment
        if (selectedEnvFilter != "TODOS") {
            list = list.filter { it.environment.contains(selectedEnvFilter, ignoreCase = true) }
        }

        // Sort order
        when (selectedSortOrder) {
            SortOrderOption.NEWEST -> list.sortedByDescending { it.plant.id }
            SortOrderOption.OLDEST -> list.sortedBy { it.plant.id }
            SortOrderOption.NAME_AZ -> list.sortedBy { it.plant.name.lowercase() }
        }
    }

    val totalRegistered = rawPlantsList.size
    val countText = if (totalRegistered == 1) "1 planta registrada" else "$totalRegistered plantas registradas"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2F2F31))
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP HEADER: Title "Mis plantas", Subtext "[N] plantas registradas", Search & Filter Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mis plantas",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = countText,
                        fontSize = 14.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Compare plants button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38383B))
                            .clickable {
                                context.startActivity(android.content.Intent(context, com.example.controlherbal.frontend.activities.plant.ComparisonActivity::class.java))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Comparar plantas",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Search toggle button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isSearchActive) Color(0xFF288B46) else Color(0xFF38383B))
                            .clickable { isSearchActive = !isSearchActive },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Filter modal button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selectedCategoryFilter != "TODAS" || selectedEnvFilter != "TODOS") Color(0xFF288B46) else Color(0xFF38383B))
                            .clickable { showFilterDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtrar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Real-time Text Search Bar (if search active)
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por nombre o nombre científico...", color = Color(0xFFCCCCCC), fontSize = 14.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF252527),
                        unfocusedContainerColor = Color(0xFF252527),
                        focusedBorderColor = Color(0xFF81C784),
                        unfocusedBorderColor = Color(0xFF3A3A3C)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. REGISTERED PLANT CARDS LIST
            if (displayPlants.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No se encontraron plantas que coincidan." else "No tienes plantas registradas aún.",
                        color = Color(0xFF888888),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(displayPlants, key = { it.plant.id }) { item ->
                        RegisteredPlantCard(
                            item = item,
                            onSelectPlant = { selectedPlant ->
                                scope.launch(Dispatchers.IO) {
                                    db.plantDao().deselectAll()
                                    db.plantDao().update(selectedPlant.copy(isSelected = true))
                                    rawPlantsList = db.plantDao().getAll()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 3. FILTER MODAL DIALOG
    if (showFilterDialog) {
        PlantFilterModalDialog(
            currentSortOrder = selectedSortOrder,
            currentCategory = selectedCategoryFilter,
            currentEnv = selectedEnvFilter,
            onDismiss = { showFilterDialog = false },
            onApply = { sort, cat, env ->
                selectedSortOrder = sort
                selectedCategoryFilter = cat
                selectedEnvFilter = env
                showFilterDialog = false
            }
        )
    }
}

/**
 * Plant presentation card matching the exact design mockup.
 */
@Composable
fun RegisteredPlantCard(
    item: DisplayPlantDetails,
    onSelectPlant: (Plant) -> Unit
) {
    val context = LocalContext.current
    val plant = item.plant

    val validLocalDrawableId = remember(item.scientificName, plant.name) {
        getLocalPlantDrawableId(context, item.scientificName)
    }

    val categoryPhotoUrl: String = remember(item.category, item.typeName) {
        getPlantPhotoForCategory(item.category, item.typeName)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectPlant(plant) }
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Photo Box ("Foto de la planta") with dashed/subtle border styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E1E20))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF444448),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (validLocalDrawableId != 0) {
                    Image(
                        painter = painterResource(id = validLocalDrawableId),
                        contentDescription = "Foto de ${plant.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                } else if (categoryPhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = categoryPhotoUrl,
                        contentDescription = "Foto de ${plant.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Foto de la planta",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Foto de la planta",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Category, Custom Plant Name, Scientific Name
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "[${item.category}]",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFA5D6A7)
                )

                Text(
                    text = plant.name,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "[${item.scientificName}]",
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = Color(0xFFCCCCCC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Divider Line
            HorizontalDivider(
                color = Color(0xFF38383B),
                thickness = 1.dp
            )

            // Bottom Row: Tipo & Environment Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "TIPO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFFAAAAAA)
                    )
                    Text(
                        text = "[${item.typeName}]",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                // Environment Circle Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38383B)),
                    contentAlignment = Alignment.Center
                ) {
                    val envLower = item.environment.lowercase()
                    when {
                        envLower.contains("luz") || envLower.contains("sol") -> {
                            Icon(
                                imageVector = HerbalIcons.Sun,
                                contentDescription = "Luz / Sol",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        envLower.contains("sombra") -> {
                            Text("🌥️", fontSize = 16.sp)
                        }
                        else -> {
                            Text("⛅", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filter modal dialog to filter plants by registration date, category, type, or environment.
 */
@Composable
fun PlantFilterModalDialog(
    currentSortOrder: SortOrderOption,
    currentCategory: String,
    currentEnv: String,
    onDismiss: () -> Unit,
    onApply: (sort: SortOrderOption, category: String, environment: String) -> Unit
) {
    var tempSort by remember { mutableStateOf(currentSortOrder) }
    var tempCategory by remember { mutableStateOf(currentCategory) }
    var tempEnv by remember { mutableStateOf(currentEnv) }

    val categoriesOptions = listOf("TODAS", "FLORES", "HIERBAS Y ESPECIAS", "MEDICINALES", "HUERTO Y FRUTALES", "SUCULENTAS Y OTROS")
    val envOptions = listOf("TODOS", "Luz", "Sombra", "Híbrido")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252527),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Filtrar Mis Plantas",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Sort Order
                Text("Ordenar por Fecha de Registro:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = if (tempSort == SortOrderOption.NEWEST) Color(0xFF288B46) else Color(0xFF38383B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { tempSort = SortOrderOption.NEWEST }
                    ) {
                        Text("Más recientes", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Surface(
                        color = if (tempSort == SortOrderOption.OLDEST) Color(0xFF288B46) else Color(0xFF38383B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { tempSort = SortOrderOption.OLDEST }
                    ) {
                        Text("Más antiguas", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                // 2. Category Filter
                Text("Filtrar por Categoría:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoriesOptions.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { cat ->
                                val isSelected = cat == tempCategory
                                Surface(
                                    color = if (isSelected) Color(0xFF288B46) else Color(0xFF38383B),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { tempCategory = cat }
                                ) {
                                    Text(
                                        text = cat,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Environment Filter
                Text("Filtrar por Ambiente:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    envOptions.forEach { env ->
                        val isSelected = env == tempEnv
                        Surface(
                            color = if (isSelected) Color(0xFF288B46) else Color(0xFF38383B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { tempEnv = env }
                        ) {
                            Text(
                                text = env,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(tempSort, tempCategory, tempEnv) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF288B46))
            ) {
                Text("Aplicar Filtros", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Restablecer", color = Color(0xFFCCCCCC))
            }
        }
    )
}

package com.example.controlherbal.frontend.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
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
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DisplayPlantDetails(
    val plant: Plant,
    val category: String,
    val typeName: String,
    val scientificName: String,
    val environment: String
)

fun parseDisplayPlantDetails(plant: Plant): DisplayPlantDetails {
    val rawType = plant.type
    var category = "FLORES"
    var typeName = "Planta"
    var scientificName = plant.name

    if (rawType.isNotBlank()) {
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

enum class SortOrderOption {
    NEWEST, OLDEST, NAME_AZ
}

/**
 * Production-ready MyPlantsScreen matching exact mockup aesthetics.
 * Supports toggle button between full-width vertical list view and 2-column grid view.
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
    var isGridView by remember { mutableStateOf(false) }

    var selectedSortOrder by remember { mutableStateOf(SortOrderOption.NEWEST) }
    var selectedCategoryFilter by remember { mutableStateOf("TODAS") }
    var selectedEnvFilter by remember { mutableStateOf("TODOS") }

    // Load registered plants from Room DB
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val plants = db.plantDao().getAll()
            if (plants.isEmpty()) {
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
        var list = rawPlantsList.map { parseDisplayPlantDetails(it) }

        // Search query
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.plant.name.lowercase().contains(q) || it.scientificName.lowercase().contains(q) || it.typeName.lowercase().contains(q)
            }
        }

        // Category filter
        if (selectedCategoryFilter != "TODAS") {
            list = list.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
        }

        // Environment filter
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
            // 1. TOP HEADER: Title "Mis plantas", Subtext "[N] plantas registradas", View Mode Toggle, Search & Filter
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Grid / List view mode toggle button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38383B))
                            .clickable { isGridView = !isGridView },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Cambiar vista",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

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

            // 2. REGISTERED PLANT CARDS LIST OR GRID
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
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayPlants, key = { it.plant.id }) { item ->
                            RegisteredPlantGridCard(
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
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
 * Full-width Vertical Plant Card matching exact mockup aesthetics.
 */
@Composable
fun RegisteredPlantCard(
    item: DisplayPlantDetails,
    onSelectPlant: (Plant) -> Unit
) {
    val context = LocalContext.current
    val drawableId = getLocalPlantDrawableId(context, item.scientificName)
    val isSelected = item.plant.isSelected

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
        shape = RoundedCornerShape(24.dp),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF288B46)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectPlant(item.plant) }
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Photo Placeholder Box with Camera icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (drawableId != 0) {
                    Image(
                        painter = painterResource(id = drawableId),
                        contentDescription = item.typeName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Foto",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Foto de la planta",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Plant Details Column
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "[${item.category}]",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCCCCCC),
                    maxLines = 1
                )
                Text(
                    text = "[${item.plant.name}]",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "[${item.scientificName}]",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    color = Color(0xFFCCCCCC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = Color(0xFF38383B), thickness = 1.dp)

            // Bottom Row: TIPO + Environment Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TIPO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCCCCC)
                    )
                    Text(
                        text = "[${item.typeName}]",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38383B)),
                    contentAlignment = Alignment.Center
                ) {
                    HerbalImageTileset.TilesetIcon(
                        iconName = "sol",
                        contentDescription = "Ambiente",
                        size = 20.dp
                    )
                }
            }
        }
    }
}

/**
 * Grid Card (Cuadrícula).
 */
@Composable
fun RegisteredPlantGridCard(
    item: DisplayPlantDetails,
    onSelectPlant: (Plant) -> Unit
) {
    val context = LocalContext.current
    val drawableId = getLocalPlantDrawableId(context, item.scientificName)
    val isSelected = item.plant.isSelected

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
        shape = RoundedCornerShape(24.dp),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF288B46)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectPlant(item.plant) }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (drawableId != 0) {
                    Image(
                        painter = painterResource(id = drawableId),
                        contentDescription = item.typeName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Foto",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Foto de la\nplanta",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "[${item.category}]",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCCCCCC),
                    maxLines = 1
                )
                Text(
                    text = "[${item.plant.name}]",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "[${item.scientificName}]",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 11.sp,
                    color = Color(0xFFCCCCCC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = Color(0xFF38383B), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TIPO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCCCCC)
                    )
                    Text(
                        text = "[${item.typeName}]",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38383B)),
                    contentAlignment = Alignment.Center
                ) {
                    HerbalImageTileset.TilesetIcon(
                        iconName = "sol",
                        contentDescription = "Ambiente",
                        size = 18.dp
                    )
                }
            }
        }
    }
}

@Composable
fun PlantFilterModalDialog(
    currentSortOrder: SortOrderOption,
    currentCategory: String,
    currentEnv: String,
    onDismiss: () -> Unit,
    onApply: (SortOrderOption, String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252527),
        shape = RoundedCornerShape(24.dp),
        title = { Text("Filtrar y ordenar", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Opciones de filtrado y ordenamiento de plantas.", color = Color(0xFFCCCCCC), fontSize = 14.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF288B46))
            ) {
                Text("Cerrar", color = Color.White)
            }
        }
    )
}

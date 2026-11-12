package com.example.controlherbal.frontend.components

import android.content.Intent
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.frontend.activities.plant.PlantSetupActivity
import com.example.controlherbal.frontend.helper.ChartManager
import com.github.mikephil.charting.charts.CombinedChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Production-ready PlantComparisonScreen matching the exact design mockup aesthetics.
 * Compares two plants side-by-side when >= 2 plants exist, or shows the empty state card ("Aún no puedes comparar")
 * with "+ Agregar planta" button when < 2 plants are registered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantComparisonScreen(
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { SensorDatabase.getInstance(context) }

    var allPlants by remember { mutableStateOf<List<Plant>>(emptyList()) }
    var plant1 by remember { mutableStateOf<Plant?>(null) }
    var plant2 by remember { mutableStateOf<Plant?>(null) }

    var readingsPlant1 by remember { mutableStateOf<List<SensorReading>>(emptyList()) }
    var readingsPlant2 by remember { mutableStateOf<List<SensorReading>>(emptyList()) }

    var showPlant1Dialog by remember { mutableStateOf(false) }
    var showPlant2Dialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Focus mode: 0 = Ambas, 1 = Planta 1, 2 = Planta 2
    var focusMode by remember { mutableStateOf(0) }

    // Metric Filter States (Default: Temp & Suelo = 2 of 7)
    var filterTemp by remember { mutableStateOf(true) }
    var filterHum by remember { mutableStateOf(false) }
    var filterSoil by remember { mutableStateOf(true) }
    var filterLight by remember { mutableStateOf(false) }
    var filterIrh by remember { mutableStateOf(false) }
    var filterSeq by remember { mutableStateOf(false) }
    var filterSomb by remember { mutableStateOf(false) }

    val activeSelectedCount = listOf(filterTemp, filterHum, filterSoil, filterLight, filterIrh, filterSeq, filterSomb).count { it }

    val activeMetricsText = remember(filterTemp, filterHum, filterSoil, filterLight, filterIrh, filterSeq, filterSomb) {
        val list = mutableListOf<String>()
        if (filterTemp) list.add("Temp")
        if (filterHum) list.add("Hum")
        if (filterSoil) list.add("Suelo")
        if (filterLight) list.add("Luz")
        if (filterIrh) list.add("IRH")
        if (filterSeq) list.add("Seq")
        if (filterSomb) list.add("Somb")
        if (list.isEmpty()) "Ninguna" else list.joinToString(" · ")
    }

    // Load initial plants and sensor readings
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val plants = db.plantDao().getAll()
            allPlants = plants

            if (plants.isNotEmpty()) {
                val p1 = plants.firstOrNull()
                val p2 = plants.getOrNull(1) ?: p1
                plant1 = p1
                plant2 = p2

                p1?.let { readingsPlant1 = db.sensorDao().getLast2000Asc(it.id) }
                p2?.let { readingsPlant2 = db.sensorDao().getLast2000Asc(it.id) }
            }
        }
    }

    // Update readings when plant selection changes
    LaunchedEffect(plant1?.id, plant2?.id) {
        withContext(Dispatchers.IO) {
            plant1?.let { readingsPlant1 = db.sensorDao().getLast2000Asc(it.id) }
            plant2?.let { readingsPlant2 = db.sensorDao().getLast2000Asc(it.id) }
        }
    }

    val needsMorePlants = allPlants.size < 2
    val headerSubtitle = if (needsMorePlants) "Necesitas 2 plantas registradas" else "Compara dos plantas lado a lado"

    val latest1 = readingsPlant1.lastOrNull()
    val latest2 = readingsPlant2.lastOrNull()

    // Real dynamic data formatters for Plant 1 (or "--" when no readings exist)
    val temp1Str = if (latest1 != null) String.format(Locale.US, "%.1f", latest1.temperature) else "--"
    val hum1Str = if (latest1 != null) String.format(Locale.US, "%.1f", latest1.humidity) else "--"
    val soil1Str = if (latest1 != null) String.format(Locale.US, "%.1f", latest1.soilMoisture) else "--"
    val light1Str = if (latest1 != null) String.format(Locale.US, "%.1f", latest1.light) else "--"
    val seq1Str = if (latest1 != null) String.format(Locale.US, "%.1f", latest1.seq) else "--"
    val somb1Str = if (latest1 != null) String.format(Locale.US, "%.1f", latest1.somb) else "--"
    val irh1Str = if (latest1 != null) String.format(Locale.US, "%.1f", latest1.irh) else "--"

    val statusText1 = when {
        latest1 == null -> "Sin datos"
        latest1.irh < 40.0 -> "Óptimo"
        latest1.irh < 70.0 -> "Atención"
        else -> "Riesgo"
    }
    val statusColor1 = when {
        latest1 == null -> Color(0xFF1565C0)
        latest1.irh < 40.0 -> Color(0xFF288B46)
        latest1.irh < 70.0 -> Color(0xFFB34700)
        else -> Color(0xFFC62828)
    }

    // Real dynamic data formatters for Plant 2 (or "--" when no readings exist)
    val temp2Str = if (latest2 != null) String.format(Locale.US, "%.1f", latest2.temperature) else "--"
    val hum2Str = if (latest2 != null) String.format(Locale.US, "%.1f", latest2.humidity) else "--"
    val soil2Str = if (latest2 != null) String.format(Locale.US, "%.1f", latest2.soilMoisture) else "--"
    val light2Str = if (latest2 != null) String.format(Locale.US, "%.1f", latest2.light) else "--"
    val seq2Str = if (latest2 != null) String.format(Locale.US, "%.1f", latest2.seq) else "--"
    val somb2Str = if (latest2 != null) String.format(Locale.US, "%.1f", latest2.somb) else "--"
    val irh2Str = if (latest2 != null) String.format(Locale.US, "%.1f", latest2.irh) else "--"

    val statusText2 = when {
        latest2 == null -> "Sin datos"
        latest2.irh < 40.0 -> "Óptimo"
        latest2.irh < 70.0 -> "Atención"
        else -> "Riesgo"
    }
    val statusColor2 = when {
        latest2 == null -> Color(0xFF1565C0)
        latest2.irh < 40.0 -> Color(0xFF288B46)
        latest2.irh < 70.0 -> Color(0xFFB34700)
        else -> Color(0xFFC62828)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2F2F31))
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp), // Generous bottom padding for bottom navigation bar
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP HEADER WITH RESPECTED CAMERA SAFE AREA (top = 64.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38383B))
                        .clickable { onBackClick?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Comparar plantas",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                        color = Color.White
                    )
                    Text(
                        text = headerSubtitle,
                        fontSize = 13.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }

            if (needsMorePlants) {
                // 2A. EMPTY STATE CARD WHEN < 2 PLANTS REGISTERED
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Sprout icon
                        Icon(
                            imageVector = HerbalIcons.Plant,
                            contentDescription = "Aún no puedes comparar",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Aún no puedes comparar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Registra al menos dos plantas para ver sus lecturas lado a lado.",
                                fontSize = 13.sp,
                                color = Color(0xFFCCCCCC),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                context.startActivity(Intent(context, PlantSetupActivity::class.java))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF458C4F)),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "+ Agregar planta",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // 2B. FULL COMPARISON UI WHEN >= 2 PLANTS REGISTERED

                // Plant Selectors Row (Two Dropdown Selector Cards Side-by-Side)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Selector Card for Plant 1
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showPlant1Dialog = true }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF288B46),
                                        shape = CircleShape,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = "PLANTA 1",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCCCCCC)
                                    )
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Seleccionar", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            Text(
                                text = plant1?.name ?: "Seleccionar Plant 1",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = "${plant1?.environment ?: "Interior"} · ${plant1?.type ?: "Especia"}",
                                fontSize = 12.sp,
                                color = Color(0xFFCCCCCC),
                                maxLines = 1
                            )
                        }
                    }

                    // Selector Card for Plant 2
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showPlant2Dialog = true }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF288B46),
                                        shape = CircleShape,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("2", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = "PLANTA 2",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCCCCCC)
                                    )
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Seleccionar", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            Text(
                                text = plant2?.name ?: "Seleccionar Plant 2",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = "${plant2?.environment ?: "Interior"} · ${plant2?.type ?: "Especia"}",
                                fontSize = 12.sp,
                                color = Color(0xFFCCCCCC),
                                maxLines = 1
                            )
                        }
                    }
                }

                // 3. CARD 1: COMPARACIÓN DE LECTURAS (Chart & Focus Controls)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Comparación de lecturas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )

                            // Interactive Filtros Button opening ModalBottomSheet
                            Surface(
                                color = Color(0xFF38383B),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable { showFilterSheet = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filtros", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Filtros", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Surface(
                                        color = Color(0xFF288B46),
                                        shape = CircleShape,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("$activeSelectedCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Focus Segmented Control
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "ENFOQUE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCCCCCC)
                            )

                            Surface(
                                color = Color(0xFF38383B),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val focusOptions = listOf(Pair("Ambas", 0), Pair("— Planta 1", 1), Pair("--- Planta 2", 2))
                                    focusOptions.forEach { (label, modeIndex) ->
                                        val isSelected = focusMode == modeIndex
                                        Surface(
                                            color = if (isSelected) Color(0xFF458C4F) else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { focusMode = modeIndex }
                                        ) {
                                            Text(
                                                text = label,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Mostrando: $activeMetricsText",
                                fontSize = 12.sp,
                                color = Color(0xFFCCCCCC),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Chart Container
                        val activeReadings = when (focusMode) {
                            1 -> readingsPlant1
                            2 -> readingsPlant2
                            else -> readingsPlant1
                        }

                        if (activeReadings.isNotEmpty()) {
                            AndroidView(
                                factory = { ctx ->
                                    CombinedChart(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            (200 * ctx.resources.displayMetrics.density).toInt()
                                        )
                                        ChartManager.setupCombinedChart(
                                            chart = this,
                                            readings = activeReadings,
                                            showTemp = filterTemp,
                                            showHum = filterHum,
                                            showSoil = filterSoil,
                                            showLight = filterLight,
                                            showIrh = filterIrh
                                        )
                                    }
                                },
                                update = { chart ->
                                    ChartManager.setupCombinedChart(
                                        chart = chart,
                                        readings = activeReadings,
                                        showTemp = filterTemp,
                                        showHum = filterHum,
                                        showSoil = filterSoil,
                                        showLight = filterLight,
                                        showIrh = filterIrh
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sin lecturas suficientes para comparar",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // 4. CARD 2: ÚLTIMA LECTURA (Centered Cell Layout & Exact PNG Tilesets tile_*.png)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Última lectura",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Column 1: Plant 1 Metrics
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Centered Header Plant 1
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Surface(color = Color(0xFF288B46), shape = CircleShape, modifier = Modifier.size(20.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "[${plant1?.name ?: "Planta 1"}]",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF38383B), thickness = 1.dp)

                                // Metric Rows Plant 1 (Exact PNG Tileset Drawables tile_*.png)
                                ComparisonMetricRow("temperatura", "Temp.", temp1Str, "°C")
                                ComparisonMetricRow("humedad", "Hum. ambiente", hum1Str, "%")
                                ComparisonMetricRow("suelo", "Hum. tierra", soil1Str, "%")
                                ComparisonMetricRow("sol", "Luz", light1Str, "%")
                                ComparisonMetricRow("sol", "Seq.", seq1Str, "h")
                                ComparisonMetricRow("sol", "Somb.", somb1Str, "h")

                                // IRH Metric & Status Badge Plant 1 (tile_irh.png)
                                ComparisonMetricRow("irh", "IRH", irh1Str, "", showDivider = false)

                                Surface(
                                    color = statusColor1,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = statusText1,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Thin Central Vertical Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(380.dp)
                                    .background(Color(0xFF38383B))
                            )

                            // Column 2: Plant 2 Metrics
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Centered Header Plant 2
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Surface(color = Color(0xFF288B46), shape = CircleShape, modifier = Modifier.size(20.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("2", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "[${plant2?.name ?: "Planta 2"}]",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF38383B), thickness = 1.dp)

                                // Metric Rows Plant 2 (Exact PNG Tileset Drawables tile_*.png)
                                ComparisonMetricRow("temperatura", "Temp.", temp2Str, "°C")
                                ComparisonMetricRow("humedad", "Hum. ambiente", hum2Str, "%")
                                ComparisonMetricRow("suelo", "Hum. tierra", soil2Str, "%")
                                ComparisonMetricRow("sol", "Luz", light2Str, "%")
                                ComparisonMetricRow("sol", "Seq.", seq2Str, "h")
                                ComparisonMetricRow("sol", "Somb.", somb2Str, "h")

                                // IRH Metric & Status Badge Plant 2 (tile_irh.png)
                                ComparisonMetricRow("irh", "IRH", irh2Str, "", showDivider = false)

                                Surface(
                                    color = statusColor2,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = statusText2,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ModalBottomSheet for "Filtros" matching exact design proposal
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = Color(0xFF252527),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF555557))
                )
            }
        ) {
            var tempSelected by remember { mutableStateOf(filterTemp) }
            var humSelected by remember { mutableStateOf(filterHum) }
            var soilSelected by remember { mutableStateOf(filterSoil) }
            var lightSelected by remember { mutableStateOf(filterLight) }
            var irhSelected by remember { mutableStateOf(filterIrh) }
            var seqSelected by remember { mutableStateOf(filterSeq) }
            var sombSelected by remember { mutableStateOf(filterSomb) }

            val sheetCount = listOf(tempSelected, humSelected, soilSelected, lightSelected, irhSelected, seqSelected, sombSelected).count { it }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title & Subtitle
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Filtros",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Elige qué lecturas mostrar en el gráfico",
                        fontSize = 14.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }

                // Sub-header LECTURAS
                Text(
                    text = "LECTURAS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCCCCCC)
                )

                // Metric Filter Chips Grid (Row 1 & Row 2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChipItem("- Temp", Color(0xFFCC6600), isSelected = tempSelected) { tempSelected = !tempSelected }
                        FilterChipItem("-- Hum", Color(0xFF0099CC), isSelected = humSelected) { humSelected = !humSelected }
                        FilterChipItem("... Suelo", Color(0xFF42A5F5), isSelected = soilSelected) { soilSelected = !soilSelected }
                        FilterChipItem("- Luz", Color(0xFFCCCCCC), isSelected = lightSelected) { lightSelected = !lightSelected }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChipItem("- IRH", Color(0xFFC62828), isSelected = irhSelected) { irhSelected = !irhSelected }
                        FilterChipItem("-- Seq", Color(0xFFAB47BC), isSelected = seqSelected) { seqSelected = !seqSelected }
                        FilterChipItem("... Somb", Color(0xFF5C6BC0), isSelected = sombSelected) { sombSelected = !sombSelected }
                    }
                }

                // Counter Subtext
                Text(
                    text = "$sheetCount de 7 lecturas seleccionadas",
                    fontSize = 14.sp,
                    color = Color(0xFFCCCCCC)
                )

                // Bottom Action Buttons: Restablecer & Aplicar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Restablecer
                    OutlinedButton(
                        onClick = {
                            tempSelected = true
                            humSelected = false
                            soilSelected = true
                            lightSelected = false
                            irhSelected = false
                            seqSelected = false
                            sombSelected = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF252527)),
                        border = BorderStroke(1.5.dp, Color(0xFF555557)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Restablecer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    // Aplicar
                    Button(
                        onClick = {
                            filterTemp = tempSelected
                            filterHum = humSelected
                            filterSoil = soilSelected
                            filterLight = lightSelected
                            filterIrh = irhSelected
                            filterSeq = seqSelected
                            filterSomb = sombSelected
                            showFilterSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF458C4F)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Aplicar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Plant 1 Selection Dialog
    if (showPlant1Dialog) {
        AlertDialog(
            onDismissRequest = { showPlant1Dialog = false },
            containerColor = Color(0xFF252527),
            shape = RoundedCornerShape(24.dp),
            title = { Text("Seleccionar Planta 1", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allPlants.forEach { p ->
                        Surface(
                            color = if (p.id == plant1?.id) Color(0xFF458C4F) else Color(0xFF38383B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    plant1 = p
                                    showPlant1Dialog = false
                                }
                        ) {
                            Text(
                                text = "${p.name} (${p.type})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Plant 2 Selection Dialog
    if (showPlant2Dialog) {
        AlertDialog(
            onDismissRequest = { showPlant2Dialog = false },
            containerColor = Color(0xFF252527),
            shape = RoundedCornerShape(24.dp),
            title = { Text("Seleccionar Planta 2", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allPlants.forEach { p ->
                        Surface(
                            color = if (p.id == plant2?.id) Color(0xFF458C4F) else Color(0xFF38383B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    plant2 = p
                                    showPlant2Dialog = false
                                }
                        ) {
                            Text(
                                text = "${p.name} (${p.type})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

/**
 * Filter Chip Item matching exact proposal mockup:
 * Dark background (#252527), green border when selected (#458C4F).
 */
@Composable
fun FilterChipItem(
    label: String,
    lineColor: Color,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val parts = label.split(" ")
    val lineStyleText = parts.getOrElse(0) { "" }
    val titleText = parts.getOrElse(1) { "" }

    Surface(
        color = Color(0xFF252527),
        shape = RoundedCornerShape(18.dp),
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF458C4F)) else BorderStroke(1.dp, Color(0xFF38383B)),
        modifier = Modifier.clickable { onToggle() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = lineStyleText,
                color = lineColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = titleText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Single Metric Row for Side-by-Side Comparison Table using exact PNG tilesets (tile_*.png):
 * Left PNG Tileset Icon (32dp), Centered Label & Value + Unit inside cell, Bottom Horizontal Divider.
 */
@Composable
fun ComparisonMetricRow(
    iconName: String,
    label: String,
    value: String,
    unit: String,
    showDivider: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            // Left PNG Tileset Icon (32.dp)
            HerbalImageTileset.TilesetIcon(
                iconName = iconName,
                contentDescription = label,
                size = 32.dp
            )

            // Centered Label & Value Column
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = Color(0xFFCCCCCC),
                    textAlign = TextAlign.End
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = value,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        textAlign = TextAlign.End
                    )
                    if (unit.isNotEmpty() && value != "--") {
                        Text(
                            text = unit,
                            fontSize = 12.sp,
                            color = Color(0xFFCCCCCC),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = Color(0xFF38383B),
                thickness = 1.dp
            )
        }
    }
}

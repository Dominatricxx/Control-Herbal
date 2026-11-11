package com.example.controlherbal.frontend.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Warning
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
import com.example.controlherbal.frontend.helper.ChartManager
import com.github.mikephil.charting.charts.CombinedChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production-ready HistoryLogScreen matching the exact design mockup.
 * Supports offline local fallback data caching and non-synced warning banner.
 */
@Composable
fun HistoryLogScreen(
    initialPeriodTab: Int = 0, // 0 = Diario, 1 = Semanal, 2 = Mensual
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { SensorDatabase.getInstance(context) }

    var selectedPeriodTab by remember { mutableStateOf(initialPeriodTab) }
    var selectedPlant by remember { mutableStateOf<Plant?>(null) }
    var readingsList by remember { mutableStateOf<List<SensorReading>>(emptyList()) }
    var isOfflineFallback by remember { mutableStateOf(false) }
    var lastSyncFormatted by remember { mutableStateOf<String?>(null) }

    // Chart metric toggles
    var showTemp by remember { mutableStateOf(true) }
    var showHum by remember { mutableStateOf(true) }
    var showSoil by remember { mutableStateOf(true) }
    var showLight by remember { mutableStateOf(true) }
    var showIrh by remember { mutableStateOf(true) }

    // Reactive load of selected plant and real-time sensor readings Flow with offline local fallback
    LaunchedEffect(selectedPeriodTab, selectedPlant?.id) {
        withContext(Dispatchers.IO) {
            val plant = selectedPlant ?: db.plantDao().getSelectedPlant() ?: db.plantDao().getAll().firstOrNull()
            selectedPlant = plant

            if (plant != null) {
                // ALWAYS load cached local data from Room DB first so user sees local fallback readings immediately!
                val cachedReadings = db.sensorDao().getLast2000Asc(plant.id)
                val latestReading = cachedReadings.lastOrNull()
                if (latestReading != null) {
                    val sdf = SimpleDateFormat("d 'de' MMMM, HH:mm", Locale("es", "ES"))
                    lastSyncFormatted = sdf.format(Date(latestReading.timestamp))
                }

                db.sensorDao().getLatestReadingFlow(plant.id).collect { latest ->
                    val now = System.currentTimeMillis()
                    val periodDurationMillis = when (selectedPeriodTab) {
                        1 -> 7L * 24 * 3600 * 1000L // Semanal
                        2 -> 30L * 24 * 3600 * 1000L // Mensual
                        else -> 24L * 3600 * 1000L // Diario
                    }
                    val startTime = now - periodDurationMillis
                    val fetchedReadings = db.sensorDao().getReadingsBetween(plant.id, startTime, now)
                    val activeList = fetchedReadings.ifEmpty { cachedReadings }
                    readingsList = activeList

                    // If latest reading is older than 5 minutes or no active server stream, show offline fallback warning
                    val diffMinutes = if (latest != null) (now - latest.timestamp) / (1000 * 60) else 999L
                    isOfflineFallback = diffMinutes > 5
                }
            }
        }
    }

    val plantNameText = selectedPlant?.name ?: "[Nombre de la planta]"
    val periodTitle = when (selectedPeriodTab) {
        1 -> "Registro semanal"
        2 -> "Registro mensual"
        else -> "Registro diario"
    }
    val periodSubtext = when (selectedPeriodTab) {
        1 -> "Últimos 7 días"
        2 -> "Últimos 30 días"
        else -> "Últimas 24 horas"
    }

    val hasData = readingsList.isNotEmpty()
    val avgTemp = if (hasData) String.format("%.1f °C", readingsList.map { it.temperature.toDouble() }.average()) else "--"
    val avgHum = if (hasData) String.format("%.0f %%", readingsList.map { it.humidity.toDouble() }.average()) else "--"
    val avgSoil = if (hasData) String.format("%.0f %%", readingsList.map { it.soilMoisture.toDouble() }.average()) else "--"
    val avgLight = if (hasData) String.format("%.0f %%", readingsList.map { it.light.toDouble() }.average()) else "--"
    val avgIrh = if (hasData) String.format("%.1f", readingsList.map { it.irh.toDouble() }.average()) else "--"

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
                .padding(bottom = 24.dp),
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
                        text = periodTitle,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                        color = Color.White
                    )
                    Text(
                        text = "$plantNameText · $periodSubtext",
                        fontSize = 13.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }

            // 2. SEGMENTED TAB SELECTOR (Diario / Semanal / Mensual)
            Surface(
                color = Color(0xFF252527),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Tab 0: Diario
                    val isDiarioSelected = selectedPeriodTab == 0
                    Surface(
                        color = if (isDiarioSelected) Color(0xFF458C4F) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPeriodTab = 0 }
                    ) {
                        Text(
                            text = if (isDiarioSelected) "✓ Diario" else "Diario",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    // Tab 1: Semanal
                    val isSemanalSelected = selectedPeriodTab == 1
                    Surface(
                        color = if (isSemanalSelected) Color(0xFF458C4F) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPeriodTab = 1 }
                    ) {
                        Text(
                            text = if (isSemanalSelected) "✓ Semanal" else "Semanal",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    // Tab 2: Mensual
                    val isMensualSelected = selectedPeriodTab == 2
                    Surface(
                        color = if (isMensualSelected) Color(0xFF458C4F) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPeriodTab = 2 }
                    ) {
                        Text(
                            text = if (isMensualSelected) "✓ Mensual" else "Mensual",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }

            // OFFLINE FALLBACK WARNING BANNER
            if (isOfflineFallback && hasData) {
                Surface(
                    color = Color(0xFF3B2E00),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Sin conexión en vivo",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(20.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sin conexión en vivo con el servidor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFFFE082)
                            )
                            Text(
                                text = if (lastSyncFormatted != null) "Mostrando reserva de datos locales ($lastSyncFormatted)" else "Mostrando reserva de lecturas guardadas en el dispositivo",
                                fontSize = 11.sp,
                                color = Color(0xFFFFECB3)
                            )
                        }
                    }
                }
            }

            // 3. CARD 1: RESUMEN DEL PERIODO
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
                        text = "Resumen del periodo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )

                    // IRH MEDIO Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38383B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = HerbalIcons.IrhShield,
                                contentDescription = "IRH Medio",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "IRH MEDIO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFCCCCCC)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = avgIrh,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(IA)",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCCCCCC)
                                )
                            }
                        }

                        Surface(
                            color = if (hasData) Color(0xFF288B46) else Color(0xFF1565C0),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = if (hasData) "✔ Óptimo" else "ℹ Sin datos",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // 2x2 Sensor Metrics Grid
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Temp
                            SensorMetricPill(
                                title = "Temp. media",
                                value = avgTemp,
                                iconName = "temperatura",
                                modifier = Modifier.weight(1f)
                            )
                            // Hum. ambiente
                            SensorMetricPill(
                                title = "Hum. ambiente",
                                value = avgHum,
                                iconName = "humedad",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Hum. tierra
                            SensorMetricPill(
                                title = "Hum. tierra",
                                value = avgSoil,
                                iconName = "suelo",
                                modifier = Modifier.weight(1f)
                            )
                            // Luz solar
                            SensorMetricPill(
                                title = "Luz solar",
                                value = avgLight,
                                iconName = "sol",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 4. CARD 2: GRÁFICO DEL PERIODO (Interactive Legend Chips + Real MPAndroidChart CombinedChart when hasData)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Gráfico del periodo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )

                    // Interactive Legend Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InteractiveLegendChip("- Temp", Color(0xFFCC6600), isSelected = showTemp) { showTemp = !showTemp }
                        InteractiveLegendChip("-- Hum", Color(0xFF0099CC), isSelected = showHum) { showHum = !showHum }
                        InteractiveLegendChip("... Suelo", Color(0xFF42A5F5), isSelected = showSoil) { showSoil = !showSoil }
                        InteractiveLegendChip("- Luz", Color(0xFFCCCCCC), isSelected = showLight) { showLight = !showLight }
                        InteractiveLegendChip("|| IRH", Color(0xFFC62828), isSelected = showIrh) { showIrh = !showIrh }
                    }

                    // CombinedChart or Empty State Placeholder
                    if (hasData) {
                        AndroidView(
                            factory = { ctx ->
                                CombinedChart(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        (220 * ctx.resources.displayMetrics.density).toInt()
                                    )
                                    ChartManager.setupCombinedChart(
                                        chart = this,
                                        readings = readingsList,
                                        showTemp = showTemp,
                                        showHum = showHum,
                                        showSoil = showSoil,
                                        showLight = showLight,
                                        showIrh = showIrh
                                    )
                                }
                            },
                            update = { chart ->
                                ChartManager.setupCombinedChart(
                                    chart = chart,
                                    readings = readingsList,
                                    showTemp = showTemp,
                                    showHum = showHum,
                                    showSoil = showSoil,
                                    showLight = showLight,
                                    showIrh = showIrh
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = HerbalIcons.Plant,
                                    contentDescription = "Sin registros",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )

                                Text(
                                    text = "No hay registros para este periodo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "Importa tus lecturas desde la nube\no espera nuevas mediciones.",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCCCCCC),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorMetricPill(
    title: String,
    value: String,
    iconName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF38383B),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HerbalImageTileset.TilesetIcon(
                iconName = iconName,
                contentDescription = title,
                size = 24.dp
            )

            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color(0xFFCCCCCC),
                    maxLines = 1
                )
                Text(
                    text = value,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun InteractiveLegendChip(
    label: String,
    color: Color,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = if (isSelected) Color(0xFF38383B) else Color(0xFF222224),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onToggle() }
    ) {
        Text(
            text = label,
            color = if (isSelected) color else Color(0xFF666666),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

package com.example.controlherbal.frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.frontend.viewmodel.MainUiState
import com.example.controlherbal.frontend.viewmodel.MainViewModel

data class ParsedPlantDetails(
    val category: String,
    val typeName: String,
    val scientificName: String
)

fun parsePlantTypeString(typeString: String?): ParsedPlantDetails {
    if (typeString.isNullOrBlank()) {
        return ParsedPlantDetails("Flores", "Girasol", "Helianthus annuus")
    }
    var cat = "Flores"
    var sci = typeString
    if (typeString.contains("Categoría:")) {
        val parts = typeString.split("|")
        cat = parts.getOrNull(0)?.substringAfter("Categoría:")?.trim() ?: "Flores"
        sci = parts.getOrNull(1)?.substringAfter("Tipo:")?.trim() ?: typeString
    }
    return ParsedPlantDetails(cat, sci, sci)
}

/**
 * Production-ready Hero Dashboard Composable screen connected to MainViewModel StateFlow.
 * HerbalHeroHeader spans edge-to-edge at the top of the 28.dp rounded card with zero top/side gaps.
 */
@Composable
fun HeroDashboardScreen(
    viewModel: MainViewModel,
    onPlantNameClick: () -> Unit,
    onWateringClick: () -> Unit,
    onDataControlClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()

    var plant: Plant? = null
    var lastReading: SensorReading? = null
    var isConnecting by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is MainUiState.Success -> {
            plant = state.plant
            lastReading = state.lastReading
        }
        is MainUiState.Loading -> {
            isConnecting = true
        }
        else -> {}
    }

    val plantName = plant?.name ?: "[Nombre de la planta]"
    val plantDetails = remember(plant?.type) { parsePlantTypeString(plant?.type) }
    val category = plantDetails.category
    val scientificName = plantDetails.scientificName
    val irhValue = if (lastReading != null) lastReading!!.irh.toFloat() else null
    val hasData = lastReading != null

    val connectionState = when {
        lastReading != null -> ConnectionState.CONNECTED
        isConnecting -> ConnectionState.CONNECTING
        else -> ConnectionState.DISCONNECTED
    }

    val herbalValues = remember(lastReading) {
        mapSensorReadingToHerbalValues(lastReading)
    }

    val cardBg = if (isDark) Color(0xFF2B2B2D) else Color(0xFFD9D5C3)
    val textColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF26261F)
    val textSecondary = if (isDark) Color(0xFFC4C4CC) else Color(0xFF4A493F)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // FIXED Card Container with permanent 28.dp rounded corners (NO padding on Box so header goes edge-to-edge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(cardBg)
        ) {
            // Internal scrollable column WITHIN the fixed rounded card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. HERO HEADER (Edge-to-edge at top with top card corners)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlantNameClick() }
                ) {
                    HerbalHeroHeader(
                        plantName = plantName,
                        category = category,
                        scientificName = scientificName,
                        environmentType = plant?.environment ?: "Interior"
                    )
                }

                // Inner content container with 16.dp padding for all items below the header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 2. IRH RING COMPONENT
                    HerbalIrhRing(
                        irhValue = irhValue,
                        connectionState = connectionState,
                        isDarkTheme = isDark,
                        modifier = Modifier.fillMaxWidth(),
                        animate = true
                    )

                    // 3. TWO SQUARES ROW (Sensor 2x2 grid + Light hours)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        ) {
                            HerbalStatusWidget(
                                water = if (hasData) herbalValues.water else null,
                                sun = if (hasData) herbalValues.sun else null,
                                plant = if (hasData) herbalValues.plant else null,
                                temperature = if (hasData) herbalValues.temperature else null,
                                hasData = hasData,
                                modifier = Modifier.fillMaxSize(),
                                animate = true
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(28.dp))
                                .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFC8C3B0))
                                .padding(16.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HerbalImageTileset.TilesetIcon(iconName = "sol", contentDescription = "Sol", size = 16.dp)
                                    Text(
                                        text = "Horas de luz hoy",
                                        fontSize = 12.sp,
                                        color = textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (hasData) String.format(java.util.Locale.getDefault(), "%.1f", lastReading!!.light / 200.0) else "--",
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "de 6 h",
                                            fontSize = 12.sp,
                                            color = textSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { if (hasData) ((lastReading!!.light / 1000.0).toFloat().coerceIn(0f, 1f)) else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFFF6B209),
                                        trackColor = textSecondary.copy(alpha = 0.2f)
                                    )
                                    Text(
                                        text = if (hasData) "Meta diaria óptima" else "Sin lecturas",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                        }
                    }

                    // 4. LARGE ACTION PANEL
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFC8C3B0))
                            .padding(20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    HerbalImageTileset.TilesetIcon(iconName = "suelo", contentDescription = "Suelo", size = 20.dp)
                                    Text(
                                        text = "Humedad de suelo",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (hasData) Color(0xFFB34700) else Color.Gray.copy(alpha = 0.4f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (hasData) {
                                            if (lastReading!!.soilMoisture < 30.0) "▲ Atención" else "▲ Preventivo"
                                        } else "○ Sin datos",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Riego sugerido",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (hasData) "8:45" else "--:--",
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = textColor
                                        )
                                        Text(
                                            text = if (hasData) "p. m." else "",
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = textSecondary,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    Text(
                                        text = if (hasData) "Acción automatizada lista" else "Esperando señal de sensores",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (hasData) "Actual %.0f %%".format(lastReading!!.soilMoisture) else "Actual -- %",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Mínimo ideal 25 %",
                                        fontSize = 11.sp,
                                        color = textSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Bottom spacing inside card
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

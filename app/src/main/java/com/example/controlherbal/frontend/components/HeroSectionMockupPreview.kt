package com.example.controlherbal.frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

fun mockIrhColor24(p: Float): Color = Color(0xFFF6B209)

@Composable
fun FullyIntegratedSensorHeroScreen(
    isDark: Boolean = true
) {
    val cardBg = if (isDark) Color(0xFF2B2B2D) else Color(0xFFD9D5C3)
    val textColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF26261F)
    val textSecondary = if (isDark) Color(0xFFC4C4CC) else Color(0xFF4A493F)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main card container (radius 28)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(cardBg)
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. HERO HEADER
                    HerbalHeroHeader(
                        plantName = "[Nombre de la\nplanta]",
                        category = "[Categoría]",
                        scientificName = "[Nombre científico]",
                        environmentType = "Interior"
                    )

                    // 2. IRH RING COMPONENT (Integrated with HerbalIrhRing)
                    HerbalIrhRing(
                        irhValue = 34f,
                        isConnected = true,
                        isDarkTheme = isDark,
                        modifier = Modifier.fillMaxWidth(),
                        animate = false
                    )

                    // 3. TWO SQUARES ROW (Integrated with HerbalStatusWidget 2x2 grid)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Sensor Status Widget (2x2 Rings powered by HerbalRingMapper)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        ) {
                            HerbalStatusWidget(
                                water = 65f,
                                sun = 75f,
                                plant = 31f,
                                temperature = 48f,
                                modifier = Modifier.fillMaxSize(),
                                animate = false
                            )
                        }

                        // Light hours box
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
                                            text = "3.5",
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
                                        progress = { 0.58f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFFF6B209),
                                        trackColor = textSecondary.copy(alpha = 0.2f)
                                    )
                                    Text(
                                        text = "58 % de la meta",
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
                                        .background(Color(0xFFB34700))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "▲ Preventivo",
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
                                        text = "Riego sugerido a las",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "8:45",
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = textColor
                                        )
                                        Text(
                                            text = "p. m.",
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = textSecondary,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    Text(
                                        text = "En 5 horas y 45 minutos",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Hoy 31 %",
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "••••",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    letterSpacing = 6.sp
                                )
                            }
                        }
                    }
                }
            }

            // 5. BOTTOM NAVIGATION BAR (Icons made slightly larger: 28dp, center add: 28dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(if (isDark) Color(0xFF2B2B2D) else Color(0xFFD9D5C3))
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HerbalImageTileset.TilesetIcon(iconName = "inicio", contentDescription = "Inicio", size = 28.dp)
                        HerbalImageTileset.TilesetIcon(iconName = "plantas", contentDescription = "Plantas", size = 28.dp)
                        Spacer(modifier = Modifier.width(56.dp))
                        HerbalImageTileset.TilesetIcon(iconName = "calendario", contentDescription = "Calendario", size = 28.dp)
                        HerbalImageTileset.TilesetIcon(iconName = "perfil", contentDescription = "Perfil", size = 28.dp)
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-4).dp)
                        .size(width = 56.dp, height = 56.dp)
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 12.dp, topEnd = 12.dp))
                        .background(Color(0xFF4F7348)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        HerbalImageTileset.TilesetIcon(iconName = "agregar", contentDescription = "Agregar", size = 28.dp)
                    }
                }
            }
        }
    }
}

@Preview(name = "Fully Integrated Sensor Rings Preview", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 390, heightDp = 844)
@Composable
fun FullyIntegratedSensorRingsPreview() {
    FullyIntegratedSensorHeroScreen(isDark = true)
}

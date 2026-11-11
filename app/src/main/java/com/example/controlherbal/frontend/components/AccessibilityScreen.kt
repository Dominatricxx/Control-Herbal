package com.example.controlherbal.frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
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
import com.example.controlherbal.common.accessibility.PaletteMode
import com.example.controlherbal.common.accessibility.StatusPalette

/**
 * Production-ready AccessibilityScreen matching the exact design mockup aesthetics.
 * Features palette mode selection radio list for all visual conditions and live preview of status state colors.
 */
@Composable
fun AccessibilityScreen(
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(StatusPalette.mode(context)) }

    // Dynamic state colors computed live based on selected PaletteMode
    val currentStatusColors = remember(selectedMode) {
        StatusPalette.colors(selectedMode)
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP HEADER WITH SAFE CAMERA AREA (top = 64.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
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

                Text(
                    text = "Accesibilidad",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    color = Color.White
                )
            }

            // 2. CARD 1: COLORES PARA DISTINTAS CONDICIONES VISUALES (Radio List)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Colores para distintas condiciones visuales",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    val modes = listOf(
                        PaletteMode.STANDARD,
                        PaletteMode.PROTANOPIA,
                        PaletteMode.DEUTERANOPIA,
                        PaletteMode.TRITANOPIA,
                        PaletteMode.PROTANOMALY,
                        PaletteMode.DEUTERANOMALY,
                        PaletteMode.ACHROMATOPSIA,
                        PaletteMode.HIGH_CONTRAST
                    )

                    modes.forEach { mode ->
                        val isSelected = mode == selectedMode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedMode = mode
                                    StatusPalette.setMode(context, mode)
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedMode = mode
                                    StatusPalette.setMode(context, mode)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF288B46),
                                    unselectedColor = Color(0xFF666666)
                                )
                            )

                            Text(
                                text = mode.label,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. CARD 2: ASÍ SE VEN LOS ESTADOS (Live Preview Card)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Así se ven los estados",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    // 3 Live State Badges displaying dynamic colors & contrast text from selected PaletteMode
                    val okTextColor = Color(StatusPalette.textColorFor(com.example.controlherbal.common.accessibility.StatusLevel.OK, selectedMode))
                    val warnTextColor = Color(StatusPalette.textColorFor(com.example.controlherbal.common.accessibility.StatusLevel.WARN, selectedMode))
                    val criticalTextColor = Color(StatusPalette.textColorFor(com.example.controlherbal.common.accessibility.StatusLevel.CRITICAL, selectedMode))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Óptimo (OK)
                        Surface(
                            color = Color(currentStatusColors.ok),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "✔ Óptimo",
                                color = okTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // 2. Atención (WARN)
                        Surface(
                            color = Color(currentStatusColors.warn),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "▲ Atención",
                                color = warnTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // 3. Riesgo (CRITICAL)
                        Surface(
                            color = Color(currentStatusColors.critical),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "✖ Riesgo",
                                color = criticalTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    Text(
                        text = "Los estados siempre llevan también un símbolo y un texto, no solo color.",
                        fontSize = 13.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }
        }
    }
}

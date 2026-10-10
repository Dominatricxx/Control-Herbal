package com.example.controlherbal.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Barra de navegación inferior con los 4 botones y efecto líquido de estiramiento ("Gooey / Liquid Stretch")
 * cuando la gota se desplaza entre pestañas.
 */
@Composable
fun GooeyBottomBar(
    tabs: List<Triple<String, ImageVector, String>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Color(0xFF2E7D32),     // Tono verde característico de la app
    unselectedColor: Color = Color(0xFF6B7280),   // Gris neutro para iconos no seleccionados
    barBackground: Color = Color.White,           // Fondo blanco para el contenedor
    borderColor: Color = Color(0xFFE5E7EB)        // Borde sutil
) {
    var previousTab by remember { mutableStateOf(selectedTab) }
    LaunchedEffect(selectedTab) {
        previousTab = selectedTab
    }

    // Animación de posición con física de resorte (efecto gota líquida)
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedTab.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessLow
        ),
        label = "gooeyOffset"
    )

    // Cálculo del estiramiento (squash and stretch) al viajar entre pestañas
    val currentDiff = abs(indicatorOffset - selectedTab.toFloat())
    val stretchFactor = 1f + (currentDiff * 0.6f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(barBackground)
            .border(1.dp, borderColor, RoundedCornerShape(36.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            // Gota líquida con efecto de estiramiento (Squash & Stretch)
            Layout(
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(28.dp))
                            .background(selectedColor)
                    )
                }
            ) { measurables, constraints ->
                val tabWidth = constraints.maxWidth / tabs.size
                val baseSize = 56.dp.toPx()
                val currentWidth = (baseSize * stretchFactor).coerceAtMost(tabWidth.toFloat())

                val measurable = measurables.first()
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = currentWidth.toInt(),
                        maxWidth = currentWidth.toInt(),
                        minHeight = baseSize.toInt(),
                        maxHeight = baseSize.toInt()
                    )
                )

                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = (tabWidth * indicatorOffset).toInt() + (tabWidth - placeable.width) / 2
                    val y = (constraints.maxHeight - placeable.height) / 2
                    placeable.placeRelative(x, y)
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(30.dp))
                            .clickable { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.second,
                            contentDescription = tab.first,
                            tint = if (isSelected) Color.White else unselectedColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.example.controlherbal.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

@Composable
fun GooeyBottomBar(
    tabs: List<Triple<String, ImageVector, String>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Color(0xFF2E7D32),     // Tono verde característico de la app
    unselectedColor: Color = Color(0xFF6B7280),   // Gris neutro para iconos no seleccionados
    barBackground: Color = Color.White,           // Fondo blanco para el contenedor
    borderColor: Color = Color(0xFFE5E7EB),       // Borde sutil
    selectionProgress: Float? = null
) {
    LiquidNavBarFloat(
        items = tabs.map { (_, icon, description) -> NavItem(icon, description) },
        currentSelectedIndex = selectionProgress ?: selectedTab.toFloat(),
        onSelect = onTabSelected,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
        barBackground = barBackground,
        borderColor = borderColor
    )
}

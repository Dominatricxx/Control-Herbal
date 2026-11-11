package com.example.controlherbal.frontend.components

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.controlherbal.frontend.activities.plant.PlantSetupActivity

/**
 * Production-ready HerbalNavBar utilizing HerbalLiquidNavBar with fluid droplet physics,
 * smooth tab transitions, and the pop-up menu for "Nueva planta" and "Editar planta",
 * using exact PNG tileset icons (tile_inicio, tile_plantas, tile_calendario, tile_perfil, tile_agregar).
 */
@Composable
fun HerbalNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var agregarSeleccionado by remember { mutableStateOf(false) }

    val items = listOf(
        HerbalNavItem("inicio", "Inicio"),
        HerbalNavItem("plantas", "Plantas"),
        HerbalNavItem("calendario", "Calendario"),
        HerbalNavItem("perfil", "Perfil")
    )

    HerbalLiquidNavBar(
        items = items,
        selectedIndex = selectedTab,
        onSelect = { index -> onTabSelected(index) },
        agregarSeleccionado = agregarSeleccionado,
        onAgregarSeleccionadoChange = { agregarSeleccionado = it },
        onNuevaPlanta = {
            agregarSeleccionado = false
            context.startActivity(Intent(context, PlantSetupActivity::class.java))
        },
        onEditarPlanta = {
            agregarSeleccionado = false
            onAddClick()
        },
        modifier = modifier,
        colors = if (isDark) HerbalNavColors.Oscuro else HerbalNavColors.Claro
    )
}

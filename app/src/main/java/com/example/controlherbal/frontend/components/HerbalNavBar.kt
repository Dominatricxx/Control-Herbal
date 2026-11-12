package com.example.controlherbal.frontend.components

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.frontend.activities.plant.PlantSetupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PlantActionState {
    object None : PlantActionState()
    data class Menu(val plant: Plant) : PlantActionState()
    data class EditName(val plant: Plant) : PlantActionState()
    data class ConfirmDelete(val plant: Plant) : PlantActionState()
}

/**
 * Production-ready HerbalNavBar utilizing HerbalLiquidNavBar with fluid droplet physics,
 * smooth tab transitions, and the pop-up menu for "Nueva planta" and "Editar planta" (Edit name & Delete with confirmation).
 */
@Composable
fun HerbalNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    val context = LocalContext.current
    val db = remember { SensorDatabase.getInstance(context) }
    var agregarSeleccionado by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var actionDialogState by remember { mutableStateOf<PlantActionState>(PlantActionState.None) }

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
            scope.launch(Dispatchers.IO) {
                val plant = db.plantDao().getSelectedPlant() ?: db.plantDao().getAll().firstOrNull()
                withContext(Dispatchers.Main) {
                    if (plant == null) {
                        context.startActivity(Intent(context, PlantSetupActivity::class.java))
                    } else {
                        actionDialogState = PlantActionState.Menu(plant)
                    }
                }
            }
        },
        modifier = modifier,
        colors = if (isDark) HerbalNavColors.Oscuro else HerbalNavColors.Claro
    )

    // Render Compose Dialogs matching exact dark aesthetics
    when (val state = actionDialogState) {
        is PlantActionState.Menu -> {
            PlantActionsMenuDialog(
                plant = state.plant,
                onDismiss = { actionDialogState = PlantActionState.None; agregarSeleccionado = false },
                onEditClick = { actionDialogState = PlantActionState.EditName(state.plant) },
                onDeleteClick = { actionDialogState = PlantActionState.ConfirmDelete(state.plant) }
            )
        }
        is PlantActionState.EditName -> {
            PlantEditNameDialog(
                plant = state.plant,
                onDismiss = { actionDialogState = PlantActionState.None; agregarSeleccionado = false },
                onSave = { newName ->
                    scope.launch(Dispatchers.IO) {
                        db.plantDao().update(state.plant.copy(name = newName))
                        withContext(Dispatchers.Main) {
                            actionDialogState = PlantActionState.None
                            agregarSeleccionado = false
                            (context as? Activity)?.recreate()
                        }
                    }
                }
            )
        }
        is PlantActionState.ConfirmDelete -> {
            PlantDeleteConfirmDialog(
                plant = state.plant,
                onDismiss = { actionDialogState = PlantActionState.None; agregarSeleccionado = false },
                onConfirm = {
                    scope.launch(Dispatchers.IO) {
                        db.sensorDao().deleteAllByPlantId(state.plant.id)
                        db.plantDao().delete(state.plant)
                        val remaining = db.plantDao().getAll()
                        remaining.firstOrNull()?.let {
                            db.plantDao().update(it.copy(isSelected = true))
                        }
                        withContext(Dispatchers.Main) {
                            actionDialogState = PlantActionState.None
                            agregarSeleccionado = false
                            (context as? Activity)?.recreate()
                        }
                    }
                }
            )
        }
        else -> {}
    }
}

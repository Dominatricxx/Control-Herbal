package com.example.controlherbal.frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controlherbal.data.database.PlantTask
import com.example.controlherbal.data.database.TaskStatus
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Data structure representing a day cell in the calendar grid.
 */
data class CalendarDay(
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val year: Int,
    val month: Int, // 0-based
    val dateMillis: Long,
    val hasWaterTask: Boolean = false,
    val hasSunTask: Boolean = false,
    val hasLeafTask: Boolean = false,
    val hasOverdueTask: Boolean = false
)

/**
 * Predefined valid plant care task types with custom vector icons.
 */
data class PredefinedTaskOption(
    val title: String,
    val vectorIcon: ImageVector,
    val defaultDescription: String
)

val PREDEFINED_CARE_TASKS = listOf(
    PredefinedTaskOption("Riego", HerbalIcons.WaterDrop, "Riego profundo según necesidades"),
    PredefinedTaskOption("Fertilizar", HerbalIcons.Plant, "Aplicar fertilizante o abono"),
    PredefinedTaskOption("Poner al sol", HerbalIcons.Sun, "Exposición a luz solar directa/indirecta"),
    PredefinedTaskOption("Mover a sombra", HerbalIcons.Sun, "Proteger de la luz solar intensa"),
    PredefinedTaskOption("Poda", HerbalIcons.Plant, "Poda de mantenimiento o retirar hojas secas"),
    PredefinedTaskOption("Limpieza", HerbalIcons.Plant, "Limpiar polvo acumulado en hojas")
)

/**
 * Pixel-perfect CalendarScreen implementation matching the user's design mockup aesthetics.
 * The entire page scrolls seamlessly vertically, allowing task list navigation to scroll the calendar upwards.
 */
@Composable
fun CalendarScreen(
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { SensorDatabase.getInstance(context) }

    var calendarDate by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDayNumber by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    var plantsList by remember { mutableStateOf<List<Plant>>(emptyList()) }
    var tasksList by remember { mutableStateOf<List<PlantTask>>(emptyList()) }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Seed default tasks if DB tasks are empty
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            plantsList = db.plantDao().getAll()
            if (plantsList.isEmpty()) {
                val defaultPlant = Plant(name = "Menta", type = "Hierbas y Especias", environment = "Sombra", isSelected = true)
                db.plantDao().insert(defaultPlant)
                plantsList = db.plantDao().getAll()
            }

            val tasks = db.plantTaskDao().observeAllTasks()
            tasks.collect { currentTasks ->
                if (currentTasks.isEmpty() && plantsList.isNotEmpty()) {
                    val pId = plantsList.first().id
                    val now = System.currentTimeMillis()
                    val cal = Calendar.getInstance()

                    // Today 5:30 PM
                    cal.set(Calendar.HOUR_OF_DAY, 17)
                    cal.set(Calendar.MINUTE, 30)
                    val todayDue = cal.timeInMillis

                    // Seed 2 default tasks matching the mockup exactly
                    db.plantTaskDao().insertTask(
                        PlantTask(
                            plantId = pId,
                            title = "Riego",
                            description = "5:30 p. m.",
                            dueAt = todayDue,
                            status = TaskStatus.PENDING
                        )
                    )

                    db.plantTaskDao().insertTask(
                        PlantTask(
                            plantId = pId,
                            title = "Fertilizar",
                            description = "Cualquier momento del día",
                            dueAt = todayDue,
                            status = TaskStatus.COMPLETED,
                            completedAt = now
                        )
                    )
                } else {
                    tasksList = currentTasks
                }
            }
        }
    }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es", "ES")) }
    val formattedMonthYear = remember(calendarDate.timeInMillis) {
        val raw = monthYearFormat.format(calendarDate.time)
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "ES")) else it.toString() }
    }

    val daysInGrid = remember(calendarDate.timeInMillis, tasksList) {
        calculateCalendarDays(calendarDate, tasksList)
    }

    val selectedDateCalendar = remember(calendarDate.timeInMillis, selectedDayNumber) {
        val cal = calendarDate.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, selectedDayNumber.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        cal
    }

    val selectedDateText = remember(selectedDateCalendar.timeInMillis) {
        val isToday = isSameDay(selectedDateCalendar, Calendar.getInstance())
        val dayName = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES")).format(selectedDateCalendar.time)
        val formattedDayName = dayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "ES")) else it.toString() }
        if (isToday) "Hoy · $formattedDayName" else formattedDayName
    }

    val overdueCount = remember(tasksList) {
        val now = System.currentTimeMillis()
        tasksList.count { it.status == TaskStatus.PENDING && it.dueAt < now }
    }

    val plantsMap = remember(plantsList) {
        plantsList.associateBy { it.id }
    }

    // Main dark container background with full vertical scrolling so tasks scroll calendar upwards
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2F2F31))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP HEADER: Title "Calendario" + "+ Tarea" Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendario",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp,
                    color = Color.White
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Button to open HistoryLogScreen (Registro Diario / Semanal / Mensual)
                    Button(
                        onClick = {
                            context.startActivity(android.content.Intent(context, com.example.controlherbal.frontend.activities.plant.HistoryActivity::class.java))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38383B)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Registros",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = { showAddTaskDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF288B46)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "+ Tarea",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // 2. MONTH NAVIGATION: < Noviembre 2026 >
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newCal = calendarDate.clone() as Calendar
                        newCal.add(Calendar.MONTH, -1)
                        calendarDate = newCal
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Mes anterior",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = formattedMonthYear,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 22.sp,
                    color = Color.White
                )

                IconButton(
                    onClick = {
                        val newCal = calendarDate.clone() as Calendar
                        newCal.add(Calendar.MONTH, 1)
                        calendarDate = newCal
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Siguiente mes",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 3. CALENDAR CONTAINER CARD (#252527)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Weekday headers: L M M J V S D
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val weekdays = listOf("L", "M", "M", "J", "V", "S", "D")
                        weekdays.forEach { day ->
                            Text(
                                text = day,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Days Grid (6 rows of 7 columns)
                    val rows = daysInGrid.chunked(7)
                    rows.forEach { rowDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            rowDays.forEach { cell ->
                                val isSelected = cell.isCurrentMonth && cell.dayNumber == selectedDayNumber

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 2.dp)
                                        .clickable(enabled = cell.isCurrentMonth) {
                                            selectedDayNumber = cell.dayNumber
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color(0xFF458C4F) else Color.Transparent
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cell.dayNumber.toString(),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 15.sp,
                                            color = when {
                                                isSelected -> Color.White
                                                cell.isCurrentMonth -> Color.White
                                                else -> Color(0xFF555557)
                                            }
                                        )
                                    }

                                    // Task indicator icons below day number
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(14.dp)
                                    ) {
                                        if (cell.isCurrentMonth) {
                                            if (cell.hasWaterTask) {
                                                Icon(
                                                    imageVector = HerbalIcons.WaterDrop,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                            if (cell.hasSunTask || cell.hasLeafTask) {
                                                Icon(
                                                    imageVector = HerbalIcons.Plant,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                            if (cell.hasOverdueTask) {
                                                Text("▲", fontSize = 9.sp, color = Color(0xFFD84315))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. DAY TASKS STATUS HEADER: "Hoy · domingo 8 de noviembre" + Overdue Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedDateText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (overdueCount > 0) {
                    Surface(
                        color = Color(0xFFD84315),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "▲ $overdueCount vencida",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF288B46),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "✓ Al día",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 5. TASKS LIST FOR SELECTED DAY (Inline scrollable items within page)
            val tasksForSelectedDay = remember(tasksList, selectedDateCalendar.timeInMillis) {
                tasksList.filter { isSameDayMillis(it.dueAt, selectedDateCalendar.timeInMillis) }
            }

            if (tasksForSelectedDay.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay tareas registradas para este día.",
                        color = Color(0xFF888888),
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    tasksForSelectedDay.forEach { task ->
                        val plantName = plantsMap[task.plantId]?.name ?: "[Nombre de la planta]"
                        CalendarTaskItemCard(
                            task = task,
                            plantName = plantName,
                            onComplete = { taskId ->
                                scope.launch(Dispatchers.IO) {
                                    db.plantTaskDao().completeTask(taskId, System.currentTimeMillis())
                                }
                            },
                            onSkip = { taskId ->
                                scope.launch(Dispatchers.IO) {
                                    db.plantTaskDao().skipTask(taskId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 6. PREDEFINED ADD TASK MODAL DIALOG WITH CUSTOM STYLED TIME PICKER
    if (showAddTaskDialog) {
        PredefinedAddTaskModalDialog(
            plants = plantsList,
            selectedDateMillis = selectedDateCalendar.timeInMillis,
            onDismiss = { showAddTaskDialog = false },
            onSave = { title, description, plantId, dueAtMillis ->
                scope.launch(Dispatchers.IO) {
                    db.plantTaskDao().insertTask(
                        PlantTask(
                            plantId = plantId,
                            title = title,
                            description = description,
                            dueAt = dueAtMillis,
                            status = TaskStatus.PENDING
                        )
                    )
                    showAddTaskDialog = false
                }
            }
        )
    }
}

/**
 * Task item card component matching the exact mockup style.
 */
@Composable
fun CalendarTaskItemCard(
    task: PlantTask,
    plantName: String,
    onComplete: (Long) -> Unit,
    onSkip: (Long) -> Unit
) {
    val isCompleted = task.status == TaskStatus.COMPLETED

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Circle Icon Container (#38383B)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38383B)),
                contentAlignment = Alignment.Center
            ) {
                val titleLower = task.title.lowercase()
                when {
                    titleLower.contains("riego") || titleLower.contains("agua") -> {
                        Icon(
                            imageVector = HerbalIcons.WaterDrop,
                            contentDescription = "Riego",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    titleLower.contains("fertiliz") || titleLower.contains("abono") -> {
                        Icon(
                            imageVector = HerbalIcons.Plant,
                            contentDescription = "Fertilizar",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    titleLower.contains("sol") || titleLower.contains("luz") -> {
                        Icon(
                            imageVector = HerbalIcons.Sun,
                            contentDescription = "Sol",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = HerbalIcons.Plant,
                            contentDescription = "Tarea",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Middle Column Text
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = task.description ?: "5:30 p. m.",
                    fontSize = 13.sp,
                    color = Color(0xFFCCCCCC)
                )
                Text(
                    text = "[$plantName]",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }

            // Right Action Buttons / Status Badge
            if (isCompleted) {
                Surface(
                    color = Color(0xFF288B46),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "✓ Completada",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkmark complete button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF288B46))
                            .clickable { onComplete(task.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Skip button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38383B))
                            .clickable { onSkip(task.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Saltar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Predefined Task Creation Dialog styled matching app dark theme.
 * Restricts tasks to valid botanical/care options and custom vector icons.
 * Features an analog clock hands dial / keyboard time picker themed in dark aesthetic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredefinedAddTaskModalDialog(
    plants: List<Plant>,
    selectedDateMillis: Long,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, plantId: Int, dueAtMillis: Long) -> Unit
) {
    var selectedTaskOption by remember { mutableStateOf(PREDEFINED_CARE_TASKS.first()) }
    var selectedPlant by remember { mutableStateOf(plants.firstOrNull()) }

    var showTimePickerDialog by remember { mutableStateOf(false) }

    val timePickerState = rememberTimePickerState(
        initialHour = 17,
        initialMinute = 30,
        is24Hour = false
    )

    val timeFormattedText = remember(timePickerState.hour, timePickerState.minute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            set(Calendar.MINUTE, timePickerState.minute)
        }
        SimpleDateFormat("h:mm a", Locale("es", "ES")).format(cal.time)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252527),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Programar Tarea de Cuidado",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Task Type Selection (Custom Vector Icons)
                Text(
                    text = "Tipo de Cuidado:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PREDEFINED_CARE_TASKS.chunked(2).forEach { rowTasks ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowTasks.forEach { taskOpt ->
                                val isSelected = taskOpt == selectedTaskOption
                                Surface(
                                    color = if (isSelected) Color(0xFF288B46) else Color(0xFF38383B),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedTaskOption = taskOpt }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = taskOpt.vectorIcon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = taskOpt.title,
                                            color = Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Select Target Plant
                Text(
                    text = "Planta Destino:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                if (plants.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        plants.take(3).forEach { plant ->
                            val isSelected = plant.id == selectedPlant?.id
                            Surface(
                                color = if (isSelected) Color(0xFF288B46) else Color(0xFF38383B),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPlant = plant }
                            ) {
                                Text(
                                    text = plant.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Time Display Card opening the Dark Analog Clock Hands Picker
                Text(
                    text = "Hora de Realización:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Surface(
                    color = Color(0xFF38383B),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePickerDialog = true }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Hora: $timeFormattedText",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cambiar 🕒",
                            color = Color(0xFF81C784),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val plantId = selectedPlant?.id ?: 1
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = selectedDateMillis
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    onSave(
                        selectedTaskOption.title,
                        timeFormattedText,
                        plantId,
                        cal.timeInMillis
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF288B46))
            ) {
                Text("Programar Tarea", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCCCCCC))
            }
        }
    )

    // Dark Material Analog Clock Hands / Dial TimePicker Dialog
    if (showTimePickerDialog) {
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            containerColor = Color(0xFF252527),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Seleccionar Hora",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = Color(0xFF38383B),
                            clockDialSelectedContentColor = Color.White,
                            clockDialUnselectedContentColor = Color(0xFFCCCCCC),
                            selectorColor = Color(0xFF288B46),
                            containerColor = Color(0xFF252527),
                            periodSelectorBorderColor = Color(0xFF81C784),
                            periodSelectorSelectedContainerColor = Color(0xFF288B46),
                            periodSelectorUnselectedContainerColor = Color(0xFF38383B),
                            periodSelectorSelectedContentColor = Color.White,
                            periodSelectorUnselectedContentColor = Color(0xFFCCCCCC),
                            timeSelectorSelectedContainerColor = Color(0xFF288B46),
                            timeSelectorUnselectedContainerColor = Color(0xFF38383B),
                            timeSelectorSelectedContentColor = Color.White,
                            timeSelectorUnselectedContentColor = Color(0xFFCCCCCC)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTimePickerDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF288B46))
                ) {
                    Text("Aceptar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancelar", color = Color(0xFFCCCCCC))
                }
            }
        )
    }
}

/**
 * Helper to calculate calendar grid day cells.
 */
private fun calculateCalendarDays(calendarDate: Calendar, tasks: List<PlantTask>): List<CalendarDay> {
    val result = mutableListOf<CalendarDay>()

    val cal = calendarDate.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    // Day of week for day 1 (1 = Sunday, 2 = Monday ... 7 = Saturday in Java Calendar)
    val firstDayOfWeekJava = cal.get(Calendar.DAY_OF_WEEK)
    // Convert to Monday=0, Tuesday=1 ... Sunday=6
    val firstDayIndex = (firstDayOfWeekJava + 5) % 7

    // Previous month days fill
    val prevCal = cal.clone() as Calendar
    prevCal.add(Calendar.MONTH, -1)
    val maxDaysPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val currentYear = cal.get(Calendar.YEAR)
    val currentMonth = cal.get(Calendar.MONTH)

    for (i in (maxDaysPrevMonth - firstDayIndex + 1)..maxDaysPrevMonth) {
        val tempCal = prevCal.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, i)
        result.add(
            CalendarDay(
                dayNumber = i,
                isCurrentMonth = false,
                year = tempCal.get(Calendar.YEAR),
                month = tempCal.get(Calendar.MONTH),
                dateMillis = tempCal.timeInMillis
            )
        )
    }

    // Current month days
    val maxDaysCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val now = System.currentTimeMillis()

    for (i in 1..maxDaysCurrentMonth) {
        val tempCal = cal.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, i)
        val dayMillis = tempCal.timeInMillis

        val dayTasks = tasks.filter { isSameDayMillis(it.dueAt, dayMillis) }
        val hasWater = dayTasks.any { it.title.lowercase().contains("riego") || it.title.lowercase().contains("agua") }
        val hasSun = dayTasks.any { it.title.lowercase().contains("sol") || it.title.lowercase().contains("luz") }
        val hasLeaf = dayTasks.any { it.title.lowercase().contains("fertiliz") || it.title.lowercase().contains("abono") }
        val hasOverdue = dayTasks.any { it.status == TaskStatus.PENDING && it.dueAt < now }

        result.add(
            CalendarDay(
                dayNumber = i,
                isCurrentMonth = true,
                year = currentYear,
                month = currentMonth,
                dateMillis = dayMillis,
                hasWaterTask = hasWater,
                hasSunTask = hasSun,
                hasLeafTask = hasLeaf,
                hasOverdueTask = hasOverdue
            )
        )
    }

    // Next month days fill to reach multiple of 7 (up to 42 cells)
    val remaining = (42 - result.size) % 7
    val totalNeeded = if (result.size + remaining < 35) 35 - result.size else remaining
    val nextCal = cal.clone() as Calendar
    nextCal.add(Calendar.MONTH, 1)

    for (i in 1..totalNeeded) {
        val tempCal = nextCal.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, i)
        result.add(
            CalendarDay(
                dayNumber = i,
                isCurrentMonth = false,
                year = tempCal.get(Calendar.YEAR),
                month = tempCal.get(Calendar.MONTH),
                dateMillis = tempCal.timeInMillis
            )
        )
    }

    return result
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isSameDayMillis(millis1: Long, millis2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
    return isSameDay(cal1, cal2)
}

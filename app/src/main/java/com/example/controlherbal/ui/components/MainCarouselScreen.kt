package com.example.controlherbal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.controlherbal.ui.viewmodel.SensorViewModel
import kotlinx.coroutines.launch

/**
 * Pantalla principal unificada en carrusel (HorizontalPager) de 4 secciones con navegación fluida
 * y efecto líquido (Metaball) continuo en LiquidNavBarFloat, sin cambiar de actividad.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainCarouselScreen(
    onOpenDrawer: () -> Unit,
    onAddPlant: () -> Unit,
    onWateringClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    val navItems = listOf(
        NavItem(Icons.Filled.Home, "Inicio"),
        NavItem(Icons.Filled.Search, "Buscar"),
        NavItem(Icons.Filled.Star, "Favoritos"),
        NavItem(Icons.Filled.Person, "Perfil")
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
    ) {
        // 1. Capa inferior: Fondo dinámico animado edge-to-edge
        AnimatedGradientBackground(
            modifier = Modifier.fillMaxSize(),
            baseColor = Color(0xFF0A0F1D)
        )

        // 2. Capa superior: Estructura unificada con carrusel y LiquidNavBar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Contenedor superior estático con HorizontalPager (4 secciones sin salir de la pantalla)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> DashboardPanelContent(onOpenDrawer, onAddPlant, onWateringClick)
                        1 -> SensorsHistoryContent()
                        2 -> ComparisonContent()
                        3 -> ProfilePrivacyContent()
                    }
                }
            }

            // 3. Contenedor inferior: LiquidNavBar con seguimiento continuo y fluido del Pager
            val currentSelectedIndex = pagerState.currentPage.toFloat() + pagerState.currentPageOffsetFraction

            LiquidNavBarFloat(
                items = navItems,
                currentSelectedIndex = currentSelectedIndex,
                onSelect = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun DashboardPanelContent(
    onOpenDrawer: () -> Unit,
    onAddPlant: () -> Unit,
    onWateringClick: () -> Unit,
    sensorViewModel: SensorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by sensorViewModel.uiState.collectAsState()
    val ringValues = mapSensorUiStateToHerbalValues(uiState, uiState.isConnected)
    val dimmed = !uiState.isConnected || !ringValues.hasData
    val waterVal = if (ringValues.hasData) ringValues.water else 0f
    val sunVal = if (ringValues.hasData) ringValues.sun else 0f
    val plantVal = if (ringValues.hasData) ringValues.plant else 0f
    val tempVal = if (ringValues.hasData) ringValues.temperature else 0f

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cabecera superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Menú", tint = Color(0xFF2E7D32))
            }
            Text(
                text = "Control Herbal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
            Surface(
                shape = CircleShape,
                color = Color(0xFFE8F5E9),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = onAddPlant) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Añadir", tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Estado de Conexión
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Estado Conexión:", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4B5563))
            Text(
                text = if (uiState.isConnected) "Conectado" else "Desconectado",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (uiState.isConnected) Color(0xFF2E7D32) else Color(0xFF757575)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { /* Conectar */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text(text = "CONECTAR AL DISPOSITIVO", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Componente visual HerbalStatusWidget centrado
        HerbalStatusWidget(
            water = waterVal,
            sun = sunVal,
            plant = plantVal,
            temperature = tempVal,
            dimmed = dimmed,
            modifier = Modifier
                .widthIn(max = 180.dp)
                .fillMaxWidth(0.7f)
                .aspectRatio(1.0f)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta de Datos actuales
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF9FAFB),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Datos actuales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Temperatura", style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
                        Text(text = "-- °C", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Hum. Ambiente", style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
                        Text(text = "-- %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Hum. Tierra", style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
                        Text(text = "-- %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Luz solar", style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
                        Text(text = "-- %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onWateringClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Text(text = "REGISTRAR RIEGO", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun SensorsHistoryContent() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        Text(
            text = "Historial de Sensores",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Consulta el registro detallado diario, semanal y mensual del comportamiento de tus cultivos y sensores.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4B5563)
        )
    }
}

@Composable
fun ComparisonContent() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        Text(
            text = "Comparador de Plantas",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Compara el rendimiento, temperatura, humedad y luz entre dos plantas diferentes seleccionadas.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4B5563)
        )
    }
}

@Composable
fun ProfilePrivacyContent() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        Text(
            text = "Perfil y Privacidad",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Gestión de cuenta, seguridad, contraseña y políticas de privacidad y control de datos.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4B5563)
        )
    }
}

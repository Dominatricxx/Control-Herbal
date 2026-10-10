package com.example.controlherbal.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.controlherbal.ui.theme.ControlHerbalTheme
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Componente reutilizable que dibuja un fondo dinámico animado ("Hyper Dynamic Background")
 * utilizando Canvas, Brush.radialGradient y animaciones trigonométricas fluidas.
 */
@Composable
fun AnimatedGradientBackground(
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF021C14),
) {
    val infiniteTransition = rememberInfiniteTransition(label = "HyperDynamicBackgroundTransition")

    val phase1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase1",
    )

    val phase2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase2",
    )

    val phase3 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase3",
    )

    // Paleta de tonos verde oscuro, verde claro y amarillo un poco oscuro (dorado/oliva)
    val colorDarkGreen = Color(0xFF047857).copy(alpha = 0.40f)
    val colorLightGreen = Color(0xFF34D399).copy(alpha = 0.35f)
    val colorDarkYellow = Color(0xFFCA8A04).copy(alpha = 0.30f)
    val colorForest = Color(0xFF064E3B).copy(alpha = 0.35f)

    Box(modifier = modifier.background(baseColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val x1 = width * 0.3f + (width * 0.2f) * sin(phase1.value)
            val y1 = height * 0.25f + (height * 0.15f) * cos(phase1.value * 0.8f)

            val x2 = width * 0.7f + (width * 0.25f) * cos(phase2.value)
            val y2 = height * 0.6f + (height * 0.2f) * sin(phase2.value * 0.7f)

            val x3 = width * 0.5f + (width * 0.3f) * sin(phase3.value * 1.2f)
            val y3 = height * 0.85f + (height * 0.15f) * cos(phase3.value)

            // Círculo 1: Verde oscuro superior izquierdo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorDarkGreen, Color.Transparent),
                    center = Offset(x1, y1),
                    radius = width * 0.75f,
                ),
                radius = width * 0.75f,
                center = Offset(x1, y1),
            )

            // Círculo 2: Verde claro y bosque central-derecho
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorLightGreen, colorForest, Color.Transparent),
                    center = Offset(x2, y2),
                    radius = width * 0.85f,
                ),
                radius = width * 0.85f,
                center = Offset(x2, y2),
            )

            // Círculo 3: Amarillo un poco oscuro inferior
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorDarkYellow, Color.Transparent),
                    center = Offset(x3, y3),
                    radius = width * 0.8f,
                ),
                radius = width * 0.8f,
                center = Offset(x3, y3),
            )
        }
    }
}

/**
 * Pantalla principal (`MainContentScreen`) integrada con el sistema completo y ViewModel:
 * 1. Contenedor superior scrolleable con toda la información y datos reales en vivo.
 * 2. Contenedor inferior con botones de acción y navegación funcionales.
 */
@Composable
fun MainContentScreen(
    temp: Double = 0.0,
    hum: Double = 0.0,
    soil: Double = 0.0,
    luz: Double = 0.0,
    irh: Double = 100.0,
    seq: Double = 0.0,
    somb: Double = 0.0,
    isConnected: Boolean = false,
    isLinking: Boolean = false,
    isDay: Boolean = true,
    recommendation: String = "Riego recomendado: --",
    onMenuClick: () -> Unit = {},
    onAddPlantClick: () -> Unit = {},
    onConnectClick: () -> Unit = {},
    onWateringClick: () -> Unit = {},
    onPanelClick: () -> Unit = {},
    onSensorsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val locale = Locale.getDefault()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12)),
    ) {
        // Fondo dinámico animado en la capa inferior
        AnimatedGradientBackground(
            modifier = Modifier.fillMaxSize(),
            baseColor = Color(0xFF070A12),
        )

        // Estructura con márgenes externos para que el fondo encuadre toda la pantalla
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Contenedor superior (Dashboard principal con scroll)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.96f),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                ) {
                    // Cabecera superior
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú",
                                tint = Color(0xFF2E7D32),
                            )
                        }

                        Text(
                            text = "Control Herbal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                        )

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onAddPlantClick() },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Añadir",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Estado de Conexión (Texto naranja si sincroniza, sin contenedor rojo)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Estado Conexión:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4B5563),
                        )
                        Text(
                            text = when {
                                isConnected -> "Conectado"
                                isLinking -> "Sincronizando..."
                                else -> "Desconectado"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isConnected -> Color(0xFF10B981)
                                isLinking -> Color(0xFFF59E0B)
                                else -> Color(0xFFDC2626)
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Botón Conectar al Dispositivo
                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                        ),
                    ) {
                        Text(
                            text = "CONECTAR AL DISPOSITIVO",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Barra de Riesgo / Alerta
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFE5E7EB),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isConnected) "Sistema Estable" else "Riesgo detectado",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) Color(0xFF10B981) else Color(0xFFDC2626),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Última actualización: En vivo",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tarjeta de Datos actuales (Telemetría en vivo)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF9FAFB),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Datos actuales",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                )
                                Text(
                                    text = if (isDay) "(Día)" else "(Noche)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B7280),
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Temperatura", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                                    Text(text = if (temp > 0) String.format(locale, "%.1f °C", temp) else "-- °C", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Hum. Ambiente", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                                    Text(text = if (hum > 0) String.format(locale, "%.1f %%", hum) else "-- %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Hum. Tierra", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                                    Text(text = if (soil > 0) String.format(locale, "%.1f %%", soil) else "-- %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Luz solar", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                                    Text(text = if (luz > 0) String.format(locale, "%.1f %%", luz) else "-- %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(text = "IRH", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                                Text(text = String.format(locale, "%.1f", irh), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón Registrar Riego
                    Button(
                        onClick = onWateringClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                        ),
                    ) {
                        Text(
                            text = "REGISTRAR RIEGO",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tarjeta de Predicción
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF9FAFB),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "Predicción",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recommendation,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                            )
                            Text(
                                text = "Próximo riego estimado: ${seq.toInt()}h",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Sequía estimada: ${seq.toInt()}h", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4B5563))
                            Text(text = "Sombra necesaria: ${somb.toInt()}h", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4B5563))
                        }
                    }
                }
            }

            // Contenedor inferior (Barra de botones de acción)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .clickable { onPanelClick() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Panel",
                            tint = Color(0xFF10B981),
                        )
                        Text(
                            text = "Panel",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .clickable { onSensorsClick() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Sensores",
                            tint = Color(0xFF6B7280),
                        )
                        Text(
                            text = "Sensores",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6B7280),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 891)
@Composable
fun MainContentScreenPreview() {
    ControlHerbalTheme {
        MainContentScreen(modifier = Modifier.fillMaxSize())
    }
}

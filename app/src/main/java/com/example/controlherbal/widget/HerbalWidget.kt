package com.example.controlherbal.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.controlherbal.R
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import kotlinx.coroutines.runBlocking
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.Button
import com.example.controlherbal.MainActivity
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll

/**
 * Utilidad para forzar la actualización de todos los widgets desde el servicio o la app
 */
object HerbalWidgetManager {
    suspend fun updateWidgets(context: Context) {
        try {
            HerbalWidgetSummary().updateAll(context)
            HerbalWidgetStatus().updateAll(context)
            HerbalWidgetAlert().updateAll(context)
        } catch (e: Exception) {
            android.util.Log.e("HerbalWidget", "Error updating widgets: ${e.message}")
        }
    }
}

/**
 * Widget Principal: Información completa y estado de la planta
 */
class HerbalWidgetSummary : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = SensorDatabase.getInstance(context)
        provideContent {
            GlanceTheme {
                val plant = runBlocking { db.plantDao().getSelectedPlant() }
                val reading = runBlocking { 
                    plant?.let { db.sensorDao().getAllOrderByTimestampDesc(it.id).firstOrNull() }
                }
                UnifiedWidgetContent(plant?.name ?: "Sin Planta", reading)
            }
        }
    }
}

/**
 * Widget de IA: Recomendación y Diagnóstico (Mismo diseño 2x2)
 */
class HerbalWidgetStatus : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = SensorDatabase.getInstance(context)
        provideContent {
            GlanceTheme {
                val plant = runBlocking { db.plantDao().getSelectedPlant() }
                val reading = runBlocking { 
                    plant?.let { db.sensorDao().getAllOrderByTimestampDesc(it.id).firstOrNull() }
                }
                UnifiedWidgetContent(plant?.name ?: "Sin Planta", reading, showAI = true)
            }
        }
    }
}

/**
 * Widget de Alerta: Diseño 2x2 pero con enfoque en alertas
 */
class HerbalWidgetAlert : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = SensorDatabase.getInstance(context)
        provideContent {
            GlanceTheme {
                val plant = runBlocking { db.plantDao().getSelectedPlant() }
                val reading = runBlocking { 
                    plant?.let { db.sensorDao().getAllOrderByTimestampDesc(it.id).firstOrNull() }
                }
                UnifiedWidgetContent(plant?.name ?: "Sin Planta", reading, isAlertVersion = true)
            }
        }
    }
}

@Composable
private fun UnifiedWidgetContent(name: String, r: SensorReading?, showAI: Boolean = false, isAlertVersion: Boolean = false) {
    val primaryGreen = Color(0xFF2E7D32)
    val alertRed = Color(0xFFD32F2F)
    val irhValue = r?.irh ?: 0.0
    val isCritical = irhValue > 50

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.rounded_background))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.mipmap.ic_launcher_round),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp)
            )
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    text = name,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorProvider(if (isAlertVersion && isCritical) alertRed else primaryGreen))
                )
                if (showAI && r != null) {
                    Text(
                        text = "Herbal AI: ${r.action.take(20)}...",
                        style = TextStyle(fontSize = 9.sp, color = ColorProvider(Color.Gray))
                    )
                } else {
                    Text(
                        text = if (r != null) "Sincronizado" else "Sin datos",
                        style = TextStyle(fontSize = 9.sp, color = ColorProvider(Color.Gray))
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        if (r != null) {
            // Grid 2x2
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                SensorItem(modifier = GlanceModifier.defaultWeight(), label = "TEMP", value = "${String.format("%.1f", r.temperature)}°", icon = "🌡️")
                SensorItem(modifier = GlanceModifier.defaultWeight(), label = "HUM", value = "${String.format("%.1f", r.humidity)}%", icon = "☁️")
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                SensorItem(modifier = GlanceModifier.defaultWeight(), label = "SUELO", value = "${String.format("%.1f", r.soilMoisture)}%", icon = "💧")
                SensorItem(modifier = GlanceModifier.defaultWeight(), label = "LUZ", value = "${String.format("%.0f", r.light)}lx", icon = "☀️")
            }

            Spacer(GlanceModifier.height(8.dp))

            // IRH Bar
            val statusColor = if (isCritical) alertRed else primaryGreen
            Column(
                modifier = GlanceModifier.fillMaxWidth()
                    .background(ColorProvider(statusColor.copy(alpha = 0.1f)))
                    .padding(6.dp),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Text(
                    text = if (isCritical) "⚠️ RIESGO ALTO" else "✅ ESTADO ÓPTIMO",
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ColorProvider(statusColor))
                )
                Text(
                    text = "IRH: ${String.format("%.1f", irhValue)}%",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorProvider(statusColor))
                )
            }
        } else {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando datos...", style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.Gray)))
            }
        }
    }
}

@Composable
private fun SensorItem(modifier: GlanceModifier, label: String, value: String, icon: String) {
    Column(
        modifier = modifier.padding(2.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(text = icon, style = TextStyle(fontSize = 10.sp))
            Spacer(GlanceModifier.width(2.dp))
            Text(text = value, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
        Text(text = label, style = TextStyle(fontSize = 8.sp, color = ColorProvider(Color.Gray)))
    }
}

// Receivers
class HerbalWidgetSummaryReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HerbalWidgetSummary()
}

class HerbalWidgetStatusReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HerbalWidgetStatus()
}

class HerbalWidgetAlertReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HerbalWidgetAlert()
}

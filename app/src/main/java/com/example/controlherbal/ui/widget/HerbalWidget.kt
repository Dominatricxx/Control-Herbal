package com.example.controlherbal.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.controlherbal.R
import com.example.controlherbal.common.SecureLogger
import com.example.controlherbal.common.SecurityUtils
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.ui.activities.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

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
            SecureLogger.e("HerbalWidget", "Error al actualizar widgets: ${e.javaClass.simpleName}")
        }
    }
}

/** Lee la planta seleccionada y su última lectura; el nombre se normaliza antes de mostrarse. */
private suspend fun loadWidgetData(context: Context): Pair<String, SensorReading?> =
    withContext(Dispatchers.IO) {
        val db = SensorDatabase.getInstance(context)
        val plant = db.plantDao().getSelectedPlant()
        val reading = plant?.let { db.sensorDao().getAllOrderByTimestampDesc(it.id).firstOrNull() }
        SecurityUtils.sanitizeText(plant?.name ?: "Sin Planta") to reading
    }

/**
 * Widget Principal: Información completa y estado de la planta
 */
class HerbalWidgetSummary : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Los datos se leen aquí (corrutina de Glance, fuera del hilo principal) y no con
        // runBlocking dentro de la composición, que podía bloquear la UI.
        val (safeName, reading) = loadWidgetData(context)
        provideContent {
            GlanceTheme {
                UnifiedWidgetContent(safeName, reading)
            }
        }
    }
}

/**
 * Widget de IA: Recomendación y Diagnóstico (Mismo diseño 2x2)
 */
class HerbalWidgetStatus : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Los datos se leen aquí (corrutina de Glance, fuera del hilo principal) y no con
        // runBlocking dentro de la composición, que podía bloquear la UI.
        val (safeName, reading) = loadWidgetData(context)
        provideContent {
            GlanceTheme {
                UnifiedWidgetContent(safeName, reading, showAI = true)
            }
        }
    }
}

/**
 * Widget de Alerta: Diseño 2x2 con enfoque en alertas
 */
class HerbalWidgetAlert : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Los datos se leen aquí (corrutina de Glance, fuera del hilo principal) y no con
        // runBlocking dentro de la composición, que podía bloquear la UI.
        val (safeName, reading) = loadWidgetData(context)
        provideContent {
            GlanceTheme {
                UnifiedWidgetContent(safeName, reading, isAlertVersion = true)
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
                    val safeAction = SecurityUtils.sanitizeText(r.action, 20)
                    Text(
                        text = "Herbal AI: $safeAction...",
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
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                SensorItem(modifier = GlanceModifier.defaultWeight(), label = "TEMP", value = "${String.format(Locale.getDefault(), "%.1f", r.temperature)}°", icon = "🌡️")
                SensorItem(modifier = GlanceModifier.defaultWeight(), label = "HUM", value = "${String.format(Locale.getDefault(), "%.1f", r.humidity)}%", icon = "☁️")
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                SensorItem(modifier = GlanceModifier.defaultWeight(), label = "SUELO", value = "${String.format(Locale.getDefault(), "%.1f", r.soilMoisture)}%", icon = "💧")
                SensorItem(modifier = GlanceModifier.defaultWeight(), label = "LUZ", value = "${String.format(Locale.getDefault(), "%.0f", r.light)}lx", icon = "☀️")
            }

            Spacer(GlanceModifier.height(8.dp))

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
                    text = "IRH: ${String.format(Locale.getDefault(), "%.1f", irhValue)}%",
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

// Los receivers deben estar exportados para que el launcher envíe APPWIDGET_UPDATE. No leen
// ningún dato del Intent: solo vuelven a dibujar el contenido desde la base local, así que un
// broadcast falso no puede inyectar datos ni provocar acciones (ver docs/CAMBIOS-SEGURIDAD.md).
class HerbalWidgetSummaryReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HerbalWidgetSummary()
}

class HerbalWidgetStatusReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HerbalWidgetStatus()
}

class HerbalWidgetAlertReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HerbalWidgetAlert()
}

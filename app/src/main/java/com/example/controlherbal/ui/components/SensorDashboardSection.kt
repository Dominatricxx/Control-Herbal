package com.example.controlherbal.ui.components

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.controlherbal.R
import java.util.Locale

/**
 * Sección de tarjetas animadas de monitoreo en el Dashboard (Jetpack Compose).
 */
@Composable
fun SensorDashboardSection(
    temp: Double,
    hum: Double,
    soil: Double,
    luz: Double,
    modifier: Modifier = Modifier,
    isDay: Boolean = true
) {
    val locale = Locale.getDefault()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📊 Telemetría en Vivo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (isDay) "☀️ Día" else "🌙 Noche",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fila 1: Temperatura y Humedad Ambiente
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedSensorCard(
                title = "Temperatura",
                value = if (temp > 0) String.format(locale, "%.1f °C", temp) else "-- °C",
                modifier = Modifier.weight(1f),
                statusText = when {
                    temp == 0.0 -> "Esperando datos"
                    temp < 15.0 -> "Clima frío"
                    temp in 15.0..30.0 -> "Rango óptimo"
                    else -> "Calor elevado"
                },
                gifResId = R.raw.temp_animated,
                fallbackIcon = "🌡️",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )

            AnimatedSensorCard(
                title = "Hum. Ambiente",
                value = if (hum > 0) String.format(locale, "%.1f %%", hum) else "-- %",
                modifier = Modifier.weight(1f),
                statusText = when {
                    hum == 0.0 -> "Esperando datos"
                    hum < 40.0 -> "Humedad baja"
                    hum in 40.0..80.0 -> "Humedad ideal"
                    else -> "Humedad alta"
                },
                gifResId = R.raw.humidity_animated,
                fallbackIcon = "💧",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fila 2: Humedad Suelo y Luz Solar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedSensorCard(
                title = "Hum. Suelo",
                value = if (soil > 0) String.format(locale, "%.1f %%", soil) else "-- %",
                modifier = Modifier.weight(1f),
                statusText = when {
                    soil == 0.0 -> "Esperando datos"
                    soil < 30.0 -> "Riego necesario"
                    soil in 30.0..70.0 -> "Suelo óptimo"
                    else -> "Suelo saturado"
                },
                gifResId = R.raw.soil_animated,
                fallbackIcon = "🪴",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )

            AnimatedSensorCard(
                title = "Luz Solar",
                value = if (luz > 0) String.format(locale, "%.1f %%", luz) else "-- %",
                modifier = Modifier.weight(1f),
                statusText = when {
                    luz == 0.0 -> "Sombra"
                    luz < 30.0 -> "Luz tenue"
                    else -> "Sol directo"
                },
                gifResId = R.raw.sun_animated,
                fallbackIcon = "☀️",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

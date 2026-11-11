package com.example.controlherbal.frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controlherbal.common.accessibility.StatusLevel
import com.example.controlherbal.frontend.style.HerbalDesignTokens

enum class ConnectionState {
    CONNECTED, CONNECTING, DISCONNECTED
}

fun irhColor(p: Float): Color {
    val safeP = if (p.isNaN() || p.isInfinite()) 0f else p
    return lerpPiecewiseColor(safeP.coerceIn(0f, 1f), HerbalDesignTokens.IrhCurve.colors)
}

fun lerpPiecewiseColor(p: Float, points: List<Pair<Float, Color>>): Color {
    if (points.isEmpty()) return Color.Green
    val clampedP = if (p.isNaN() || p.isInfinite()) 0f else p.coerceIn(0f, 1f)
    if (clampedP <= points.first().first) return points.first().second
    if (clampedP >= points.last().first) return points.last().second

    for (i in 0 until points.size - 1) {
        val (p1, c1) = points[i]
        val (p2, c2) = points[i + 1]
        if (clampedP in p1..p2) {
            if (p2 == p1) return c1
            val t = (clampedP - p1) / (p2 - p1)
            val red = c1.red + t * (c2.red - c1.red)
            val green = c1.green + t * (c2.green - c1.green)
            val blue = c1.blue + t * (c2.blue - c1.blue)
            return Color(red, green, blue)
        }
    }
    return points.last().second
}

fun irhToStatusLevel(irh: Float): StatusLevel {
    val safeIrh = if (irh.isNaN() || irh.isInfinite()) 0f else irh
    return when {
        safeIrh <= 25f -> StatusLevel.OK
        safeIrh <= 50f -> StatusLevel.WARN
        safeIrh <= 75f -> StatusLevel.WARN
        else -> StatusLevel.CRITICAL
    }
}

fun irhStatusLabel(irh: Float): String {
    val safeIrh = if (irh.isNaN() || irh.isInfinite()) 0f else irh
    return when {
        safeIrh <= 25f -> "Óptimo"
        safeIrh <= 50f -> "Atención"
        safeIrh <= 75f -> "Advertencia"
        else -> "Riesgo"
    }
}

/**
 * Convenience overload for Boolean connection status.
 */
@Composable
fun HerbalIrhRing(
    irhValue: Float?,
    isConnected: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val state = if (isConnected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    HerbalIrhRing(
        irhValue = irhValue,
        connectionState = state,
        isDarkTheme = isDarkTheme,
        modifier = modifier,
        animate = animate
    )
}

/**
 * Composable para el anillo de Índice de Riesgo Hídrico (IRH) de 76dp.
 * Si no hay datos (hasData = false), muestra "--%" y el anillo en gris sin color.
 */
@Composable
fun HerbalIrhRing(
    irhValue: Float?,
    connectionState: ConnectionState,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val hasData = irhValue != null && !irhValue.isNaN() && !irhValue.isInfinite()
    val clampedIrh = if (hasData) irhValue!!.coerceIn(0f, 100f) else 0f
    val statusLevel = if (hasData) irhToStatusLevel(clampedIrh) else StatusLevel.OK
    val label = if (hasData) irhStatusLabel(clampedIrh) else "Sin datos"

    val badgeBg = if (hasData) {
        when (statusLevel) {
            StatusLevel.OK -> HerbalDesignTokens.Status.optimal
            StatusLevel.WARN -> HerbalDesignTokens.Status.warning
            StatusLevel.CRITICAL -> HerbalDesignTokens.Status.critical
            else -> HerbalDesignTokens.Status.optimal
        }
    } else Color.Gray.copy(alpha = 0.4f)

    val (connectionColor, connectionText, connectionBg) = when (connectionState) {
        ConnectionState.CONNECTED -> Triple(Color(0xFF00FF66), "● Conectado", Color(0xFF17703D).copy(alpha = 0.35f))
        ConnectionState.CONNECTING -> Triple(Color(0xFFF6B209), "● Conectando...", Color(0xFFB34700).copy(alpha = 0.35f))
        ConnectionState.DISCONNECTED -> Triple(Color(0xFFD32F2F), "● Desconectado", Color(0xFFB30000).copy(alpha = 0.35f))
    }

    val textColor = if (isDarkTheme) HerbalDesignTokens.Dark.textPrimary else HerbalDesignTokens.Light.textPrimary
    val textSecondaryColor = if (isDarkTheme) HerbalDesignTokens.Dark.textSecondary else HerbalDesignTokens.Light.textSecondary

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HerbalRing(
                percentage = if (hasData) clampedIrh else 0f,
                colorFor = ::irhColor,
                painter = HerbalImageTileset.getPainter("irh"),
                animate = animate && hasData,
                modifier = Modifier.size(76.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column {
                    Text(
                        text = "IRH",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = textSecondaryColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (hasData) "%.0f%%".format(clampedIrh) else "--%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasData) label else "Sin datos",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Dynamic Connection State Pill with indicator light (Green = Connected, Orange = Connecting, Red = Disconnected)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(connectionBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = connectionText,
                color = connectionColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

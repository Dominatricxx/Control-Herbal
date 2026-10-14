package com.example.controlherbal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.first

private val IconoActivo = Color(0xFFFFFFFF)
private val IconoInactivo = Color(0xFF6B7280)
private const val MainSpringStiffness = 220f
private const val MainSpringDamping = 25f
private const val TailSpringStiffness = 100f
private const val TailSpringDamping = 19f
private const val MaxFrameDeltaSeconds = 1f / 30f

data class NavItem(val icono: ImageVector, val descripcion: String)

@Composable
fun LiquidNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidNavBarFloat(
        items = items,
        currentSelectedIndex = selectedIndex.toFloat(),
        onSelect = onSelect,
        modifier = modifier
    )
}

@Composable
fun LiquidNavBarFloat(
    items: List<NavItem>,
    currentSelectedIndex: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Color(0xFF2E7D32),
    unselectedColor: Color = IconoInactivo,
    barBackground: Color = Color.White,
    borderColor: Color = Color(0xFFE5E7EB),
    onSelectionSettled: (() -> Unit)? = null
) {
    val forma = RoundedCornerShape(25.dp)
    var anchoPx by remember { mutableFloatStateOf(0f) }

    val anchoTab = if (items.isEmpty()) 0f else anchoPx / items.size
    val objetivo = anchoTab * (currentSelectedIndex + 0.5f)

    var x by remember { mutableFloatStateOf(0f) }
    var v by remember { mutableFloatStateOf(0f) }
    var xc by remember { mutableFloatStateOf(0f) }
    var vc by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }
    val latestTarget by rememberUpdatedState(objetivo)
    val latestOnSelectionSettled by rememberUpdatedState(onSelectionSettled)

    LaunchedEffect(anchoTab) {
        if (anchoTab <= 0f) return@LaunchedEffect
        if (!isInitialized) {
            x = latestTarget
            xc = latestTarget
            isInitialized = true
        }
        while (true) {
            snapshotFlow { latestTarget }.first { abs(it - x) > 0.1f }
            var lastFrameTimeNanos = 0L
            while (
                abs(latestTarget - x) > 0.5f ||
                abs(x - xc) > 0.5f ||
                abs(v) > 2f ||
                abs(vc) > 2f
            ) {
                withFrameNanos { frameTimeNanos ->
                    val deltaSeconds = if (lastFrameTimeNanos == 0L) {
                        0f
                    } else {
                        ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                            .coerceIn(0f, MaxFrameDeltaSeconds)
                    }
                    lastFrameTimeNanos = frameTimeNanos

                    v += (latestTarget - x) * MainSpringStiffness * deltaSeconds
                    v *= kotlin.math.exp(-MainSpringDamping * deltaSeconds)
                    x += v * deltaSeconds

                    vc += (x - xc) * TailSpringStiffness * deltaSeconds
                    vc *= kotlin.math.exp(-TailSpringDamping * deltaSeconds)
                    xc += vc * deltaSeconds
                }
            }
            x = latestTarget
            xc = latestTarget
            v = 0f
            vc = 0f
            latestOnSelectionSettled?.invoke()
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(50.dp)
            .shadow(12.dp, forma, clip = false)
            .clip(forma)
            .background(barBackground)
            .border(1.dp, borderColor, forma)
            .onSizeChanged { anchoPx = it.width.toFloat() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (anchoTab <= 0f) return@Canvas

            val cy = size.height / 2f
            val mx = x
            val tx = xc
            val d = abs(mx - tx)

            val estiramiento = (abs(objetivo - mx) / anchoTab * 0.18f).coerceAtMost(0.4f)
            val radioBase = 22.dp.toPx()
            val rx = radioBase * (1f + estiramiento + min(abs(v) / 1800f, 0.3f))
            val ry = radioBase * (1f - estiramiento * 0.55f)

            val rCola = 18.dp.toPx() * (0.6f + (d / (anchoTab * 2.5f)).coerceAtMost(0.3f))

            if (d > 1f) {
                val xl = min(mx, tx)
                val xr = max(mx, tx)
                val el = ry * 0.8f
                val er = rCola * 0.8f

                val grosor = (1f - d / (anchoTab * 2.5f)).coerceIn(0.22f, 1f)
                val mitad = rCola * grosor
                val c = max(0f, 2f * mitad - (el + er) / 2f)
                val xm = (xl + xr) / 2f

                val cuello = Path().apply {
                    moveTo(xl, cy - el)
                    quadraticTo(xm, cy - c, xr, cy - er)
                    lineTo(xr, cy + er)
                    quadraticTo(xm, cy + c, xl, cy + el)
                    close()
                }
                drawPath(cuello, selectedColor)
            }

            drawCircle(selectedColor, radius = rCola, center = Offset(tx, cy))
            drawOval(
                color = selectedColor,
                topLeft = Offset(mx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f)
            )
        }

        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, item ->
                val activo = abs(currentSelectedIndex - i) < 0.5f
                val color by animateColorAsState(if (activo) IconoActivo else unselectedColor, label = "color")
                val escala by animateFloatAsState(
                    targetValue = if (activo) 1.15f else 1f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    label = "escala"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icono,
                        contentDescription = item.descripcion,
                        tint = color,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(escala)
                    )
                }
            }
        }
    }
}

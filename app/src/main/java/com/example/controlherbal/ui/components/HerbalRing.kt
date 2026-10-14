package com.example.controlherbal.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Individual progress ring Composable drawn with Canvas.
 * - Rail: full circle, white with 26% opacity.
 * - Progress arc: starts at top (-90°) and advances clockwise, sweep = 360 * percentage / 100.
 * - Color: evaluated dynamically from the animated percentage via colorFor.
 * - Icon: centered, native vector colors (#F2F2F7 with #3A3A40 details via tint = Color.Unspecified), scaling with ring size (35.2%).
 */
@Composable
fun HerbalRing(
    percentage: Float,
    colorFor: (Float) -> Color,
    icon: ImageVector,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    val targetPercentage = percentage.coerceIn(0f, 100f)
    var currentPercentage by remember { mutableFloatStateOf(if (animate) 0f else targetPercentage) }

    LaunchedEffect(targetPercentage) {
        if (animate) {
            currentPercentage = targetPercentage
        } else {
            currentPercentage = targetPercentage
        }
    }

    val animatedPercentage by animateFloatAsState(
        targetValue = currentPercentage,
        animationSpec = if (animate) {
            tween(durationMillis = 800, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 0)
        },
        label = "RingProgressAnimation"
    )

    val evaluatedColor = colorFor(animatedPercentage / 100f)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val ringMaxWidth = maxWidth

        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            // Proportional stroke width based on reference container 190x190 (ring diameter ~77, stroke 10)
            val strokeWidth = diameter * (10f / 77f)
            val radius = (diameter - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // 1. Rail (background circle): white with 26% opacity
            drawCircle(
                color = Color.White.copy(alpha = 0.26f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Progress arc: starts at top (-90°) and advances clockwise, sweep = 360 * percentage / 100
            val sweepAngle = 360f * (animatedPercentage / 100f)
            if (sweepAngle > 0f) {
                drawArc(
                    color = evaluatedColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // 3. Center Icon (scaling with 35.2% of ring width, native vector colors)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(ringMaxWidth * 0.352f)
        )
    }
}

package com.example.controlherbal.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Helper function for piecewise linear interpolation.
 */
fun lerpPiecewise(p: Float, points: List<Pair<Float, Float>>): Float {
    val clamped = p.coerceIn(0f, 1f)
    if (points.isEmpty()) return 0f
    if (clamped <= points.first().first) return points.first().second
    if (clamped >= points.last().first) return points.last().second

    for (i in 0 until points.size - 1) {
        val (p1, v1) = points[i]
        val (p2, v2) = points[i + 1]
        if (clamped in p1..p2) {
            if (p2 == p1) return v1
            val t = (clamped - p1) / (p2 - p1)
            return v1 + t * (v2 - v1)
        }
    }
    return points.last().second
}

fun waterColor(p: Float): Color {
    val clamped = p.coerceIn(0f, 1f)
    val hue = 198f + (216f - 198f) * clamped
    val sat = lerpPiecewise(clamped, listOf(0f to 60f, 0.15f to 67f, 0.5f to 90f, 0.9f to 100f, 1f to 100f)) / 100f
    val light = lerpPiecewise(clamped, listOf(0f to 66f, 0.15f to 59f, 0.5f to 46f, 0.9f to 24f, 1f to 21f)) / 100f
    return Color.hsl(hue, sat, light)
}

fun plantColor(p: Float): Color {
    val clamped = p.coerceIn(0f, 1f)
    val hue = 128f + (144f - 128f) * clamped
    val sat = lerpPiecewise(clamped, listOf(0f to 60f, 0.15f to 67f, 0.5f to 90f, 0.9f to 100f, 1f to 100f)) / 100f
    val light = lerpPiecewise(clamped, listOf(0f to 66f, 0.15f to 59f, 0.5f to 46f, 0.9f to 24f, 1f to 21f)) / 100f
    return Color.hsl(hue, sat, light)
}

fun sunColor(p: Float): Color {
    val clamped = p.coerceIn(0f, 1f)
    val hue = lerpPiecewise(clamped, listOf(0f to 52f, 0.15f to 52f, 0.5f to 50f, 0.9f to 45f, 1f to 44f))
    val sat = lerpPiecewise(clamped, listOf(0f to 80f, 0.15f to 90f, 0.5f to 100f, 0.9f to 100f, 1f to 100f)) / 100f
    val light = lerpPiecewise(clamped, listOf(0f to 68f, 0.15f to 60f, 0.5f to 50f, 0.9f to 40f, 1f to 38f)) / 100f
    return Color.hsl(hue, sat, light)
}

fun temperatureColor(p: Float): Color {
    val clamped = p.coerceIn(0f, 1f)
    val r = lerpPiecewise(clamped, listOf(0f to 110f, 0.25f to 79f, 0.4f to 232f, 0.5f to 245f, 0.7f to 245f, 0.85f to 230f, 1f to 190f)) / 255f
    val g = lerpPiecewise(clamped, listOf(0f to 155f, 0.25f to 134f, 0.4f to 220f, 0.5f to 200f, 0.7f to 140f, 0.85f to 80f, 1f to 25f)) / 255f
    val b = lerpPiecewise(clamped, listOf(0f to 224f, 0.25f to 222f, 0.4f to 150f, 0.5f to 40f, 0.7f to 30f, 0.85f to 35f, 1f to 30f)) / 255f
    return Color(red = r, green = g, blue = b)
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

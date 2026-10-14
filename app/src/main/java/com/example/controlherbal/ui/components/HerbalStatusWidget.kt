package com.example.controlherbal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Convenience overload for preview and basic usage with default parameters.
 */
@Composable
fun HerbalStatusWidget(
    water: Float,
    sun: Float,
    plant: Float,
    temperature: Float,
    modifier: Modifier = Modifier
) {
    HerbalStatusWidget(
        water = water,
        sun = sun,
        plant = plant,
        temperature = temperature,
        modifier = modifier,
        dimmed = false,
        animate = true
    )
}

/**
 * Herbal Status Widget component containing a 2x2 grid of progress rings (Water, Sun, Plant, Temperature).
 * iPhone translucent glass style container with rounded corners (19% / radius 36 relative to 190).
 * NOTE: Draws NO background of its own other than the translucent fill (Color(0x6B787A86)) and
 * 1dp white border at 22% opacity, allowing whatever app background is behind to show through.
 */
@Composable
fun HerbalStatusWidget(
    water: Float,
    sun: Float,
    plant: Float,
    temperature: Float,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    animate: Boolean = true
) {
    val realTemp = temperature / 100f * (TEMP_MAX_C - TEMP_MIN_C) + TEMP_MIN_C
    val contentDesc = String.format(
        Locale.getDefault(),
        "Agua %.0f %%, luz %.0f %%, planta %.0f %%, temperatura %.1f °C",
        water, sun, plant, realTemp
    )

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1.0f)
            .graphicsLayer(alpha = if (dimmed) 0.6f else 1f)
            .clip(RoundedCornerShape(19))
            .background(Color(0x6B787A86)) // rgba(120,122,134) with 42% opacity
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(19))
            .semantics { contentDescription = contentDesc }
    ) {
        val containerWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        // Proportional sizing based on 190x190 reference dimensions:
        // Ring size = 77, outer padding = 13.5, inner gap = 9
        val ringSizeDp = with(LocalDensity.current) { (containerWidthPx * (77f / 190f)).toDp() }
        val paddingDp = with(LocalDensity.current) { (containerWidthPx * (13.5f / 190f)).toDp() }
        val spacingDp = with(LocalDensity.current) { (containerWidthPx * (9f / 190f)).toDp() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingDp),
            verticalArrangement = Arrangement.spacedBy(spacingDp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top row: Water & Sun
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingDp, Alignment.CenterHorizontally)
            ) {
                HerbalRing(
                    percentage = water,
                    colorFor = ::waterColor,
                    icon = HerbalIcons.WaterDrop,
                    modifier = Modifier.size(ringSizeDp),
                    animate = animate
                )
                HerbalRing(
                    percentage = sun,
                    colorFor = ::sunColor,
                    icon = HerbalIcons.Sun,
                    modifier = Modifier.size(ringSizeDp),
                    animate = animate
                )
            }

            // Bottom row: Plant & Temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingDp, Alignment.CenterHorizontally)
            ) {
                HerbalRing(
                    percentage = plant,
                    colorFor = ::plantColor,
                    icon = HerbalIcons.Plant,
                    modifier = Modifier.size(ringSizeDp),
                    animate = animate
                )
                HerbalRing(
                    percentage = temperature,
                    colorFor = ::temperatureColor,
                    icon = HerbalIcons.Thermometer,
                    modifier = Modifier.size(ringSizeDp),
                    animate = animate
                )
            }
        }
    }
}

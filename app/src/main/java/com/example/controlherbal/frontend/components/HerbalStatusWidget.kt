package com.example.controlherbal.frontend.components

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
        hasData = true,
        modifier = modifier,
        dimmed = false,
        animate = true
    )
}

/**
 * Herbal Status Widget component containing a 2x2 grid of progress rings (Water, Sun, Plant, Temperature).
 * If hasData is false or values are null, displays gray ring tracks without color arcs and "--" representations.
 */
@Composable
fun HerbalStatusWidget(
    water: Float?,
    sun: Float?,
    plant: Float?,
    temperature: Float?,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    animate: Boolean = true,
    hasData: Boolean = true
) {
    val realTemp = if (hasData && temperature != null) {
        temperature / 100f * (TEMP_MAX_C - TEMP_MIN_C) + TEMP_MIN_C
    } else 0.0

    val contentDesc = if (hasData) {
        String.format(
            Locale.getDefault(),
            "Agua %.0f %%, luz %.0f %%, planta %.0f %%, temperatura %.1f °C",
            water ?: 0f, sun ?: 0f, plant ?: 0f, realTemp
        )
    } else "Sin datos de sensores"

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1.0f)
            .graphicsLayer(alpha = if (dimmed) 0.6f else 1f)
            .clip(RoundedCornerShape(19))
            .background(Color(0x6B787A86))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(19))
            .semantics { contentDescription = contentDesc }
    ) {
        val containerWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
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
            // Top row: Water & Sun (Using PNG tileset assets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingDp, Alignment.CenterHorizontally)
            ) {
                HerbalRing(
                    percentage = if (hasData && water != null) water else 0f,
                    colorFor = ::waterColor,
                    painter = HerbalImageTileset.getPainter("humedad"),
                    modifier = Modifier.size(ringSizeDp),
                    animate = animate && hasData
                )
                HerbalRing(
                    percentage = if (hasData && sun != null) sun else 0f,
                    colorFor = ::sunColor,
                    painter = HerbalImageTileset.getPainter("sol"),
                    modifier = Modifier.size(ringSizeDp),
                    animate = animate && hasData
                )
            }

            // Bottom row: Plant & Temperature (Using PNG tileset assets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingDp, Alignment.CenterHorizontally)
            ) {
                HerbalRing(
                    percentage = if (hasData && plant != null) plant else 0f,
                    colorFor = ::plantColor,
                    painter = HerbalImageTileset.getPainter("suelo"),
                    modifier = Modifier.size(ringSizeDp),
                    animate = animate && hasData
                )
                HerbalRing(
                    percentage = if (hasData && temperature != null) temperature else 0f,
                    colorFor = ::temperatureColor,
                    painter = HerbalImageTileset.getPainter("temperatura"),
                    modifier = Modifier.size(ringSizeDp),
                    animate = animate && hasData
                )
            }
        }
    }
}

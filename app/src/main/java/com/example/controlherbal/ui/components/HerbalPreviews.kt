package com.example.controlherbal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Preview 1: 3 states (15%, 50%, 90%) on a solid dark neutral background (#1B1B1F).
 */
@Preview(name = "Herbal Status Widget - Dark Background", showBackground = true, backgroundColor = 0xFF1B1B1F, widthDp = 640, heightDp = 240)
@Composable
fun HerbalStatusWidgetDarkPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1B1F))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HerbalStatusWidget(
                    water = 15f,
                    sun = 15f,
                    plant = 15f,
                    temperature = 15f,
                    modifier = Modifier.size(150.dp),
                    dimmed = false,
                    animate = false
                )
                Text(
                    text = "Bajo · 15 %",
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HerbalStatusWidget(
                    water = 50f,
                    sun = 50f,
                    plant = 50f,
                    temperature = 50f,
                    modifier = Modifier.size(150.dp),
                    dimmed = false,
                    animate = false
                )
                Text(
                    text = "Medio · 50 %",
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HerbalStatusWidget(
                    water = 90f,
                    sun = 90f,
                    plant = 90f,
                    temperature = 90f,
                    modifier = Modifier.size(150.dp),
                    dimmed = false,
                    animate = false
                )
                Text(
                    text = "Alto · 90 %",
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Preview 2: 3 states (15%, 50%, 90%) on a solid neutral light background (#D9D9DE).
 */
@Preview(name = "Herbal Status Widget - Light Background", showBackground = true, backgroundColor = 0xFFD9D9DE, widthDp = 640, heightDp = 240)
@Composable
fun HerbalStatusWidgetLightPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD9D9DE))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HerbalStatusWidget(
                    water = 15f,
                    sun = 15f,
                    plant = 15f,
                    temperature = 15f,
                    modifier = Modifier.size(150.dp),
                    dimmed = false,
                    animate = false
                )
                Text(
                    text = "Bajo · 15 %",
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HerbalStatusWidget(
                    water = 50f,
                    sun = 50f,
                    plant = 50f,
                    temperature = 50f,
                    modifier = Modifier.size(150.dp),
                    dimmed = false,
                    animate = false
                )
                Text(
                    text = "Medio · 50 %",
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HerbalStatusWidget(
                    water = 90f,
                    sun = 90f,
                    plant = 90f,
                    temperature = 90f,
                    modifier = Modifier.size(150.dp),
                    dimmed = false,
                    animate = false
                )
                Text(
                    text = "Alto · 90 %",
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Preview 3: Color strips (0% to 100%) for water, sun, plant, and temperature.
 */
@Preview(showBackground = true, backgroundColor = 0xFF1B1B1F, widthDp = 640, heightDp = 200)
@Composable
fun ColorStripsPreview() {
    val funcs = listOf<(Float) -> Color>(
        ::waterColor, ::sunColor, ::plantColor, ::temperatureColor
    )
    Column(
        Modifier.fillMaxSize().background(Color(0xFF1B1B1F)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        funcs.forEach { f ->
            Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))) {
                for (i in 0..100) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(f(i / 100f)))
                }
            }
        }
    }
}

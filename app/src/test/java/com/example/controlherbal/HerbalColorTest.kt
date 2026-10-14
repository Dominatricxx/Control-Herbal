package com.example.controlherbal

import com.example.controlherbal.ui.components.plantColor
import com.example.controlherbal.ui.components.sunColor
import com.example.controlherbal.ui.components.temperatureColor
import com.example.controlherbal.ui.components.waterColor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HerbalColorTest {

    @Test
    fun verifyColorsAndContinuity() {
        val percentages = listOf(0.0f, 0.15f, 0.25f, 0.50f, 0.75f, 0.90f, 1.00f)

        val refAgua = listOf("#74BDDC", "#50ACDC", "#39A2E1", "#0C80DF", "#034EA1", "#00357A", "#002B6B")
        val refSol = listOf("#EFDD6C", "#F5DC3D", "#F7DA2C", "#FFD400", "#DFAE00", "#CC9900", "#C28E00")
        val refPlanta = listOf("#74DC82", "#50DC69", "#39E15B", "#0CDF44", "#03A138", "#007A2E", "#006B2B")
        val refTemp = listOf("#6E9BE0", "#5B8EDF", "#4F86DE", "#F5C828", "#F07820", "#D93E21", "#BE191E")

        for (i in percentages.indices) {
            val p = percentages[i]
            val label = "Color al ${(p * 100).toInt()}%"
            assertTrue("$label para agua", matchesReference(waterColor(p), refAgua[i]))
            assertTrue("$label para sol", matchesReference(sunColor(p), refSol[i]))
            assertTrue("$label para planta", matchesReference(plantColor(p), refPlanta[i]))
            assertTrue("$label para temperatura", matchesReference(temperatureColor(p), refTemp[i]))
        }

        // Check continuity across 101 steps
        var maxWaterDiff = 0
        var maxSunDiff = 0
        var maxPlantDiff = 0
        var maxTempDiff = 0

        for (i in 0 until 100) {
            val p1 = i / 100f
            val p2 = (i + 1) / 100f

            maxWaterDiff = maxOf(maxWaterDiff, diffRgb(waterColor(p1), waterColor(p2)))
            maxSunDiff = maxOf(maxSunDiff, diffRgb(sunColor(p1), sunColor(p2)))
            maxPlantDiff = maxOf(maxPlantDiff, diffRgb(plantColor(p1), plantColor(p2)))
            maxTempDiff = maxOf(maxTempDiff, diffRgb(temperatureColor(p1), temperatureColor(p2)))
        }

        assertTrue("Agua cambia $maxWaterDiff por paso; máximo 5", maxWaterDiff <= 5)
        assertTrue("Sol cambia $maxSunDiff por paso; máximo 5", maxSunDiff <= 5)
        assertTrue("Planta cambia $maxPlantDiff por paso; máximo 5", maxPlantDiff <= 5)
        assertTrue("Temperatura cambia $maxTempDiff por paso; máximo 12", maxTempDiff <= 12)
    }

    private fun matchesReference(color: androidx.compose.ui.graphics.Color, expectedHex: String): Boolean {
        val expected = expectedHex.removePrefix("#").toLong(16)
        val expectedColor = androidx.compose.ui.graphics.Color(
            red = ((expected shr 16) and 0xFF).toFloat() / 255f,
            green = ((expected shr 8) and 0xFF).toFloat() / 255f,
            blue = (expected and 0xFF).toFloat() / 255f
        )
        return diffRgb(color, expectedColor) <= 1
    }

    private fun diffRgb(c1: androidx.compose.ui.graphics.Color, c2: androidx.compose.ui.graphics.Color): Int {
        val r1 = (c1.red * 255).toInt(); val r2 = (c2.red * 255).toInt()
        val g1 = (c1.green * 255).toInt(); val g2 = (c2.green * 255).toInt()
        val b1 = (c1.blue * 255).toInt(); val b2 = (c2.blue * 255).toInt()
        return maxOf(abs(r1 - r2), abs(g1 - g2), abs(b1 - b2))
    }
}

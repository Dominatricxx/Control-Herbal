package com.example.controlherbal.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

private val IconWhite = Color(0xFFF2F2F7)
private val IconCutout = Color(0xFF3A3A40)

private fun ImageVector.Builder.fillPath(d: String, color: Color) =
    addPath(pathData = addPathNodes(d), fill = SolidColor(color))

private fun ImageVector.Builder.strokePath(d: String, color: Color, width: Float) =
    addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(color),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )

private fun herbalIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 54.dp, 54.dp, 54f, 54f).group(
        name = "centered",
        translationX = 2f,
        translationY = 2f
    ) {
        block()
    }.build()

object HerbalIcons {

    val WaterDrop: ImageVector by lazy {
        herbalIcon("WaterDrop") {
            fillPath("M23 0 C31 12 40 20 40 31 A17 17 0 0 1 6 31 C6 20 15 12 23 0 Z", IconWhite)
            strokePath("M15 32 A8 8 0 0 0 23 40", IconCutout, 3f)
        }
    }

    val Sun: ImageVector by lazy {
        herbalIcon("Sun") {
            fillPath("M13 23 A10 10 0 1 1 33 23 A10 10 0 1 1 13 23 Z", IconWhite)
            strokePath(
                "M39 23 H46 M34.3 34.3 L39.3 39.3 M23 39 V46 M11.7 34.3 L6.7 39.3 " +
                "M7 23 H0 M11.7 11.7 L6.7 6.7 M23 7 V0 M34.3 11.7 L39.3 6.7",
                IconWhite, 4f
            )
        }
    }

    val Plant: ImageVector by lazy {
        herbalIcon("Plant") {
            strokePath("M23 45 V25", IconWhite, 4f)
            fillPath("M23 28 C21 15 11 10 2 12 C2 23 11 30 23 28 Z", IconWhite)
            fillPath("M23 24 C23 11 32 2 45 2 C45 15 36 24 23 24 Z", IconWhite)
            strokePath("M8 46 H38", IconWhite, 4f)
        }
    }

    val Thermometer: ImageVector by lazy {
        herbalIcon("Thermometer") {
            fillPath("M16 6 A7 7 0 0 1 23 -1 H23 A7 7 0 0 1 30 6 V30 A7 7 0 0 1 23 37 A7 7 0 0 1 16 30 Z", IconWhite)
            fillPath("M11 37 A12 12 0 1 1 35 37 A12 12 0 1 1 11 37 Z", IconWhite)
            fillPath("M17.5 37 A5.5 5.5 0 1 1 28.5 37 A5.5 5.5 0 1 1 17.5 37 Z", IconCutout)
            fillPath("M20.25 16.75 A2.75 2.75 0 0 1 23 14 H23 A2.75 2.75 0 0 1 25.75 16.75 V35 A2.75 2.75 0 0 1 23 37.75 A2.75 2.75 0 0 1 20.25 35 Z", IconCutout)
        }
    }
}

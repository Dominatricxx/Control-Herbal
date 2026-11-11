package com.example.controlherbal.frontend.components

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

private fun herbalIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 54.dp, 54.dp, 54f, 54f).group(
        name = "centered",
        translationX = 2f,
        translationY = 2f
    ) {
        block()
    }.build()

/**
 * Exact vector representations matching the user's uploaded tileset sheet:
 * Row 0: humedad, sol, suelo, temperatura, irh
 * Row 1: inicio, plantas, agregar, calendario, perfil
 */
object HerbalIcons {

    // 1. humedad (Water drop with internal crescent reflection)
    val WaterDrop: ImageVector by lazy {
        herbalIcon("WaterDrop") {
            fillPath("M23 2 C28 10 38 20 38 31 A15 15 0 0 1 8 31 C8 20 18 10 23 2 Z", IconWhite)
            fillPath("M15 32 C14 27 18 24 21 23 C20 27 17 30 15 32 Z", IconCutout)
        }
    }

    // 2. sol (Sun with central circle and 8 rounded capsule rays)
    val Sun: ImageVector by lazy {
        herbalIcon("Sun") {
            // Central circle
            fillPath("M23 15 A8 8 0 1 1 23 31 A8 8 0 1 1 23 15 Z", IconWhite)
            // 8 rays (capsules)
            fillPath("M23 4 V10", IconWhite) // top
            fillPath("M23 36 V42", IconWhite) // bottom
            fillPath("M10 23 H16", IconWhite) // left
            fillPath("M30 23 H36", IconWhite) // right
            fillPath("M13.8 13.8 L18 18", IconWhite) // top-left
            fillPath("M28 28 L32.2 32.2", IconWhite) // bottom-right
            fillPath("M32.2 13.8 L28 18", IconWhite) // top-right
            fillPath("M18 28 L13.8 32.2", IconWhite) // bottom-left
        }
    }

    // 3. suelo (Sprout with base mound and two leaves)
    val Plant: ImageVector by lazy {
        herbalIcon("Plant") {
            // Mound base
            fillPath("M11 37 C11 33 35 33 35 37 C35 39 11 39 11 37 Z", IconWhite)
            // Stem
            fillPath("M21 23 V36 H25 V23 Z", IconWhite)
            // Left leaf
            fillPath("M23 24 C18 24 10 21 6 15 C10 13 18 16 23 24 Z", IconWhite)
            // Right leaf
            fillPath("M23 24 C28 24 36 21 40 15 C36 13 28 16 23 24 Z", IconWhite)
        }
    }

    // 4. temperatura (Thermometer)
    val Thermometer: ImageVector by lazy {
        herbalIcon("Thermometer") {
            // Outer tube and bulb
            fillPath("M19 4 C19 1 27 1 27 4 V28 C32 31 35 36 35 39 C35 45 30 48 23 48 C16 48 11 45 11 39 C11 36 14 31 19 28 Z", IconWhite)
            // Inner cutout tube and bulb
            fillPath("M21 10 V29 C18 31 15 35 15 39 C15 42 18 44 23 44 C28 44 31 42 31 39 C31 35 28 31 25 29 V10 Z", IconCutout)
        }
    }

    // 5. irh (Shield with leaf and curved vein)
    val IrhShield: ImageVector by lazy {
        herbalIcon("IrhShield") {
            // Shield outer
            fillPath("M23 2 C34 4 41 8 41 16 C41 28 32 37 23 44 C14 37 5 28 5 16 C5 8 12 4 23 2 Z", IconWhite)
            // Leaf inside shield
            fillPath("M23 12 C18 12 13 16 12 24 C17 28 23 28 28 24 C27 16 23 12 23 12 Z", IconCutout)
            // Leaf vein arc
            fillPath("M17 21 C19 19 22 18 25 18 C23 20 21 22 19 24 Z", IconWhite)
        }
    }

    // 6. inicio (House)
    val Home: ImageVector by lazy {
        herbalIcon("Home") {
            // Roof triangle
            fillPath("M23 4 L41 20 H37 V39 H9 V20 H5 Z", IconWhite)
            // Door cutout
            fillPath("M19 27 H27 V39 H19 Z", IconCutout)
        }
    }

    // 7. plantas (Potted plant with two leaves)
    val PlantPot: ImageVector by lazy {
        herbalIcon("PlantPot") {
            // Pot
            fillPath("M13 25 H33 L30 43 H16 Z", IconWhite)
            // Left leaf
            fillPath("M23 25 C18 25 10 20 7 13 C12 11 18 17 23 25 Z", IconWhite)
            // Right leaf
            fillPath("M23 25 C28 25 36 20 39 13 C34 11 28 17 23 25 Z", IconWhite)
        }
    }

    // 8. agregar (Circle plus)
    val Add: ImageVector by lazy {
        herbalIcon("Add") {
            // Outer green circle container
            fillPath("M23 -1 A24 24 0 1 0 23 47 A24 24 0 1 0 23 -1 Z", Color(0xFF4F7348))
            // White inner circle
            fillPath("M23 3 A20 20 0 1 1 23 43 A20 20 0 1 1 23 3 Z", IconWhite)
            // Plus sign
            fillPath("M21 13 H25 V21 H33 V25 H25 V33 H21 V25 H13 V21 H21 Z", Color(0xFF4F7348))
        }
    }

    // 9. calendario (Calendar)
    val Calendar: ImageVector by lazy {
        herbalIcon("Calendar") {
            // Top binding tabs
            fillPath("M13 6 H17 V12 H13 Z M29 6 H33 V12 H29 Z", IconWhite)
            // Main body
            fillPath("M9 12 H37 C40 12 42 14 42 17 V39 C42 42 40 44 37 44 H9 C6 44 4 42 4 39 V17 C4 14 6 12 9 12 Z", IconWhite)
            // Inner cutout box
            fillPath("M10 20 H36 V39 H10 Z", IconCutout)
        }
    }

    // 10. perfil (User profile silhouette)
    val Person: ImageVector by lazy {
        herbalIcon("Person") {
            // Head circle
            fillPath("M23 10 A7 7 0 1 0 23 24 A7 7 0 1 0 23 10 Z", IconWhite)
            // Shoulders body arc
            fillPath("M9 41 C9 31 15 28 23 28 C31 28 37 31 37 41 Z", IconWhite)
        }
    }
}

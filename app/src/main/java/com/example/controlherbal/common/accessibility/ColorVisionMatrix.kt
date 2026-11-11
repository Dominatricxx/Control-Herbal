package com.example.controlherbal.common.accessibility

/**
 * Matriz de transformación de color de 4x5 (compatible con android.graphics.ColorMatrix y
 * androidx.compose.ui.graphics.ColorMatrix) para la simulación y adaptación exacta de TODAS las
 * condiciones de visión del color (CVD - Color Vision Deficiency):
 *  1. Protanopía (Ceguera total al rojo)
 *  2. Deuteranopía (Ceguera total al verde)
 *  3. Tritanopía (Ceguera total al azul)
 *  4. Protanomalía (Deficiencia parcial al rojo)
 *  5. Deuteranomalía (Deficiencia parcial al verde)
 *  6. Acromatopsia (Ceguera total del color / Monocromático)
 *  7. Daltonismo Rojo-Verde
 *  8. Daltonismo Azul-Amarillo
 *  9. Alto Contraste
 */
object ColorVisionMatrix {

    // Matriz identidad sin alteración
    val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    // 1. Protanopía (Machado / Viénot LMS color space transformation)
    val PROTANOPIA = floatArrayOf(
        0.56667f, 0.43333f, 0.00000f, 0f, 0f,
        0.55833f, 0.44167f, 0.00000f, 0f, 0f,
        0.00000f, 0.24167f, 0.75833f, 0f, 0f,
        0.00000f, 0.00000f, 0.00000f, 1f, 0f
    )

    // 2. Deuteranopía
    val DEUTERANOPIA = floatArrayOf(
        0.62500f, 0.37500f, 0.00000f, 0f, 0f,
        0.70000f, 0.30000f, 0.00000f, 0f, 0f,
        0.00000f, 0.30000f, 0.70000f, 0f, 0f,
        0.00000f, 0.00000f, 0.00000f, 1f, 0f
    )

    // 3. Tritanopía
    val TRITANOPIA = floatArrayOf(
        0.95000f, 0.05000f, 0.00000f, 0f, 0f,
        0.00000f, 0.43333f, 0.56667f, 0f, 0f,
        0.00000f, 0.47500f, 0.52500f, 0f, 0f,
        0.00000f, 0.00000f, 0.00000f, 1f, 0f
    )

    // 4. Protanomalía
    val PROTANOMALY = floatArrayOf(
        0.81667f, 0.18333f, 0.00000f, 0f, 0f,
        0.33333f, 0.66667f, 0.00000f, 0f, 0f,
        0.00000f, 0.12500f, 0.87500f, 0f, 0f,
        0.00000f, 0.00000f, 0.00000f, 1f, 0f
    )

    // 5. Deuteranomalía
    val DEUTERANOMALY = floatArrayOf(
        0.80000f, 0.20000f, 0.00000f, 0f, 0f,
        0.25833f, 0.74167f, 0.00000f, 0f, 0f,
        0.00000f, 0.14167f, 0.85833f, 0f, 0f,
        0.00000f, 0.00000f, 0.00000f, 1f, 0f
    )

    // 6. Acromatopsia / Monocromático (Luminancia Rec. 601 / Rec. 709)
    val ACHROMATOPSIA = floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.000f, 0.000f, 0.000f, 1f, 0f
    )

    // 7. Daltonismo Rojo-Verde (Redistribución espectral para alta diferenciación)
    val RED_GREEN = floatArrayOf(
        0.00f, 0.00f, 0.00f, 0f, 0f,
        0.70f, 0.70f, 0.00f, 0f, 0f,
        0.00f, 0.24f, 0.95f, 0f, 0f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    // 8. Daltonismo Azul-Amarillo (Redistribución espectral rosa/verde)
    val BLUE_YELLOW = floatArrayOf(
        0.95f, 0.05f, 0.00f, 0f, 0f,
        0.00f, 0.43f, 0.56f, 0f, 0f,
        0.00f, 0.47f, 0.52f, 0f, 0f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    // 9. Alto Contraste (Boost de contraste 1.35x y diferenciación de luminancia)
    val HIGH_CONTRAST = floatArrayOf(
        1.35f, 0.00f, 0.00f, 0f, -0.1f,
        0.00f, 1.35f, 0.00f, 0f, -0.1f,
        0.00f, 0.00f, 1.35f, 0f, -0.1f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    fun forMode(mode: PaletteMode): FloatArray = when (mode) {
        PaletteMode.STANDARD -> IDENTITY
        PaletteMode.PROTANOPIA -> PROTANOPIA
        PaletteMode.DEUTERANOPIA -> DEUTERANOPIA
        PaletteMode.TRITANOPIA -> TRITANOPIA
        PaletteMode.PROTANOMALY -> PROTANOMALY
        PaletteMode.DEUTERANOMALY -> DEUTERANOMALY
        PaletteMode.ACHROMATOPSIA, PaletteMode.MONOCHROME -> ACHROMATOPSIA
        PaletteMode.RED_GREEN -> RED_GREEN
        PaletteMode.BLUE_YELLOW -> BLUE_YELLOW
        PaletteMode.HIGH_CONTRAST -> HIGH_CONTRAST
    }
}

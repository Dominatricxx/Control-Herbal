package com.example.controlherbal.frontend.style

import androidx.compose.ui.graphics.Color

/**
 * Tokens de diseño fijos para el rediseño del Hero y componentes de Control Herbal.
 */
object HerbalDesignTokens {

    object Dark {
        val background = Color(0xFF3A3A3C)
        val surface = Color(0xFF2B2B2D)
        val textPrimary = Color(0xFFF2F2F7)
        val textSecondary = Color(0xFFC4C4CC)
        val rail = Color.White.copy(alpha = 0.22f)
        val border = Color.White.copy(alpha = 0.22f)
        val icons = Color(0xFFF2F2F7)
    }

    object Light {
        val background = Color(0xFFFFFCF7)
        val surface = Color(0xFFD9D5C3)
        val textPrimary = Color(0xFF26261F)
        val textSecondary = Color(0xFF4A493F)
        val rail = Color(0x3C3C43).copy(alpha = 0.16f)
        val border = Color(0x3C3C43).copy(alpha = 0.14f)
        val icons = Color(0xFF3A3A3C)
        val ringWidgetContainer = Color.White.copy(alpha = 0.72f)
    }

    object Status {
        val optimal = Color(0xFF2E7D32)
        val warning = Color(0xFFB34700)
        val attention = Color(0xFF8A5A00)
        val critical = Color(0xFFC62828)
        val navigationGreen = Color(0xFF4F7348)
    }

    object IrhCurve {
        val colors = listOf(
            0.0f to Color(0xFF54D465),
            0.10f to Color(0xFF5ADA48),
            0.25f to Color(0xFF8FE335),
            0.34f to Color(0xFFBAEA29),
            0.50f to Color(0xFFF5D014),
            0.58f to Color(0xFFF6B209),
            0.75f to Color(0xFFE66A05),
            0.81f to Color(0xFFD64D03),
            1.0f to Color(0xFFA30500)
        )
    }
}

package com.example.controlherbal.frontend.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import com.example.controlherbal.common.accessibility.ColorVisionMatrix
import com.example.controlherbal.common.accessibility.StatusPalette

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Global App Theme: Applies MaterialTheme AND active global ColorVisionMatrix filter
 * across ALL screens, composables, cards, texts, and buttons in the entire application!
 */
@Composable
fun ControlHerbalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val initialMode = remember(context) { StatusPalette.mode(context) }
    val paletteState by StatusPalette.paletteState.collectAsState(initial = initialMode)
    val activeMode = paletteState ?: initialMode

    val colorMatrixArray = remember(activeMode) { ColorVisionMatrix.forMode(activeMode) }
    val isIdentity = remember(colorMatrixArray) { colorMatrixArray.contentEquals(ColorVisionMatrix.IDENTITY) }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        if (!isIdentity) {
            val filterPaint = remember(colorMatrixArray) {
                Paint().apply {
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(colorMatrixArray))
                }
            }
            Box(
                modifier = Modifier.drawWithContent {
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(size.toRect(), filterPaint)
                        drawContent()
                        canvas.restore()
                    }
                }
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

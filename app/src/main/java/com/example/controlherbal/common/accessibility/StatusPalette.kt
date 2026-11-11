package com.example.controlherbal.common.accessibility

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.frontend.activities.main.*
import com.example.controlherbal.frontend.activities.auth.*
import com.example.controlherbal.frontend.activities.privacy.*
import com.example.controlherbal.frontend.activities.plant.*
import com.example.controlherbal.frontend.style.*

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Paletas de color de ESTADO (óptimo / atención / riesgo / información) para distintas condiciones
 * visuales. Los valores se verifican en CI con scripts/audit_palettes.py (contraste WCAG y
 * distinguibilidad simulando protanopia, deuteranopia, tritanopia, protanomalía, deuteranomalía y acromatopsia).
 *
 * Importante: el color NUNCA es la única señal. Cada estado lleva además símbolo y texto
 * (ver [StatusLevel]).
 */
enum class PaletteMode(val id: String, val label: String) {
    STANDARD("standard", "Estándar"),
    PROTANOPIA("protanopia", "Protanopía (Ceguera total al rojo)"),
    DEUTERANOPIA("deuteranopia", "Deuteranopía (Ceguera total al verde)"),
    TRITANOPIA("tritanopia", "Tritanopía (Ceguera total al azul)"),
    PROTANOMALY("protanomaly", "Protanomalía (Deficiencia parcial al rojo)"),
    DEUTERANOMALY("deuteranomaly", "Deuteranomalía (Deficiencia parcial al verde)"),
    ACHROMATOPSIA("achromatopsia", "Acromatopsia (Ceguera total del color / Monocromático)"),
    RED_GREEN("red_green", "Daltonismo rojo-verde (protanopia / deuteranopia)"),
    BLUE_YELLOW("blue_yellow", "Daltonismo azul-amarillo (tritanopia)"),
    HIGH_CONTRAST("high_contrast", "Alto contraste"),
    MONOCHROME("monochrome", "Acromatopsia / Monocromático (Escala de grises pura)");

    companion object {
        fun fromId(id: String?): PaletteMode = values().firstOrNull { it.id == id } ?: STANDARD
    }
}

data class StatusColors(val ok: Int, val warn: Int, val critical: Int, val info: Int) {
    fun forLevel(level: StatusLevel): Int = when (level) {
        StatusLevel.OK -> ok
        StatusLevel.WARN -> warn
        StatusLevel.CRITICAL -> critical
        StatusLevel.INFO -> info
    }
}

/** Estado con señal redundante (símbolo + texto) para no depender solo del color. */
enum class StatusLevel(val symbol: String, val label: String) {
    OK("✔", "Óptimo"),
    WARN("▲", "Atención"),
    CRITICAL("✖", "Riesgo"),
    INFO("ℹ", "Información");

    fun describe(detail: String? = null): String =
        if (detail.isNullOrBlank()) "$symbol $label" else "$symbol $label: $detail"
}

object StatusPalette {

    private val _paletteState = MutableStateFlow<PaletteMode?>(null)
    val paletteState: StateFlow<PaletteMode?> = _paletteState.asStateFlow()

    private val PALETTES = mapOf(
        PaletteMode.STANDARD to StatusColors(ok = 0xFF2E7D32.toInt(), warn = 0xFFB34700.toInt(), critical = 0xFFC62828.toInt(), info = 0xFF1565C0.toInt()),
        PaletteMode.PROTANOPIA to StatusColors(ok = 0xFF0072B2.toInt(), warn = 0xFFE69F00.toInt(), critical = 0xFF000000.toInt(), info = 0xFF56B4E9.toInt()),
        PaletteMode.DEUTERANOPIA to StatusColors(ok = 0xFF0072B2.toInt(), warn = 0xFFD55E00.toInt(), critical = 0xFFCC79A7.toInt(), info = 0xFF56B4E9.toInt()),
        PaletteMode.TRITANOPIA to StatusColors(ok = 0xFF009E73.toInt(), warn = 0xFFE69F00.toInt(), critical = 0xFFD55E00.toInt(), info = 0xFF0072B2.toInt()),
        PaletteMode.PROTANOMALY to StatusColors(ok = 0xFF1B7837.toInt(), warn = 0xFFD95F02.toInt(), critical = 0xFF7570B3.toInt(), info = 0xFF1F78B4.toInt()),
        PaletteMode.DEUTERANOMALY to StatusColors(ok = 0xFF2B83BA.toInt(), warn = 0xFFD95F02.toInt(), critical = 0xFF7570B3.toInt(), info = 0xFF2B83BA.toInt()),
        PaletteMode.ACHROMATOPSIA to StatusColors(ok = 0xFFFFFFFF.toInt(), warn = 0xFF888888.toInt(), critical = 0xFF222222.toInt(), info = 0xFFE0E0E0.toInt()),
        PaletteMode.RED_GREEN to StatusColors(ok = 0xFF0072B2.toInt(), warn = 0xFFB34700.toInt(), critical = 0xFF9E0059.toInt(), info = 0xFF0072B2.toInt()),
        PaletteMode.BLUE_YELLOW to StatusColors(ok = 0xFF2E7D32.toInt(), warn = 0xFF7A4B00.toInt(), critical = 0xFFD81B60.toInt(), info = 0xFF1565C0.toInt()),
        PaletteMode.HIGH_CONTRAST to StatusColors(ok = 0xFF0D47A1.toInt(), warn = 0xFF804000.toInt(), critical = 0xFF9E0059.toInt(), info = 0xFF0D47A1.toInt()),
        PaletteMode.MONOCHROME to StatusColors(ok = 0xFFFFFFFF.toInt(), warn = 0xFF888888.toInt(), critical = 0xFF222222.toInt(), info = 0xFFE0E0E0.toInt())
    )

    fun colors(mode: PaletteMode): StatusColors = PALETTES.getValue(mode)

    fun textColorFor(level: StatusLevel, mode: PaletteMode): Int {
        if (mode == PaletteMode.ACHROMATOPSIA || mode == PaletteMode.MONOCHROME) {
            return when (level) {
                StatusLevel.OK, StatusLevel.INFO -> 0xFF000000.toInt()
                StatusLevel.WARN, StatusLevel.CRITICAL -> 0xFFFFFFFF.toInt()
            }
        }
        return 0xFFFFFFFF.toInt()
    }

    fun mode(context: Context): PaletteMode {
        val currentFlowValue = _paletteState.value
        if (currentFlowValue != null) return currentFlowValue
        val saved = PaletteMode.fromId(
            context.getSharedPreferences(AppConstants.PREFS_ACCESSIBILITY, Context.MODE_PRIVATE)
                .getString(AppConstants.KEY_PALETTE, null)
        )
        _paletteState.value = saved
        return saved
    }

    fun setMode(context: Context, mode: PaletteMode) {
        context.getSharedPreferences(AppConstants.PREFS_ACCESSIBILITY, Context.MODE_PRIVATE)
            .edit().putString(AppConstants.KEY_PALETTE, mode.id).apply()
        _paletteState.value = mode
        (context as? Activity)?.let { activity ->
            applyToWindow(activity.window, context)
        }
    }

    fun applyToWindow(window: Window, context: Context) {
        val activeMode = mode(context)
        val matrixArray = ColorVisionMatrix.forMode(activeMode)
        if (matrixArray.contentEquals(ColorVisionMatrix.IDENTITY)) {
            window.decorView.setLayerType(View.LAYER_TYPE_NONE, null)
        } else {
            val paint = android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(matrixArray)
            }
            window.decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }

    fun current(context: Context): StatusColors = colors(mode(context))
}

object ChartColors {
    const val TEMP = 0xFFCC6600.toInt()   // naranja
    const val HUM = 0xFF0099CC.toInt()    // celeste
    const val SOIL = 0xFF0000FF.toInt()   // azul
    const val LIGHT = 0xFF666666.toInt()  // gris
    const val IRH = 0xFF990000.toInt()    // rojo oscuro
    const val SEQ = 0xFF330000.toInt()    // casi negro rojizo
    const val SOMB = 0xFF000033.toInt()   // casi negro azulado
}

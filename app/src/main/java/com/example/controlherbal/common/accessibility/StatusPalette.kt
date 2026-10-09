package com.example.controlherbal.common.accessibility

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Context

/**
 * Paletas de color de ESTADO (óptimo / atención / riesgo / información) para distintas condiciones
 * visuales. Los valores se verifican en CI con scripts/audit_palettes.py (contraste WCAG y
 * distinguibilidad simulando protanopia, deuteranopia y tritanopia). Si cambias un color, ejecuta:
 *     python scripts/audit_palettes.py
 *
 * Importante: el color NUNCA es la única señal. Cada estado lleva además símbolo y texto
 * (ver [StatusLevel]).
 */
enum class PaletteMode(val id: String, val label: String) {
    STANDARD("standard", "Estándar"),
    RED_GREEN("red_green", "Daltonismo rojo-verde (protanopia / deuteranopia)"),
    BLUE_YELLOW("blue_yellow", "Daltonismo azul-amarillo (tritanopia)"),
    HIGH_CONTRAST("high_contrast", "Alto contraste");

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

    private val PALETTES = mapOf(
        PaletteMode.STANDARD to StatusColors(ok = 0xFF2E7D32.toInt(), warn = 0xFFB34700.toInt(), critical = 0xFFC62828.toInt(), info = 0xFF1565C0.toInt()),
        PaletteMode.RED_GREEN to StatusColors(ok = 0xFF0072B2.toInt(), warn = 0xFFB34700.toInt(), critical = 0xFF9E0059.toInt(), info = 0xFF0072B2.toInt()),
        PaletteMode.BLUE_YELLOW to StatusColors(ok = 0xFF2E7D32.toInt(), warn = 0xFF7A4B00.toInt(), critical = 0xFFD81B60.toInt(), info = 0xFF1565C0.toInt()),
        PaletteMode.HIGH_CONTRAST to StatusColors(ok = 0xFF0D47A1.toInt(), warn = 0xFF804000.toInt(), critical = 0xFF9E0059.toInt(), info = 0xFF0D47A1.toInt())
    )

    fun colors(mode: PaletteMode): StatusColors = PALETTES.getValue(mode)

    fun mode(context: Context): PaletteMode =
        PaletteMode.fromId(
            context.getSharedPreferences(AppConstants.PREFS_ACCESSIBILITY, Context.MODE_PRIVATE)
                .getString(AppConstants.KEY_PALETTE, null)
        )

    fun setMode(context: Context, mode: PaletteMode) {
        context.getSharedPreferences(AppConstants.PREFS_ACCESSIBILITY, Context.MODE_PRIVATE)
            .edit().putString(AppConstants.KEY_PALETTE, mode.id).apply()
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

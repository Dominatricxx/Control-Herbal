package com.example.controlherbal.ui.presenter

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.example.controlherbal.common.accessibility.StatusLevel
import com.example.controlherbal.common.accessibility.StatusPalette
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorReading
import java.util.Locale

/**
 * SensorDashboardPresenter: Encapsula la actualización visual de las tarjetas del Dashboard y estados.
 */
class SensorDashboardPresenter(
    private val context: Context,
    private val tvStatusTitle: TextView,
    private val tvStatusSubtitle: TextView,
    private val viewStatusIndicator: View,
    private val tvTemp: TextView,
    private val tvHum: TextView,
    private val tvLuz: TextView,
    private val tvSoil: TextView,
    private val tvIrh: TextView,
    private val tvSeq: TextView,
    private val tvSomb: TextView
) {

    fun updateMetrics(
        temp: Double, hum: Double, luz: Double, soil: Double,
        irh: Double, seq: Double, somb: Double
    ) {
        val level = when {
            irh >= 75.0 -> StatusLevel.CRITICAL
            irh >= 50.0 -> StatusLevel.WARN
            else -> StatusLevel.OK
        }

        val colors = StatusPalette.current(context)
        val colorInt = colors.forLevel(level)

        tvStatusTitle.text = "${level.symbol} ${level.label}"
        tvStatusTitle.setTextColor(colorInt)
        viewStatusIndicator.setBackgroundColor(colorInt)

        tvTemp.text = String.format(Locale.getDefault(), "%.1f °C", temp)
        tvHum.text = String.format(Locale.getDefault(), "%.1f %%", hum)
        tvLuz.text = String.format(Locale.getDefault(), "%.1f %%", luz)
        tvSoil.text = String.format(Locale.getDefault(), "%.1f %%", soil)
        tvIrh.text = String.format(Locale.getDefault(), "%.1f", irh)
        tvSeq.text = formatHours(seq)
        tvSomb.text = formatHours(somb)
    }

    fun showLinkingState() {
        tvStatusTitle.text = "Vinculando..."
        tvStatusSubtitle.text = "Conectando con el sensor ESP32"
        tvStatusTitle.setTextColor(Color.parseColor("#1E88E5"))
        viewStatusIndicator.setBackgroundColor(Color.parseColor("#1E88E5"))
    }

    fun showDisconnectedState() {
        tvStatusTitle.text = "Desconectado"
        tvStatusSubtitle.text = "Buscando señal de sensores"
        tvStatusTitle.setTextColor(Color.parseColor("#757575"))
        viewStatusIndicator.setBackgroundColor(Color.parseColor("#757575"))
    }

    private fun formatHours(hours: Double): String {
        val totalMin = (hours * 60).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

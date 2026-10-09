package com.example.controlherbal.ui.style

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*

import com.github.mikephil.charting.data.LineDataSet

/**
 * Estilo de líneas de gráfico: cada serie se distingue por color Y por trazo, de modo que el gráfico
 * siga siendo legible con daltonismo o en escala de grises.
 */
object ChartStyle {
    /** 0 = continua, 1 = guiones largos, 2 = puntos, 3 = continua con marcadores. */
    fun apply(set: LineDataSet, color: Int, seriesIndex: Int, width: Float = 2f): LineDataSet {
        set.color = color
        set.lineWidth = width
        set.setDrawCircles(false)
        set.setDrawValues(false)
        when (seriesIndex % 4) {
            1 -> set.enableDashedLine(18f, 8f, 0f)
            2 -> set.enableDashedLine(4f, 8f, 0f)
            3 -> { set.setDrawCircles(true); set.setCircleColor(color); set.circleRadius = 2f; set.setDrawCircleHole(false) }
        }
        return set
    }

    /** Segunda planta en comparaciones: mismo color de métrica pero trazo punteado fino. */
    fun applySecondary(set: LineDataSet, color: Int): LineDataSet {
        set.color = color
        set.lineWidth = 2f
        set.setDrawCircles(false)
        set.setDrawValues(false)
        set.enableDashedLine(10f, 5f, 0f)
        return set
    }
}

package com.example.controlherbal.ui.helper

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

import com.example.controlherbal.common.accessibility.ChartColors
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.ui.style.ChartStyle
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChartManager: Encapsula la preparación, estilización accesible y dibujo de gráficos combinados.
 */
object ChartManager {

    fun setupCombinedChart(
        chart: CombinedChart,
        readings: List<SensorReading>,
        showTemp: Boolean,
        showHum: Boolean,
        showSoil: Boolean,
        showLight: Boolean,
        showIrh: Boolean
    ) {
        if (readings.isEmpty()) {
            chart.clear()
            chart.setNoDataText("Esperando datos...")
            chart.invalidate()
            return
        }

        val combinedData = CombinedData()
        val lineData = LineData()

        if (showTemp) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }
            lineData.addDataSet(ChartStyle.apply(LineDataSet(entries, "Temp"), ChartColors.TEMP, 0))
        }
        if (showHum) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }
            lineData.addDataSet(ChartStyle.apply(LineDataSet(entries, "Hum"), ChartColors.HUM, 1))
        }
        if (showSoil) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }
            lineData.addDataSet(ChartStyle.apply(LineDataSet(entries, "Suelo"), ChartColors.SOIL, 2, 2.5f))
        }
        if (showLight) {
            val entries = readings.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }
            lineData.addDataSet(ChartStyle.apply(LineDataSet(entries, "Luz"), ChartColors.LIGHT, 3))
        }

        combinedData.setData(lineData)

        if (showIrh) {
            val barEntries = readings.mapIndexed { i, r -> BarEntry(i.toFloat(), r.irh.toFloat()) }
            val barDataSet = BarDataSet(barEntries, "IRH").apply {
                color = ChartColors.IRH
                setDrawValues(false)
            }
            val barData = BarData(barDataSet).apply { barWidth = 0.5f }
            combinedData.setData(barData)
        }

        chart.apply {
            data = combinedData
            description.isEnabled = false
            setExtraOffsets(5f, 5f, 5f, 15f)

            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 105f
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -30f
                axisMinimum = -0.5f
                axisMaximum = readings.size.toFloat() - 0.5f

                valueFormatter = object : ValueFormatter() {
                    private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getAxisLabel(v: Float, a: AxisBase?): String {
                        val idx = v.toInt()
                        return if (idx in readings.indices) sdf.format(Date(readings[idx].timestamp)) else ""
                    }
                }
            }

            setTouchEnabled(true)
            isDragEnabled = true
            isScaleXEnabled = true
            isScaleYEnabled = false

            animateX(400)
            invalidate()
        }
    }
}

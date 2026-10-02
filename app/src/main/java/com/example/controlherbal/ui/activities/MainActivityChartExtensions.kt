package com.example.controlherbal.ui.activities

import android.graphics.Color
import com.example.controlherbal.data.database.SensorReading
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

fun MainActivity.loadDataAndDrawChart() {
    currentPlant?.let { plant ->
        ioScope.launch {
            val readings = databaseLocal.sensorDao().getLast2000Asc(plant.id)
            withContext(Dispatchers.Main) {
                if (readings.isNotEmpty()) {
                    drawChart(readings)
                } else {
                    combinedChart.clear()
                    combinedChart.setNoDataText("Esperando datos...")
                    combinedChart.invalidate()
                }
            }
        }
    }
}

fun MainActivity.drawChart(readings: List<SensorReading>) {
    val combinedData = CombinedData()
    val lineData = LineData()
    if (cbTemp.isChecked) lineData.addDataSet(LineDataSet(readings.mapIndexed { i, r -> Entry(i.toFloat(), r.temperature.toFloat()) }, "Temp").apply { color = Color.RED; setDrawCircles(false); lineWidth = 2f })
    if (cbHum.isChecked) lineData.addDataSet(LineDataSet(readings.mapIndexed { i, r -> Entry(i.toFloat(), r.humidity.toFloat()) }, "Hum").apply { color = Color.BLUE; setDrawCircles(false); lineWidth = 2f })
    if (cbSoil.isChecked) lineData.addDataSet(LineDataSet(readings.mapIndexed { i, r -> Entry(i.toFloat(), r.soilMoisture.toFloat()) }, "Suelo").apply { color = Color.parseColor("#2E7D32"); setDrawCircles(false); lineWidth = 2.5f })
    if (cbLuz.isChecked) lineData.addDataSet(LineDataSet(readings.mapIndexed { i, r -> Entry(i.toFloat(), r.light.toFloat()) }, "Luz").apply { color = Color.rgb(255, 215, 0); setDrawCircles(false); lineWidth = 2f })
    combinedData.setData(lineData)
    if (cbIRH.isChecked) {
        val barData = BarData(BarDataSet(readings.mapIndexed { i, r -> BarEntry(i.toFloat(), r.irh.toFloat()) }, "IRH").apply { color = Color.argb(150, 211, 47, 47); setDrawValues(false) })
        barData.barWidth = 0.5f
        combinedData.setData(barData)
    }
    
    combinedChart.apply {
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

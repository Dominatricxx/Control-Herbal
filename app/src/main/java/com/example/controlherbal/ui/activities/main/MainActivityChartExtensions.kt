package com.example.controlherbal.ui.activities.main

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.ui.helper.ChartManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun MainActivity.loadDataAndDrawChart() {
    currentPlant?.let { plant ->
        ioScope.launch {
            val readings = databaseLocal.sensorDao().getLast2000Asc(plant.id)
            withContext(Dispatchers.Main) {
                drawChart(readings)
            }
        }
    }
}

fun MainActivity.drawChart(readings: List<SensorReading>) {
    ChartManager.setupCombinedChart(
        chart = combinedChart,
        readings = readings,
        showTemp = cbTemp.isChecked,
        showHum = cbHum.isChecked,
        showSoil = cbSoil.isChecked,
        showLight = cbLuz.isChecked,
        showIrh = cbIRH.isChecked
    )
}

package com.example.controlherbal

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.controlherbal.database.SensorDatabase
import com.example.controlherbal.database.SensorReading
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.appcompat.widget.Toolbar

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "Control Herbal"
    }

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvConnectionState: TextView
    // ... (resto de componentes ya declarados)
    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvLuz: TextView
    private lateinit var tvIRH: TextView
    private lateinit var tvSeq: TextView
    private lateinit var tvSomb: TextView
    private lateinit var tvAccion: TextView
    private lateinit var tvAlerta: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var lineChart: LineChart

    // Firebase
    private val databaseFirebase = FirebaseDatabase.getInstance().getReference("sensor")

    // Base de datos local y corrutinas
    private lateinit var databaseLocal: SensorDatabase
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_drawer)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        // Botón de menú personalizado (las 3 líneas)
        val btnMenu: android.widget.ImageButton = findViewById(R.id.btnMenu)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Ocultar la opción de "Panel Principal" cuando ya estamos en él
        navView.menu.findItem(R.id.nav_main).isVisible = false

        // Referencias UI (ahora dentro del include)
        tvConnectionState = findViewById(R.id.tvConnectionState)
        tvTemp = findViewById(R.id.tvTemp)
        tvHum = findViewById(R.id.tvHum)
        tvLuz = findViewById(R.id.tvLuz)
        tvIRH = findViewById(R.id.tvIRH)
        tvSeq = findViewById(R.id.tvSeq)
        tvSomb = findViewById(R.id.tvSomb)
        tvAccion = findViewById(R.id.tvAccion)
        tvAlerta = findViewById(R.id.tvAlerta)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        lineChart = findViewById(R.id.lineChart)

        // Configurar botón de conexión
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        btnConnect.visibility = View.VISIBLE
        btnConnect.setOnClickListener {
            startFirebaseListener()
        }

        // Inicializar Base de Datos Local
        databaseLocal = SensorDatabase.getInstance(this)

        startFirebaseListener()
        loadDataAndDrawChart()
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        val intent = android.content.Intent(this, HistoryActivity::class.java)
        when (item.itemId) {
            R.id.nav_daily -> {
                intent.putExtra("HISTORY_TYPE", "DIARIO")
                startActivity(intent)
            }
            R.id.nav_weekly -> {
                intent.putExtra("HISTORY_TYPE", "SEMANAL")
                startActivity(intent)
            }
            R.id.nav_monthly -> {
                intent.putExtra("HISTORY_TYPE", "MENSUAL")
                startActivity(intent)
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun startFirebaseListener() {
        tvConnectionState.text = "Sincronizando con Nube..."
        tvConnectionState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

        databaseFirebase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    tvConnectionState.text = "Conectado a Firebase"
                    tvConnectionState.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                    
                    val temp = snapshot.child("temp").getValue(Double::class.java) ?: 0.0
                    val hum = snapshot.child("hum").getValue(Double::class.java) ?: 0.0
                    val luz = snapshot.child("luz").getValue(Int::class.java) ?: 0
                    val irh = snapshot.child("irh").getValue(Double::class.java) ?: 0.0
                    val seq = snapshot.child("seq").getValue(Double::class.java) ?: 0.0
                    val somb = snapshot.child("somb").getValue(Double::class.java) ?: 0.0
                    val accion = snapshot.child("acc").getValue(String::class.java) ?: "Sin datos"

                    updateUIAndSave(temp, hum, luz, irh, seq, somb, accion)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error en Firebase: ${error.message}")
                tvConnectionState.text = "Error de conexión"
                tvConnectionState.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
            }
        })
    }

    private fun updateUIAndSave(temp: Double, hum: Double, luz: Int, irh: Double, seq: Double, somb: Double, accion: String) {
        runOnUiThread {
            // Actualizar UI
            tvTemp.text = String.format("%.1f °C", temp)
            tvHum.text = String.format("%.1f %%", hum)
            tvLuz.text = "$luz %"
            tvIRH.text = String.format("%.1f", irh)
            tvSeq.text = if (seq > 0) String.format("%.1f h", seq) else "Sin riesgo"
            tvSomb.text = if (somb > 0) String.format("%.1f h", somb) else "Sin necesidad"
            tvAccion.text = accion
            tvLastUpdate.text = "Última actualización: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"

            // Alerta visual
            when {
                irh > 75 -> {
                    tvAlerta.text = "⚠️ ¡RIESGO CRÍTICO!"
                    tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    tvAlerta.setTextColor(Color.WHITE)
                }
                irh > 25 -> {
                    tvAlerta.text = "⚠️ ADVERTENCIA"
                    tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    tvAlerta.setTextColor(Color.BLACK)
                }
                else -> {
                    tvAlerta.text = "✅ Condiciones óptimas"
                    tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    tvAlerta.setTextColor(Color.BLACK)
                }
            }
        }

        // Guardar en Room para el gráfico
        val reading = SensorReading(
            timestamp = System.currentTimeMillis(),
            temperature = temp,
            humidity = hum,
            light = luz,
            irh = irh,
            seq = seq,
            somb = somb,
            action = accion
        )
        ioScope.launch {
            databaseLocal.sensorDao().insert(reading)
            // Opcional: Limpiar datos viejos
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 3600_000L
            databaseLocal.sensorDao().deleteOldReadings(sevenDaysAgo)
            
            withContext(Dispatchers.Main) {
                loadDataAndDrawChart()
            }
        }
    }

    private fun loadDataAndDrawChart() {
        ioScope.launch {
            val readings = databaseLocal.sensorDao().getLast2000Asc()
            withContext(Dispatchers.Main) {
                if (readings.isNotEmpty()) {
                    drawChart(readings)
                } else {
                    lineChart.setNoDataText("Esperando datos de la nube...")
                    lineChart.invalidate()
                }
            }
        }
    }

    private fun drawChart(readings: List<SensorReading>) {
        val tempEntries = mutableListOf<Entry>()
        val humEntries = mutableListOf<Entry>()
        val lightEntries = mutableListOf<Entry>()

        for (i in readings.indices) {
            val r = readings[i]
            tempEntries.add(Entry(i.toFloat(), r.temperature.toFloat()))
            humEntries.add(Entry(i.toFloat(), r.humidity.toFloat()))
            lightEntries.add(Entry(i.toFloat(), r.light.toFloat()))
        }

        val tempSet = LineDataSet(tempEntries, "Temp (°C)").apply {
            color = Color.rgb(255, 80, 80)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
        }
        val humSet = LineDataSet(humEntries, "Hum (%)").apply {
            color = Color.rgb(80, 255, 80)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
        }
        val lightSet = LineDataSet(lightEntries, "Luz (%)").apply {
            color = Color.rgb(255, 200, 0)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
        }

        lineChart.apply {
            data = LineData(tempSet, humSet, lightSet)
            description.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = object : ValueFormatter() {
                private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val index = value.toInt()
                    return if (index in readings.indices) sdf.format(Date(readings[index].timestamp)) else ""
                }
            }
            invalidate()
        }
    }
}

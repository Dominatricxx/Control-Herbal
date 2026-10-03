package com.example.controlherbal.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import android.widget.Button
import android.view.View

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvLuz: TextView
    private lateinit var tvIRH: TextView
    private lateinit var tvSeq: TextView
    private lateinit var tvSomb: TextView
    private lateinit var tvAlert: TextView
    private lateinit var tvAction: TextView

    private val databaseFirebase = FirebaseDatabase.getInstance().getReference("sensor")
    private var sensorListener: ValueEventListener? = null
    private val CHANNEL_ID = "control_herbal_alerts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("HERBAL_WEAR", "MainActivity Created - Version 3.0 (Light Theme)")
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvWatchStatus)
        btnConnect = findViewById(R.id.btnWatchConnect)
        tvTemp = findViewById(R.id.tvWatchTemp)
        tvHum = findViewById(R.id.tvWatchHum)
        tvLuz = findViewById(R.id.tvWatchLuz)
        tvIRH = findViewById(R.id.tvWatchIRH)
        tvSeq = findViewById(R.id.tvWatchSeq)
        tvSomb = findViewById(R.id.tvWatchSomb)
        tvAlert = findViewById(R.id.tvWatchAlert)
        tvAction = findViewById(R.id.tvWatchAction)

        btnConnect.setOnClickListener {
            btnConnect.visibility = View.GONE
            signInThen { startFirebaseListener() }
        }

        createNotificationChannel()
        signInThen { startFirebaseListener() }
    }

    /**
     * El reloj entra con una cuenta de SOLO LECTURA ("viewer", ver firebase/database.rules.json).
     * Las credenciales se inyectan en tiempo de compilación (gradle.properties de usuario o
     * variables de entorno), nunca se versionan. Todo lo embebido en un APK es extraíble, por eso
     * esa cuenta únicamente puede leer /sensor y es revocable desde la consola de Firebase.
     */
    private fun signInThen(onReady: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            onReady()
            return
        }
        if (BuildConfig.VIEWER_EMAIL.isBlank() || BuildConfig.VIEWER_PASSWORD.isBlank()) {
            tvStatus.text = "Sin credenciales configuradas"
            tvStatus.setTextColor(Color.RED)
            return
        }
        tvStatus.text = "Iniciando sesión..."
        tvStatus.setTextColor(Color.GRAY)
        auth.signInWithEmailAndPassword(BuildConfig.VIEWER_EMAIL, BuildConfig.VIEWER_PASSWORD)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onReady()
                } else {
                    tvStatus.text = "Error de acceso"
                    tvStatus.setTextColor(Color.RED)
                    btnConnect.visibility = View.VISIBLE
                }
            }
    }

    private fun startFirebaseListener() {
        tvStatus.text = "Sincronizando..."
        tvStatus.setTextColor(Color.GRAY)
        
        sensorListener?.let { databaseFirebase.removeEventListener(it) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    tvStatus.text = "Firebase Conectado"
                    tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                    btnConnect.visibility = View.GONE
                    
                    val temp = snapshot.child("temp").getValue(Double::class.java) ?: 0.0
                    val hum = snapshot.child("hum").getValue(Double::class.java) ?: 0.0
                    val luz = snapshot.child("luz").getValue(Int::class.java) ?: 0
                    val irh = snapshot.child("irh").getValue(Double::class.java) ?: 0.0
                    val seq = snapshot.child("seq").getValue(Double::class.java) ?: 0.0
                    val somb = snapshot.child("somb").getValue(Double::class.java) ?: 0.0
                    // Texto remoto: se acota y se eliminan caracteres de control antes de mostrarlo.
                    val accion = (snapshot.child("acc").getValue(String::class.java) ?: "Sin datos")
                        .filter { !it.isISOControl() }
                        .take(200)

                    updateUI(temp, hum, luz, irh, seq, somb, accion)
                    checkAlerts(irh, accion)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                tvStatus.text = "Error de conexión"
                tvStatus.setTextColor(Color.RED)
                btnConnect.visibility = View.VISIBLE
            }
        }
        sensorListener = listener
        databaseFirebase.addValueEventListener(listener)
    }

    override fun onDestroy() {
        sensorListener?.let { databaseFirebase.removeEventListener(it) }
        sensorListener = null
        super.onDestroy()
    }

    private fun updateUI(temp: Double, hum: Double, luz: Int, irh: Double, seq: Double, somb: Double, accion: String) {
        tvTemp.text = String.format("%.1f°", temp)
        tvHum.text = String.format("%.0f%%", hum)
        tvLuz.text = String.format("%d%%", luz)
        tvIRH.text = String.format("%.1f", irh)
        tvSeq.text = if (seq > 0) String.format("%.1f h", seq) else "Sin riesgo"
        tvSomb.text = if (somb > 0) String.format("%.1f h", somb) else "Sin nec."
        tvAction.text = accion

        when {
            irh > 75 -> {
                tvAlert.text = "⚠️ RIESGO CRÍTICO"
                tvAlert.setBackgroundColor(Color.RED)
                tvAlert.setTextColor(Color.WHITE)
            }
            irh > 25 -> {
                tvAlert.text = "⚠️ ADVERTENCIA"
                tvAlert.setBackgroundColor(Color.rgb(255, 165, 0))
                tvAlert.setTextColor(Color.BLACK)
            }
            else -> {
                tvAlert.text = "✅ ÓPTIMO"
                tvAlert.setBackgroundColor(Color.rgb(165, 214, 167)) // Light Green
                tvAlert.setTextColor(Color.BLACK)
            }
        }
    }

    private fun checkAlerts(irh: Double, accion: String) {
        if (irh > 75) {
            sendNotification("¡RIESGO CRÍTICO!", accion)
        } else if (irh > 25) {
            sendNotification("Advertencia de Riesgo", accion)
        }
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(1000, 1000, 1000))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas Control Herbal"
            val descriptionText = "Notificaciones de riesgo para tus plantas"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 1000, 1000)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

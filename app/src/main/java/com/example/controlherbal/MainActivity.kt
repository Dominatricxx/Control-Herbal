package com.example.controlherbal

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ControlHerbal"
        // UUIDs del servicio y característica del ESP32
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private const val REQUEST_PERMISSION_CODE = 1
    }

    private lateinit var btnConnect: Button
    private lateinit var tvConnectionState: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvLuz: TextView
    private lateinit var tvIRH: TextView
    private lateinit var tvSeq: TextView
    private lateinit var tvSomb: TextView
    private lateinit var tvAccion: TextView
    private lateinit var tvAlerta: TextView
    private lateinit var tvLastUpdate: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = false
    private val targetDeviceName = "HerbalMonitor"

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referencias UI
        btnConnect = findViewById(R.id.btnConnect)
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

        // Inicializar Bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        btnConnect.setOnClickListener {
            if (isConnected) {
                disconnectDevice()
            } else {
                if (checkPermissions()) {
                    startBleScan()
                } else {
                    requestPermissions()
                }
            }
        }

        if (!checkPermissions()) {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Se necesitan permisos para BLE", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startBleScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Por favor, activa el Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        tvConnectionState.text = "Buscando dispositivo..."
        tvConnectionState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        bluetoothAdapter?.startLeScan(leScanCallback)
        handler.postDelayed({
            if (!isConnected) {
                stopBleScan()
                tvConnectionState.text = "No encontrado. Reintenta."
                tvConnectionState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        }, 10000)
    }

    private val leScanCallback = BluetoothAdapter.LeScanCallback { device, _, _ ->
        if (device.name == targetDeviceName) {
            stopBleScan()
            connectToDevice(device)
        }
    }

    private fun stopBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        bluetoothAdapter?.stopLeScan(leScanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        tvConnectionState.text = "Conectando a ${device.name}..."
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    runOnUiThread {
                        tvConnectionState.text = "Conectado"
                        tvConnectionState.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                        btnConnect.text = "DESCONECTAR"
                        Toast.makeText(this@MainActivity, "Conectado a HerbalMonitor", Toast.LENGTH_SHORT).show()
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    runOnUiThread {
                        tvConnectionState.text = "Desconectado"
                        tvConnectionState.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                        btnConnect.text = "CONECTAR"
                        clearData()
                    }
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null && ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val cccd = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (cccd != null) {
                        cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        gatt.writeDescriptor(cccd)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            val jsonString = String(data)
            Log.d(TAG, "Datos recibidos: $jsonString")
            runOnUiThread {
                parseAndUpdateUI(jsonString)
                tvLastUpdate.text = "Última actualización: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
            }
        }
    }

    private fun parseAndUpdateUI(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val temp = json.optDouble("temp", 0.0)
            val hum = json.optDouble("hum", 0.0)
            val luz = json.optInt("luz", 0)
            val irh = json.optDouble("irh", 0.0)
            val seq = json.optDouble("seq", 0.0)
            val somb = json.optDouble("somb", 0.0)
            val accion = json.optString("acc", "")

            tvTemp.text = String.format("%.1f °C", temp)
            tvHum.text = String.format("%.1f %%", hum)
            tvLuz.text = "$luz %"
            tvIRH.text = String.format("%.1f", irh)
            tvSeq.text = if (seq > 0) String.format("%.1f h", seq) else "Sin riesgo"
            tvSomb.text = if (somb > 0) String.format("%.1f h", somb) else "Sin necesidad"
            tvAccion.text = accion

            // Actualizar alerta visual según IRH
            when {
                irh > 75 -> {
                    tvAlerta.text = "⚠️ ¡RIESGO CRÍTICO! Actúa de inmediato."
                    tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    tvAlerta.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                }
                irh > 25 -> {
                    tvAlerta.text = "⚠️ ADVERTENCIA: Toma medidas preventivas."
                    tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    tvAlerta.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                }
                irh > 0 -> {
                    tvAlerta.text = "✅ Condiciones óptimas."
                    tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    tvAlerta.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                }
                else -> tvAlerta.text = "Esperando datos..."
            }

            // Cambiar color del texto del IRH
            when {
                irh > 75 -> tvIRH.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                irh > 25 -> tvIRH.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                else -> tvIRH.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parseando JSON: $jsonString", e)
            tvAlerta.text = "Error en datos recibidos"
        }
    }

    private fun clearData() {
        tvTemp.text = "-- °C"
        tvHum.text = "-- %"
        tvLuz.text = "-- %"
        tvIRH.text = "--"
        tvSeq.text = "-- h"
        tvSomb.text = "-- h"
        tvAccion.text = "--"
        tvAlerta.text = "Desconectado"
        tvAlerta.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        tvLastUpdate.text = "Última actualización: --"
    }

    private fun disconnectDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt?.disconnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectDevice()
    }
}
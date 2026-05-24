package com.example.controlherbal

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlantSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_setup)

        val etPlantName = findViewById<EditText>(R.id.etPlantName)
        val spinnerPlantType = findViewById<Spinner>(R.id.spinnerPlantType)
        val spinnerEnvironment = findViewById<Spinner>(R.id.spinnerEnvironment)
        val btnSavePlant = findViewById<Button>(R.id.btnSavePlant)
        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }

        val plantTypes = arrayOf("Girasol 🌻", "Tulipan 🌷", "Lavanda 🌿", "Suculenta 🌵", "Menta 🍃", "Rosas 🌹", "Otro 🌱")
        val environments = arrayOf("Luz 🌞", "Sombra 🌥️", "Híbrido ⛅")

        spinnerPlantType.adapter = ArrayAdapter(this, R.layout.spinner_item, plantTypes)
        spinnerEnvironment.adapter = ArrayAdapter(this, R.layout.spinner_item, environments)

        btnSavePlant.setOnClickListener {
            val name = etPlantName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Por favor, dale un nombre a tu planta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = spinnerPlantType.selectedItem.toString()
            val environment = spinnerEnvironment.selectedItem.toString()

            val db = SensorDatabase.getInstance(this)
            CoroutineScope(Dispatchers.IO).launch {
                db.plantDao().deselectAll()
                val plant = Plant(name = name, type = type, environment = environment, isSelected = true)
                db.plantDao().insert(plant)
                
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@PlantSetupActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}

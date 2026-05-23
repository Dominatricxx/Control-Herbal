package com.example.controlherbal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PlantSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_setup)

        val etPlantName = findViewById<EditText>(R.id.etPlantName)
        val spinnerPlantType = findViewById<Spinner>(R.id.spinnerPlantType)
        val spinnerEnvironment = findViewById<Spinner>(R.id.spinnerEnvironment)
        val btnSavePlant = findViewById<Button>(R.id.btnSavePlant)

        val plantTypes = arrayOf("Girasol 🌻", "Tulipanes 🌷", "Lavanda 🌿", "Suculenta 🌵", "Menta 🍃", "Rosas 🌹", "Otro 🌱")
        val environments = arrayOf("Luz", "Sombra", "Híbrido")

        spinnerPlantType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, plantTypes)
        spinnerEnvironment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, environments)

        btnSavePlant.setOnClickListener {
            val name = etPlantName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Por favor, dale un nombre a tu planta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = spinnerPlantType.selectedItem.toString()
            val environment = spinnerEnvironment.selectedItem.toString()

            val prefs = getSharedPreferences("PlantPrefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("plant_name", name)
                putString("plant_type", type)
                putString("plant_environment", environment)
                putBoolean("setup_complete", true)
                apply()
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

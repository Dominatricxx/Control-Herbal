package com.example.controlherbal.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.data.database.SensorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val btnContinue = findViewById<ImageButton>(R.id.btnContinue)
        btnContinue.setOnClickListener { navigateNext() }
    }

    private fun navigateNext() {
        val databaseLocal = SensorDatabase.getInstance(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val selectedPlant = databaseLocal.plantDao().getSelectedPlant()
            withContext(Dispatchers.Main) {
                val nextIntent = if (selectedPlant == null) {
                    Intent(this@SplashActivity, PlantSetupActivity::class.java)
                } else {
                    Intent(this@SplashActivity, MainActivity::class.java)
                }
                startActivity(nextIntent)
                finish()
            }
        }
    }
}

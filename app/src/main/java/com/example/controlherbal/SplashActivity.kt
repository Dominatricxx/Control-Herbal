package com.example.controlherbal

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.database.SensorDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_splash)

            val btnContinue = findViewById<ImageButton>(R.id.btnContinue)

            btnContinue.setOnClickListener {
                checkPlantsAndNavigate()
            }

        } catch (e: Exception) {
            android.util.Log.e("Control Herbal", "Error in SplashActivity: ${e.message}")
        }
    }

    private fun checkPlantsAndNavigate() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SensorDatabase.getInstance(this@SplashActivity)
                val plantCount = db.plantDao().getPlantCount()
                
                withContext(Dispatchers.Main) {
                    if (plantCount > 0) {
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    } else {
                        startActivity(Intent(this@SplashActivity, PlantSetupActivity::class.java))
                    }
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.e("Control Herbal", "Error checking plants: ${e.message}")
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

package com.example.controlherbal

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.database.SensorDatabase
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
            android.util.Log.e("Control Herbal", "Error inflating splash: ${e.message}")
            // Si falla la inflación (posible OOM por imagen pesada), intentamos ir directo
            checkPlantsAndNavigate()
        }
    }

    private fun checkPlantsAndNavigate() {
        lifecycleScope.launch {
            try {
                val db = withContext(Dispatchers.IO) {
                    SensorDatabase.getInstance(this@SplashActivity)
                }
                val plantCount = withContext(Dispatchers.IO) {
                    db.plantDao().getPlantCount()
                }
                
                if (plantCount > 0) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    startActivity(Intent(this@SplashActivity, PlantSetupActivity::class.java))
                }
                finish()
            } catch (e: Exception) {
                android.util.Log.e("Control Herbal", "Error checking plants: ${e.message}")
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        }
    }
}

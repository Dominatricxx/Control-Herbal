package com.example.controlherbal

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_splash)

            val btnContinue = findViewById<ImageButton>(R.id.btnContinue)

            btnContinue.setOnClickListener {
                try {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    android.util.Log.e("Control Herbal", "Error transitioning to MainActivity: ${e.message}")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("Control Herbal", "Error in SplashActivity: ${e.message}")
        }
    }
}

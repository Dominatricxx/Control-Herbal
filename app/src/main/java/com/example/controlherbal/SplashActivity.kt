package com.example.controlherbal

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_splash)

            val ivSplashPlant = findViewById<ImageView>(R.id.ivSplashPlant)

            // Animación de "ola" o balanceo
            val rotateAnim = ObjectAnimator.ofFloat(ivSplashPlant, "rotation", -15f, 15f)
            rotateAnim.duration = 1000
            rotateAnim.repeatCount = ValueAnimator.INFINITE
            rotateAnim.repeatMode = ValueAnimator.REVERSE
            rotateAnim.interpolator = AccelerateDecelerateInterpolator()
            rotateAnim.start()

            // Animación suave de escala para simular crecimiento/movimiento orgánico
            val scaleX = ObjectAnimator.ofFloat(ivSplashPlant, "scaleX", 1f, 1.1f)
            val scaleY = ObjectAnimator.ofFloat(ivSplashPlant, "scaleY", 1f, 1.1f)
            scaleX.duration = 1500
            scaleY.duration = 1500
            scaleX.repeatCount = ValueAnimator.INFINITE
            scaleY.repeatCount = ValueAnimator.INFINITE
            scaleX.repeatMode = ValueAnimator.REVERSE
            scaleY.repeatMode = ValueAnimator.REVERSE
            scaleX.start()
            scaleY.start()

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    android.util.Log.e("Control Herbal", "Error transitioning to MainActivity: ${e.message}")
                }
            }, 2000)
        } catch (e: Exception) {
            android.util.Log.e("Control Herbal", "Error in SplashActivity: ${e.message}")
            // Fallback: intentar ir directo a MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

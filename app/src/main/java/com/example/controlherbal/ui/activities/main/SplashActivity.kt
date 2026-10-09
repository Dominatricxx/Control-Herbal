package com.example.controlherbal.ui.activities.main

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.data.database.SensorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val videoView = findViewById<VideoView>(R.id.videoViewBackground)
        val splashRoot = findViewById<View>(R.id.splashRoot)

        // Reproducir video animado de fondo de pantalla de bienvenida en bucle continuo
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.fondo_animado_ch}")
        videoView.setVideoURI(videoUri)
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0f, 0f)
            videoView.start()
        }

        // Tocar en cualquier parte de la pantalla o video avanza cuando el usuario guste
        splashRoot.setOnClickListener { navigateNext() }
        videoView.setOnClickListener { navigateNext() }
    }

    private fun navigateNext() {
        if (hasNavigated) return
        hasNavigated = true

        // TEMPORAL: Validación de correo / sesión omitida momentáneamente según instrucción del usuario
        /*
        if (!AuthManager.isSignedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        */

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

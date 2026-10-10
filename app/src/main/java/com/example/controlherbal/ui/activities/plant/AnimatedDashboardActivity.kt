package com.example.controlherbal.ui.activities.plant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.controlherbal.ui.components.MainContentScreen
import com.example.controlherbal.ui.theme.ControlHerbalTheme

/**
 * AnimatedDashboardActivity: Muestra la pantalla con el fondo dinámico animado ("Hyper Dynamic Background")
 * y el contenedor flotante en Jetpack Compose y Material 3.
 */
class AnimatedDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ControlHerbalTheme {
                MainContentScreen()
            }
        }
    }
}

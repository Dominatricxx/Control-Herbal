package com.example.controlherbal.frontend.activities.plant

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.frontend.components.AccessibilityScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

class AccessibilityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContent {
            ControlHerbalTheme {
                AccessibilityScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

package com.example.controlherbal.frontend.activities.plant

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.frontend.components.PlantComparisonScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/**
 * Production-ready ComparisonActivity hosting PlantComparisonScreen.
 */
class ComparisonActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContent {
            ControlHerbalTheme {
                PlantComparisonScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

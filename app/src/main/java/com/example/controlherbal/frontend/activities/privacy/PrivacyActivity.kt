package com.example.controlherbal.frontend.activities.privacy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.frontend.components.PrivacyAiScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/**
 * Activity hosting PrivacyAiScreen for AI consents, privacy preferences, and legal consent logs.
 */
class PrivacyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContent {
            ControlHerbalTheme {
                PrivacyAiScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

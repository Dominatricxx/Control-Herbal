package com.example.controlherbal.frontend.activities.privacy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.frontend.components.TermsDocumentsScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/**
 * Activity hosting TermsDocumentsScreen.
 */
class TermsDocumentsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContent {
            ControlHerbalTheme {
                TermsDocumentsScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

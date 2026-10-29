package com.example.controlherbal.ui.activities.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.common.utils.AppConstants

/**
 * Trampolín de compatibilidad: widgets, notificaciones e intents antiguos apuntan aquí.
 * Reenvía los parámetros de forma segura hacia [SectionHostActivity] y finaliza.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forwardToHost()
    }

    private fun forwardToHost() {
        val targetSection = intent?.getIntExtra(SectionHostActivity.EXTRA_SECTION, 0)?.coerceIn(0, 3) ?: 0
        val historyType = intent?.getStringExtra(AppConstants.HISTORY_TYPE_KEY)

        val hostIntent = SectionHostActivity.intent(
            activity = this,
            section = targetSection,
            historyType = historyType
        ).apply {
            intent?.extras?.let { extras ->
                putExtras(extras)
            }
        }

        startActivity(hostIntent)
        finish()
    }
}

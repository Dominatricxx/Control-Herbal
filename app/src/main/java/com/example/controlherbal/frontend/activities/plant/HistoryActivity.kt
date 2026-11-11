package com.example.controlherbal.frontend.activities.plant

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.frontend.components.HistoryLogScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/**
 * Production-ready HistoryActivity hosting HistoryLogScreen for Daily, Weekly, and Monthly sensor logs.
 */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val requestedType = SecurityUtils.getSafeIntentString(intent, AppConstants.HISTORY_TYPE_KEY, AppConstants.HISTORY_DIARIO)
        val initialTab = when (requestedType) {
            AppConstants.HISTORY_SEMANAL -> 1
            AppConstants.HISTORY_MENSUAL -> 2
            else -> 0
        }

        setContent {
            ControlHerbalTheme {
                HistoryLogScreen(
                    initialPeriodTab = initialTab,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

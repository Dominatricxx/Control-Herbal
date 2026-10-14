package com.example.controlherbal.ui.helper

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.app.Activity
import android.content.Intent
import android.view.MenuItem
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.controlherbal.R
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.data.sync.SensorForegroundService
import com.example.controlherbal.ui.activities.auth.ChangePasswordActivity
import com.example.controlherbal.ui.activities.plant.ChatActivity
import com.example.controlherbal.ui.activities.plant.ComparisonActivity
import com.example.controlherbal.ui.activities.plant.HistoryActivity
import com.example.controlherbal.ui.activities.auth.LoginActivity
import com.example.controlherbal.ui.activities.main.MainActivity
import com.example.controlherbal.ui.activities.privacy.PrivacyActivity
import com.example.controlherbal.ui.activities.main.SectionHostActivity

/**
 * NavigationDrawerHandler: Centraliza la lógica de eventos de navegación del Navigation Drawer.
 */
object NavigationDrawerHandler {

    fun handleNavigation(
        activity: Activity,
        drawerLayout: DrawerLayout,
        item: MenuItem,
        onSelectPlants: (() -> Unit)? = null
    ): Boolean {
        when (item.itemId) {
            R.id.nav_main -> {
                if (activity is SectionHostActivity) activity.selectPage(0)
                else activity.startActivity(SectionHostActivity.intent(activity, 0))
            }
            R.id.nav_daily -> openHistory(activity, AppConstants.HISTORY_DIARIO)
            R.id.nav_weekly -> openHistory(activity, AppConstants.HISTORY_SEMANAL)
            R.id.nav_monthly -> openHistory(activity, AppConstants.HISTORY_MENSUAL)
            R.id.nav_plants -> {
                onSelectPlants?.invoke()
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            R.id.nav_comparison -> if (activity is SectionHostActivity) activity.selectPage(2) else activity.startActivity(SectionHostActivity.intent(activity, 2))
            R.id.nav_chat -> activity.startActivity(Intent(activity, ChatActivity::class.java))
            R.id.nav_animated_dashboard -> activity.startActivity(Intent(activity, AnimatedDashboardActivity::class.java))
            R.id.nav_privacy -> if (activity is SectionHostActivity) activity.selectPage(3) else activity.startActivity(SectionHostActivity.intent(activity, 3))
            R.id.nav_change_password -> activity.startActivity(Intent(activity, ChangePasswordActivity::class.java))
            R.id.nav_logout -> signOutAndExit(activity)
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun openHistory(activity: Activity, historyType: String) {
        activity.startActivity(SectionHostActivity.intent(activity, 1, historyType))
    }

    private fun signOutAndExit(activity: Activity) {
        activity.stopService(Intent(activity, SensorForegroundService::class.java))
        AuthManager.signOut()
        val intent = Intent(activity, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}

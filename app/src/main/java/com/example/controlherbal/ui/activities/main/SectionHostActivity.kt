package com.example.controlherbal.ui.activities.main

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.cardview.widget.CardView
import androidx.viewpager2.widget.ViewPager2
import com.example.controlherbal.R
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.ui.components.AnimatedGradientBackground
import com.example.controlherbal.ui.components.GooeyBottomBar
import com.example.controlherbal.ui.theme.ControlHerbalTheme
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

/** Una sola Activity mantiene las cuatro secciones en un ViewPager2; las pantallas externas siguen
 * siendo Activities y al cerrarse regresan al mismo host/página. */
class SectionHostActivity : AppCompatActivity() {
    private lateinit var pager: ViewPager2
    private lateinit var drawerLayout: DrawerLayout
    private var selectedPage by mutableIntStateOf(0)
    private var pageProgress by mutableFloatStateOf(0f)
    private var historyType = AppConstants.HISTORY_DIARIO

    companion object {
        const val EXTRA_SECTION = "carousel_section"
        fun intent(activity: android.content.Context, section: Int = 0, historyType: String? = null) =
            Intent(activity, SectionHostActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_SECTION, section.coerceIn(0, 3))
                historyType?.let { putExtra(AppConstants.HISTORY_TYPE_KEY, it) }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        historyType = intent.getStringExtra(AppConstants.HISTORY_TYPE_KEY)
            ?.takeIf { it in setOf(AppConstants.HISTORY_DIARIO, AppConstants.HISTORY_SEMANAL, AppConstants.HISTORY_MENSUAL) }
            ?: AppConstants.HISTORY_DIARIO

        val drawer = DrawerLayout(this)
        drawerLayout = drawer
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, dp(16))
            clipChildren = false
            clipToPadding = false
        }
        val background = ComposeView(this).apply {
            setContent {
                ControlHerbalTheme {
                    AnimatedGradientBackground(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize()
                    )
                }
            }
        }
        drawer.addView(background, DrawerLayout.LayoutParams(-1, -1))

        pager = ViewPager2(this).apply {
            id = View.generateViewId()
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 3
            adapter = SectionPagerAdapter(this@SectionHostActivity, historyType)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    pageProgress = (position + positionOffset).coerceIn(0f, 3f)
                }

                override fun onPageSelected(position: Int) {
                    selectedPage = position
                    pageProgress = position.toFloat()
                }
            })
        }
        val pageCard = CardView(this).apply {
            radius = dp(28).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(android.graphics.Color.WHITE)
            clipToPadding = true
            clipChildren = true
        }
        pageCard.addView(pager, ViewGroup.LayoutParams(-1, -1))
        content.addView(pageCard, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
            bottomMargin = dp(24)
        })
        val bottomBar = ComposeView(this).apply {
            setContent {
                val tabs = listOf(
                    Triple("Panel", Icons.Default.Home, "Panel principal"),
                    Triple("Sensores", Icons.Default.Search, "Historial"),
                    Triple("Comparar", Icons.Default.Star, "Comparador"),
                    Triple("Privacidad", Icons.Default.Person, "Privacidad")
                )
                ControlHerbalTheme {
                    GooeyBottomBar(
                        tabs = tabs,
                        selectedTab = selectedPage,
                        onTabSelected = { index -> pager.setCurrentItem(index, true) },
                        selectionProgress = pageProgress
                    )
                }
            }
        }
        bottomBar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        content.addView(bottomBar, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })
        drawer.addView(content, DrawerLayout.LayoutParams(-1, -1))

        val nav = NavigationView(this).apply {
            inflateMenu(R.menu.nav_menu)
            setNavigationItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_main -> pager.currentItem = 0
                    R.id.nav_daily, R.id.nav_weekly, R.id.nav_monthly -> {
                        historyType = when (item.itemId) {
                            R.id.nav_weekly -> AppConstants.HISTORY_SEMANAL
                            R.id.nav_monthly -> AppConstants.HISTORY_MENSUAL
                            else -> AppConstants.HISTORY_DIARIO
                        }
                        pager.currentItem = 1
                        pager.post {
                            (supportFragmentManager.findFragmentByTag("f1") as? HistorySectionFragment)
                                ?.setHistoryType(historyType)
                        }
                    }
                    R.id.nav_comparison -> pager.currentItem = 2
                    R.id.nav_privacy -> pager.currentItem = 3
                    R.id.nav_plants -> {
                        pager.currentItem = 0
                        pager.post {
                            (supportFragmentManager.findFragmentByTag("f0") as? DashboardSectionFragment)?.showPlantSelection()
                        }
                    }
                    R.id.nav_chat -> startActivity(Intent(this@SectionHostActivity, com.example.controlherbal.ui.activities.plant.ChatActivity::class.java))
                    R.id.nav_animated_dashboard -> startActivity(Intent(this@SectionHostActivity, com.example.controlherbal.ui.activities.plant.AnimatedDashboardActivity::class.java))
                    R.id.nav_change_password -> startActivity(Intent(this@SectionHostActivity, com.example.controlherbal.ui.activities.auth.ChangePasswordActivity::class.java))
                    R.id.nav_logout -> com.example.controlherbal.ui.helper.NavigationDrawerHandler.handleNavigation(
                        this@SectionHostActivity, drawer, item
                    )
                }
                drawer.closeDrawer(GravityCompat.START)
                true
            }
        }
        drawer.addView(nav, DrawerLayout.LayoutParams(dp(304), -1, Gravity.START))
        setContentView(drawer)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, dp(24) + systemBars.top, 0, dp(16) + systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(drawer)
        lifecycleScope.launch {
            val plantCount = SensorDatabase.getInstance(this@SectionHostActivity).plantDao().getPlantCount()
            nav.menu.findItem(R.id.nav_plants).isVisible = plantCount >= 2
            nav.menu.findItem(R.id.nav_comparison).isVisible = plantCount >= 2
        }
        pager.setCurrentItem(intent.getIntExtra(EXTRA_SECTION, 0).coerceIn(0, 3), false)
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    fun selectPage(index: Int) { pager.setCurrentItem(index.coerceIn(0, 3), true) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(AppConstants.HISTORY_TYPE_KEY)?.let { requested ->
            if (requested in setOf(AppConstants.HISTORY_DIARIO, AppConstants.HISTORY_SEMANAL, AppConstants.HISTORY_MENSUAL)) {
                historyType = requested
                pager.currentItem = 1
                pager.post { (supportFragmentManager.findFragmentByTag("f1") as? HistorySectionFragment)?.setHistoryType(requested) }
            }
        }
        pager.setCurrentItem(intent.getIntExtra(EXTRA_SECTION, pager.currentItem).coerceIn(0, 3), false)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

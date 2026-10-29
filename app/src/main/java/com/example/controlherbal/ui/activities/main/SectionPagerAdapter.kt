package com.example.controlherbal.ui.activities.main

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Adaptador de ViewPager2 para gestionar las cuatro secciones principales de la app.
 */
internal class SectionPagerAdapter(
    activity: SectionHostActivity,
    private val historyType: String
) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> DashboardSectionFragment()
        1 -> HistorySectionFragment.newInstance(historyType)
        2 -> ComparisonSectionFragment()
        else -> PrivacySectionFragment()
    }
}

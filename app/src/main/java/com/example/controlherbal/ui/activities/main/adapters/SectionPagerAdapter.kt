package com.example.controlherbal.ui.activities.main.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.controlherbal.ui.activities.main.SectionHostActivity
import com.example.controlherbal.ui.activities.main.fragments.ComparisonSectionFragment
import com.example.controlherbal.ui.activities.main.fragments.DashboardSectionFragment
import com.example.controlherbal.ui.activities.main.fragments.HistorySectionFragment
import com.example.controlherbal.ui.activities.main.fragments.PrivacySectionFragment

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

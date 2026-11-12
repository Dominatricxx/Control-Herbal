package com.example.controlherbal.frontend.activities.main.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.controlherbal.frontend.activities.main.SectionHostActivity
import com.example.controlherbal.frontend.activities.main.fragments.CalendarSectionFragment
import com.example.controlherbal.frontend.activities.main.fragments.DashboardSectionFragment
import com.example.controlherbal.frontend.activities.main.fragments.MyPlantsSectionFragment
import com.example.controlherbal.frontend.activities.main.fragments.ProfileSectionFragment

/**
 * Adaptador de ViewPager2 para gestionar las cuatro secciones principales de la app en la barra inferior:
 * 0 -> Inicio (Dashboard)
 * 1 -> Plantas (MyPlants)
 * 2 -> Calendario (Calendar)
 * 3 -> Perfil (Profile)
 */
internal class SectionPagerAdapter(
    activity: SectionHostActivity,
    private val historyType: String
) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> DashboardSectionFragment()
        1 -> MyPlantsSectionFragment()
        2 -> CalendarSectionFragment()
        else -> ProfileSectionFragment()
    }
}

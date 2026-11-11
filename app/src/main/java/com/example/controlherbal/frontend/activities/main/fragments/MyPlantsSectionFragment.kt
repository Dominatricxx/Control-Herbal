package com.example.controlherbal.frontend.activities.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.controlherbal.frontend.components.MyPlantsScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/**
 * Section Fragment hosting the new MyPlantsScreen inside SectionHostActivity's ViewPager2 (Tab 1 - "plantas").
 */
class MyPlantsSectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ControlHerbalTheme {
                    MyPlantsScreen()
                }
            }
        }
    }
}

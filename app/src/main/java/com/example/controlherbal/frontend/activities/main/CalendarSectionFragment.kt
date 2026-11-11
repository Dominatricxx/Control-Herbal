package com.example.controlherbal.frontend.activities.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.controlherbal.frontend.components.CalendarScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/**
 * Section Fragment hosting the new CalendarScreen inside SectionHostActivity's ViewPager2.
 */
class CalendarSectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ControlHerbalTheme {
                    CalendarScreen()
                }
            }
        }
    }
}

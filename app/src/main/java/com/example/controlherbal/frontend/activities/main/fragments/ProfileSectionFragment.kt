package com.example.controlherbal.frontend.activities.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.controlherbal.frontend.components.ProfileScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/**
 * Section Fragment hosting the new ProfileScreen inside SectionHostActivity's ViewPager2 (Tab 3 - "perfil").
 */
class ProfileSectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ControlHerbalTheme {
                    ProfileScreen()
                }
            }
        }
    }
}

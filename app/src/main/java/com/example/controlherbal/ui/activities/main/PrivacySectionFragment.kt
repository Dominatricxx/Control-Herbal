package com.example.controlherbal.ui.activities.main

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.accessibility.PaletteMode
import com.example.controlherbal.common.accessibility.StatusLevel
import com.example.controlherbal.common.accessibility.StatusPalette
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.legal.AiConsent
import com.example.controlherbal.common.legal.ConsentManager
import com.example.controlherbal.common.legal.DataControl
import com.example.controlherbal.common.legal.LegalDoc
import com.example.controlherbal.data.sync.SensorForegroundService
import com.example.controlherbal.ui.activities.auth.LoginActivity
import com.example.controlherbal.ui.activities.privacy.LegalActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Centro de privacidad completo como página del carrusel. */
class PrivacySectionFragment : Fragment() {
    private lateinit var switchAi: SwitchMaterial
    private lateinit var palette: RadioGroup

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.activity_privacy, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        view.findViewById<ImageButton>(R.id.btnPrivacyBack).apply {
            isVisible = false
        }
        val accepted = ConsentManager.acceptedAt(requireContext())
        view.findViewById<TextView>(R.id.tvAcceptedInfo).text = getString(
            R.string.privacy_accepted_info,
            ConsentManager.acceptedVersion(requireContext()) ?: "-",
            if (accepted > 0) DateFormat.getDateTimeInstance().format(Date(accepted)) else "-"
        )
        mapOf(
            R.id.btnDocPrivacy to LegalDoc.PRIVACY,
            R.id.btnDocTerms to LegalDoc.TERMS,
            R.id.btnDocCookies to LegalDoc.COOKIES,
            R.id.btnDocConsents to LegalDoc.CONSENTS
        ).forEach { (id, doc) ->
            view.findViewById<Button>(id).setOnClickListener {
                startActivity(LegalActivity.intent(requireContext(), doc))
            }
        }
        switchAi = view.findViewById(R.id.switchAi)
        switchAi.isChecked = AiConsent.isGranted(requireContext())
        switchAi.setOnClickListener {
            if (switchAi.isChecked) {
                switchAi.isChecked = false
                AiConsent.ensure(requireActivity(), onGranted = { switchAi.isChecked = true })
            } else AiConsent.revoke(requireContext())
        }
        setupPalette(view)
        view.findViewById<Button>(R.id.btnExport).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val json = DataControl.exportLocalJson(requireContext())
                if (json == null) Toast.makeText(requireContext(), R.string.privacy_export_empty, Toast.LENGTH_SHORT).show()
                else startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Control Herbal - mis datos")
                    putExtra(Intent.EXTRA_TEXT, json)
                }, getString(R.string.privacy_export_chooser)))
            }
        }
        view.findViewById<Button>(R.id.btnWipe).setOnClickListener {
            confirm(R.string.privacy_wipe, R.string.privacy_wipe_confirm) {
                viewLifecycleOwner.lifecycleScope.launch {
                    requireContext().stopService(Intent(requireContext(), SensorForegroundService::class.java))
                    DataControl.wipeLocal(requireContext())
                    Toast.makeText(requireContext(), R.string.privacy_done, Toast.LENGTH_SHORT).show()
                    returnToSplash()
                }
            }
        }
        view.findViewById<Button>(R.id.btnDeleteAccount).setOnClickListener {
            confirm(R.string.privacy_delete_account, R.string.privacy_delete_confirm) {
                viewLifecycleOwner.lifecycleScope.launch {
                    when (DataControl.deleteAccountAndData(requireContext())) {
                        AuthManager.DeleteResult.SUCCESS -> {
                            requireContext().stopService(Intent(requireContext(), SensorForegroundService::class.java))
                            Toast.makeText(requireContext(), R.string.privacy_done, Toast.LENGTH_SHORT).show()
                            returnToSplash()
                        }
                        AuthManager.DeleteResult.REQUIRES_RECENT_LOGIN -> Toast.makeText(requireContext(), R.string.privacy_delete_relogin, Toast.LENGTH_LONG).show()
                        AuthManager.DeleteResult.FAILED -> Toast.makeText(requireContext(), R.string.privacy_delete_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupPalette(view: View) {
        palette = view.findViewById(R.id.rgPalette)
        PaletteMode.values().forEach { mode ->
            palette.addView(RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = mode.label
                tag = mode
                textSize = 16f
                minHeight = (48 * resources.displayMetrics.density).toInt()
                isChecked = mode == StatusPalette.mode(requireContext())
            })
        }
        palette.setOnCheckedChangeListener { group, checked ->
            (group.findViewById<RadioButton>(checked)?.tag as? PaletteMode)?.let {
                StatusPalette.setMode(requireContext(), it)
                renderPalette(view)
            }
        }
        renderPalette(view)
    }

    private fun renderPalette(view: View) {
        val colors = StatusPalette.current(requireContext())
        listOf(
            Triple(R.id.previewOk, StatusLevel.OK, R.string.privacy_preview_ok),
            Triple(R.id.previewWarn, StatusLevel.WARN, R.string.privacy_preview_warn),
            Triple(R.id.previewCritical, StatusLevel.CRITICAL, R.string.privacy_preview_critical)
        ).forEach { (id, level, string) ->
            view.findViewById<TextView>(id).apply {
                text = "${level.symbol} ${getString(string)}"
                background = GradientDrawable().apply {
                    cornerRadius = 16f * resources.displayMetrics.density
                    setColor(colors.forLevel(level))
                }
            }
        }
    }

    private fun confirm(title: Int, message: Int, action: () -> Unit) {
        AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message)
            .setPositiveButton(title) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun returnToSplash() {
        startActivity(Intent(requireContext(), SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}

package com.example.controlherbal.ui.activities.privacy

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.legal.AiConsent
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.legal.ConsentManager
import com.example.controlherbal.common.legal.DataControl
import com.example.controlherbal.common.legal.LegalDoc
import com.example.controlherbal.common.accessibility.PaletteMode
import com.example.controlherbal.common.accessibility.StatusLevel
import com.example.controlherbal.common.accessibility.StatusPalette
import com.example.controlherbal.data.sync.SensorForegroundService
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Centro de privacidad y accesibilidad: documentos, consentimiento de IA, paleta y control de datos. */
class PrivacyActivity : AppCompatActivity() {

    private lateinit var switchAi: SwitchMaterial
    private lateinit var rgPalette: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        findViewById<ImageButton>(R.id.btnPrivacyBack).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        val at = ConsentManager.acceptedAt(this)
        findViewById<TextView>(R.id.tvAcceptedInfo).text = getString(
            R.string.privacy_accepted_info,
            ConsentManager.acceptedVersion(this) ?: "-",
            if (at > 0) DateFormat.getDateTimeInstance().format(Date(at)) else "-"
        )

        mapOf(
            R.id.btnDocPrivacy to LegalDoc.PRIVACY, R.id.btnDocTerms to LegalDoc.TERMS,
            R.id.btnDocCookies to LegalDoc.COOKIES, R.id.btnDocConsents to LegalDoc.CONSENTS
        ).forEach { (id, doc) ->
            findViewById<Button>(id).setOnClickListener { startActivity(LegalActivity.intent(this, doc)) }
        }

        setupAiSwitch()
        setupPalette()
        setupDataControls()
    }

    // ------------------------------------------------------------------ IA
    private fun setupAiSwitch() {
        switchAi = findViewById(R.id.switchAi)
        switchAi.isChecked = AiConsent.isGranted(this)
        // Se usa clic (no "checked change") para no entrar en bucle al ajustar el estado por código.
        switchAi.setOnClickListener {
            if (switchAi.isChecked) {
                switchAi.isChecked = false   // hasta que la persona acepte en el diálogo
                AiConsent.ensure(this, onGranted = { switchAi.isChecked = true })
            } else {
                AiConsent.revoke(this)
            }
        }
    }

    // ------------------------------------------------------------------ Paleta
    private fun setupPalette() {
        rgPalette = findViewById(R.id.rgPalette)
        val current = StatusPalette.mode(this)
        PaletteMode.values().forEach { mode ->
            val rb = RadioButton(this).apply {
                id = android.view.View.generateViewId()
                text = mode.label
                tag = mode
                textSize = 16f
                setTextColor(0xFF212121.toInt())
                minHeight = (48 * resources.displayMetrics.density).toInt()
                isChecked = mode == current
            }
            rgPalette.addView(rb)
        }
        rgPalette.setOnCheckedChangeListener { group, checkedId ->
            val mode = group.findViewById<RadioButton>(checkedId)?.tag as? PaletteMode ?: return@setOnCheckedChangeListener
            StatusPalette.setMode(this, mode)
            renderPreview()
        }
        renderPreview()
    }

    private fun renderPreview() {
        val pal = StatusPalette.current(this)
        listOf(
            Triple(R.id.previewOk, StatusLevel.OK, getString(R.string.privacy_preview_ok)),
            Triple(R.id.previewWarn, StatusLevel.WARN, getString(R.string.privacy_preview_warn)),
            Triple(R.id.previewCritical, StatusLevel.CRITICAL, getString(R.string.privacy_preview_critical))
        ).forEach { (id, level, label) ->
            findViewById<TextView>(id).apply {
                text = "${level.symbol} $label"      // símbolo + texto + color
                background = GradientDrawable().apply {
                    cornerRadius = 16f * resources.displayMetrics.density
                    setColor(pal.forLevel(level))
                }
            }
        }
    }

    // ------------------------------------------------------------------ Datos
    private fun setupDataControls() {
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            lifecycleScope.launch {
                val json = DataControl.exportLocalJson(this@PrivacyActivity)
                if (json == null) {
                    toast(R.string.privacy_export_empty)
                } else {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Control Herbal - mis datos")
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    startActivity(Intent.createChooser(send, getString(R.string.privacy_export_chooser)))
                }
            }
        }

        findViewById<Button>(R.id.btnWipe).setOnClickListener {
            confirm(R.string.privacy_wipe, R.string.privacy_wipe_confirm) {
                lifecycleScope.launch {
                    stopService(Intent(this@PrivacyActivity, SensorForegroundService::class.java))
                    DataControl.wipeLocal(this@PrivacyActivity)
                    toast(R.string.privacy_done)
                    restartFromSplash()
                }
            }
        }

        findViewById<Button>(R.id.btnDeleteAccount).setOnClickListener {
            confirm(R.string.privacy_delete_account, R.string.privacy_delete_confirm) {
                lifecycleScope.launch {
                    when (DataControl.deleteAccountAndData(this@PrivacyActivity)) {
                        AuthManager.DeleteResult.SUCCESS -> {
                            stopService(Intent(this@PrivacyActivity, SensorForegroundService::class.java))
                            toast(R.string.privacy_done)
                            restartFromSplash()
                        }
                        AuthManager.DeleteResult.REQUIRES_RECENT_LOGIN -> toast(R.string.privacy_delete_relogin, long = true)
                        AuthManager.DeleteResult.FAILED -> toast(R.string.privacy_delete_failed, long = true)
                    }
                }
            }
        }
    }

    private fun confirm(titleRes: Int, messageRes: Int, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(titleRes) { _, _ -> onYes() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(res: Int, long: Boolean = false) =
        Toast.makeText(this, res, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private fun restartFromSplash() {
        startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}

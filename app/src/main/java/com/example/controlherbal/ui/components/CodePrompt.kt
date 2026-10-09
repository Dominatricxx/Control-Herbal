package com.example.controlherbal.ui.components

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
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.PersistableBundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.common.auth.BackupCodesClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Diálogo para pedir un código (TOTP de 6 dígitos o código de respaldo). No cancelable tocando fuera. */
object CodePrompt {

    class Config(
        val title: String,
        val message: String?,
        val hint: String,
        val numeric: Boolean,
        val maxLength: Int,
        val positive: String,
        val neutral: String? = null,
    )

    fun show(
        activity: Activity,
        config: Config,
        onSubmit: (String) -> Unit,
        onNeutral: (() -> Unit)? = null,
        onCancel: () -> Unit = {},
    ) {
        val edit = EditText(activity).apply {
            hint = config.hint
            gravity = Gravity.CENTER
            inputType = if (config.numeric) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            filters = arrayOf(InputFilter.LengthFilter(config.maxLength))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            textSize = 22f
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = FrameLayout(activity).apply { setPadding(pad, pad / 2, pad, 0); addView(edit) }

        val builder = AlertDialog.Builder(activity)
            .setTitle(config.title)
            .setView(box)
            .setCancelable(false)
            .setPositiveButton(config.positive) { _, _ -> onSubmit(edit.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
        config.message?.let { builder.setMessage(it) }
        config.neutral?.let { label -> builder.setNeutralButton(label) { _, _ -> onNeutral?.invoke() } }
        val dialog = builder.create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }
}

/** Presentación y regeneración de los códigos de respaldo de 2FA. */
object BackupCodesUi {

    /** Muestra los códigos (una sola vez) y espera a que el usuario confirme que los guardó. */
    suspend fun showCodes(activity: Activity, codes: List<String>) = suspendCancellableCoroutine<Unit> { cont ->
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val list = TextView(activity).apply {
            text = codes.joinToString("\n")
            typeface = Typeface.MONOSPACE
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(pad, pad / 2, pad, 0)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.backup_codes_title)
            .setMessage(R.string.backup_codes_message)
            .setView(list)
            .setCancelable(false)
            .setPositiveButton(R.string.backup_codes_saved) { _, _ -> if (cont.isActive) cont.resume(Unit) }
            .setNeutralButton(R.string.backup_codes_copy, null) // se asigna abajo para que no cierre el diálogo
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("backup", codes.joinToString("\n"))
            // Que el sistema no muestre una vista previa del contenido copiado.
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            cm.setPrimaryClip(clip)
            Toast.makeText(activity, R.string.backup_codes_copied, Toast.LENGTH_SHORT).show()
        }
        cont.invokeOnCancellation { dialog.dismiss() }
    }

    /** Tras un inicio de sesión con 2FA: si no hay códigos, créalos y muéstralos. Nunca bloquea el acceso. */
    suspend fun offerIfNone(activity: Activity) {
        val left = BackupCodesClient.remaining().getOrNull() ?: return
        if (left > 0) return
        val codes = BackupCodesClient.generate().getOrNull() ?: return
        showCodes(activity, codes)
    }

    /** Desde el menú: genera un juego nuevo (invalida el anterior) tras confirmar. */
    fun regenerate(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.backup_regen_title)
            .setMessage(R.string.backup_regen_message)
            .setPositiveButton(R.string.backup_regen_confirm) { _, _ ->
                activity.lifecycleScope.launch {
                    BackupCodesClient.generate()
                        .onSuccess { showCodes(activity, it) }
                        .onFailure { Toast.makeText(activity, R.string.backup_error, Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

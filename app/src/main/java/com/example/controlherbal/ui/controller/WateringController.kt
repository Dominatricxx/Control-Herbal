package com.example.controlherbal.ui.controller

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
import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.controlherbal.R
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.domain.usecase.ExecuteWateringUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * WateringController: Encapsula el diálogo de confirmación de riego, cooldowns y envío de orden.
 */
class WateringController(
    private val executeWateringUseCase: ExecuteWateringUseCase
) {

    fun showWateringDialog(
        activity: Activity,
        plant: Plant,
        scope: CoroutineScope,
        onWateringSent: () -> Unit
    ) {
        val uid = AuthManager.uid()
        if (uid == null) {
            Toast.makeText(activity, "Inicia sesión para enviar órdenes de riego", Toast.LENGTH_LONG).show()
            return
        }

        val guardPrefs = activity.getSharedPreferences(AppConstants.PREFS_WATERING, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = guardPrefs.getLong(AppConstants.KEY_LAST_WATERING_COMMAND, 0L)
        if (now - last in 0 until AppConstants.WATERING_COOLDOWN_MS) {
            val wait = (AppConstants.WATERING_COOLDOWN_MS - (now - last)) / 1000 + 1
            Toast.makeText(activity, "Espera $wait s antes de enviar otra orden de riego", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_confirm, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnAction = dialogView.findViewById<Button>(R.id.btnAction)

        val safeName = SecurityUtils.sanitizeText(plant.name)
        tvTitle.text = "Riego Manual 💧"
        tvMessage.text = "¿Deseas activar el sistema de riego automático para '$safeName'?"
        btnAction.text = "ACTIVAR"
        btnAction.setBackgroundColor(Color.parseColor("#1E88E5"))

        val dialog = AlertDialog.Builder(activity).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAction.setOnClickListener {
            guardPrefs.edit().putLong(AppConstants.KEY_LAST_WATERING_COMMAND, now).apply()

            val duration = when {
                plant.environment.contains("Luz", ignoreCase = true) -> 10
                plant.environment.contains("Sombra", ignoreCase = true) -> 30
                else -> 20
            }.coerceIn(1, AppConstants.MAX_WATERING_SECONDS)

            scope.launch(Dispatchers.Main) {
                val result = executeWateringUseCase(duration, uid)
                result.onSuccess {
                    Toast.makeText(activity, "Comando enviado al sistema de riego 💧", Toast.LENGTH_SHORT).show()
                    onWateringSent()
                }.onFailure { e ->
                    Toast.makeText(activity, e.message ?: "El servidor rechazó la orden de riego", Toast.LENGTH_LONG).show()
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}

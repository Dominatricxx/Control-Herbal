package com.example.controlherbal.common.legal

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/**
 * AiConsent: aviso y consentimiento explícito antes de enviar fotos o texto a un servicio
 * de IA de terceros (Google, vía Firebase AI Logic).
 */
object AiConsent {

    private fun prefs(context: Context) =
        context.getSharedPreferences(AppConstants.PREFS_AI_CONSENT, Context.MODE_PRIVATE)

    fun isGranted(context: Context): Boolean =
        prefs(context).getBoolean(AppConstants.KEY_AI_CONSENT_GRANTED, false)

    /** Retira el consentimiento: la app volverá a pedirlo antes de enviar nada a la IA. */
    fun revoke(context: Context) {
        prefs(context).edit().putBoolean(AppConstants.KEY_AI_CONSENT_GRANTED, false).apply()
    }

    /** Ejecuta [onGranted] si ya hay consentimiento; si no, muestra el aviso y espera la respuesta. */
    fun ensure(activity: Activity, onDenied: (() -> Unit)? = null, onGranted: () -> Unit) {
        if (isGranted(activity)) {
            onGranted()
            return
        }

        var dialog: AlertDialog? = null
        val composeView = ComposeView(activity).apply {
            setContent {
                ControlHerbalTheme {
                    AlertDialog(
                        onDismissRequest = {
                            dialog?.dismiss()
                            onDenied?.invoke()
                        },
                        containerColor = Color(0xFF252527),
                        shape = RoundedCornerShape(24.dp),
                        title = {
                            Text(
                                text = "Uso de inteligencia artificial",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Si aceptas, esta función enviará a Google (servicio Gemini, mediante Firebase AI Logic) " +
                                            "las fotos que tomes aquí y el texto que escribas en el asistente, junto con el tipo de planta. " +
                                            "No se envía el nombre que le diste a la planta ni tu correo.",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Text(
                                    text = "Evita fotografiar personas, documentos o información privada y no escribas datos personales.",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = "La IA puede equivocarse. Nunca decidas consumir una planta ni tratar a una persona o animal basándote en sus respuestas.",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = "Puedes retirar este permiso en Menú, Privacidad e IA.",
                                    color = Color(0xFFA5D6A7),
                                    fontSize = 13.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    prefs(activity).edit().putBoolean(AppConstants.KEY_AI_CONSENT_GRANTED, true).apply()
                                    dialog?.dismiss()
                                    onGranted()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF288B46))
                            ) {
                                Text("ACEPTO", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    dialog?.dismiss()
                                    onDenied?.invoke()
                                }
                            ) {
                                Text("CANCELAR", color = Color(0xFFCCCCCC))
                            }
                        }
                    )
                }
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setView(composeView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}

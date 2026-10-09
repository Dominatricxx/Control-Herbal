package com.example.controlherbal.ui.activities.main

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.controlherbal.R
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.security.SecurityUtils

fun MainActivity.handleSignificantNotifications(isConnected: Boolean, cause: String) {
    if (!isConnected) {
        if (lastAlertState != 1) {
            sendNotification("¡Alerta de Conexión!", "Se ha perdido la conexión con el sensor ESP32 de tu planta.")
            lastAlertState = 1
        }
        return
    }
    val safeCause = SecurityUtils.sanitizeText(cause, 200)
    if (safeCause.contains("Riesgo Crítico", ignoreCase = true) || safeCause.contains("Crítico", ignoreCase = true)) {
        if (lastAlertState != 2) {
            sendNotification("⚠️ Alerta Crítica en tu Planta", safeCause)
            lastAlertState = 2
        }
    } else {
        lastAlertState = 0
    }
}

fun MainActivity.sendNotification(title: String, message: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    }
    val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val builder = NotificationCompat.Builder(this, AppConstants.ALERTS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_leaf)
        .setContentTitle(SecurityUtils.sanitizeText(title))
        .setContentText(SecurityUtils.sanitizeText(message, 300))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(AppConstants.NOTIFICATION_ID_CRITICAL, builder.build())
}

fun MainActivity.sendSyncNotification(title: String, message: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }
    val builder = NotificationCompat.Builder(this, AppConstants.ALERTS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_herbal)
        .setContentTitle(SecurityUtils.sanitizeText(title))
        .setContentText(SecurityUtils.sanitizeText(message, 300))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(AppConstants.NOTIFICATION_ID_SYNC, builder.build())
}

fun MainActivity.requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}

fun MainActivity.createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            AppConstants.ALERTS_CHANNEL_ID,
            AppConstants.ALERTS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = AppConstants.ALERTS_CHANNEL_DESC
        }
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

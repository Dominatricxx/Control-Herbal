package com.example.controlherbal

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

import android.app.Application
import com.example.controlherbal.common.AppCheckInstaller

/**
 * Application: inicializa Firebase App Check lo antes posible para que TODAS las
 * llamadas a Realtime Database y Firebase AI Logic salgan con token de atestación.
 */
class ControlHerbalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCheckInstaller.install()
    }
}

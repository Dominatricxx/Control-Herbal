package com.example.controlherbal

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

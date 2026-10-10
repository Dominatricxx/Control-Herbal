package com.example.controlherbal

import android.app.Application
import com.example.controlherbal.common.AppCheckInstaller
import com.example.controlherbal.data.database.PlantDatabaseSeeder
import com.example.controlherbal.data.database.SensorDatabase
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application: inicializa Firebase App Check lo antes posible para que TODAS las
 * llamadas a Realtime Database y Firebase AI Logic salgan con token de atestación.
 */
class ControlHerbalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppCheckInstaller.install()
        val db = SensorDatabase.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
            PlantDatabaseSeeder.seedCatalogIfEmpty(db)
        }
    }
}

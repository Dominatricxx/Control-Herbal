package com.example.controlherbal

import android.app.Application
import com.example.controlherbal.common.AppCheckInstaller
import com.example.controlherbal.data.database.PlantDatabaseSeeder
import com.example.controlherbal.di.AppContainer
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application: inicializa Firebase App Check y expone el contenedor de dependencias [AppContainer].
 */
class ControlHerbalApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppCheckInstaller.install()

        // Inicialización única del contenedor de dependencias manual (Service Locator)
        appContainer = AppContainer(this)

        CoroutineScope(Dispatchers.IO).launch {
            PlantDatabaseSeeder.seedCatalogIfEmpty(appContainer.database)
        }
    }
}

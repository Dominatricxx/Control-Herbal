import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.controlherbal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.controlherbal"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Firebase AI Logic & Auth & App Check
    implementation(libs.firebase.ai)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // Firebase Realtime Database
    implementation(libs.firebase.database)

    implementation(libs.tensorflow.lite)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // MPAndroidChart
    implementation(libs.mpandroidchart)

    // Corrutinas
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Jetpack Glance (Widgets)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}

val verifyModelIntegrity = tasks.register("verifyModelIntegrity") {
    group = "verification"
    description = "Verifica que herbal_model.tflite coincide con su huella SHA-256 versionada."
    val assetsDir = layout.projectDirectory.dir("src/main/assets")
    doLast {
        val model = assetsDir.file("herbal_model.tflite").asFile
        val shaFile = assetsDir.file("herbal_model.tflite.sha256").asFile
        if (!model.exists()) {
            logger.lifecycle("verifyModelIntegrity: no hay modelo embebido, se omite.")
            return@doLast
        }
        if (!shaFile.exists()) {
            throw GradleException("Falta ${shaFile.name}: genera el modelo con ml/train_herbal_model.py o calcula su SHA-256.")
        }
        val expected = shaFile.readText().trim().split(Regex("\\s+")).first().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            throw GradleException("herbal_model.tflite no coincide con su huella SHA-256 (esperada $expected, real $actual).")
        }
        logger.lifecycle("verifyModelIntegrity: OK ($actual)")
    }
}
tasks.named("preBuild") { dependsOn(verifyModelIntegrity) }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

// Escapa un valor para incrustarlo como literal String en BuildConfig.
fun String.asBuildConfigString(): String =
    "\"" + this.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.controlherbal.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.controlherbal.wear"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        // Cuenta de SOLO LECTURA ("viewer") para el reloj. Se lee de gradle.properties de usuario
        // (~/.gradle/gradle.properties) o de variables de entorno; nunca se versiona.
        // Atención: todo lo embebido en un APK es extraíble; por eso esta cuenta solo
        // tiene permiso de lectura sobre /sensor (ver firebase/database.rules.json).
        val viewerEmail = (project.findProperty("herbal.viewerEmail") as String?) ?: System.getenv("HERBAL_VIEWER_EMAIL") ?: ""
        val viewerPassword = (project.findProperty("herbal.viewerPassword") as String?) ?: System.getenv("HERBAL_VIEWER_PASSWORD") ?: ""
        buildConfigField("String", "VIEWER_EMAIL", viewerEmail.asBuildConfigString())
        buildConfigField("String", "VIEWER_PASSWORD", viewerPassword.asBuildConfigString())
    }
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.play.services.wearable)
    implementation("androidx.percentlayout:percentlayout:1.0.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.wear:wear:1.4.0")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
}

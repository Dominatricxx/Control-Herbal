plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.json:json:20240303")
    // Removed Vico as it's Android-only AAR based.
    // Switching to manual Canvas drawing implementation for Desktop compatibility.
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}

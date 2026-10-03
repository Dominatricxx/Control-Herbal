pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack es un repositorio de terceros que compila desde GitHub: se limita
        // EXCLUSIVAMENTE al grupo de MPAndroidChart para que no pueda servir ningún otro artefacto.
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroup("com.github.PhilJay") }
        }
    }
}

rootProject.name = "ControlHerbal"
include(":app")
include(":wear")
include(":desktop")
include(":common")

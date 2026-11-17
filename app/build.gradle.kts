plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Dirección del servidor MuJoCo. 10.0.2.2 es la PC vista desde el emulador; para un celular:
// ./gradlew assembleDebug -Pgo2.mujocoAddress=192.168.0.20:8765
val mujocoAddress = providers.gradleProperty("go2.mujocoAddress").getOrElse("10.0.2.2:8765")

android {
    namespace = "ar.edu.uade.go2rondas"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ar.edu.uade.go2rondas"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "MUJOCO_SERVER_ADDRESS", "\"$mujocoAddress\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
}

// Los sitios de ejemplo (carpeta sitios/ del repositorio) van dentro del APK: son los mismos archivos que
// carga el servidor MuJoCo. Otro sitio se le carga al robot simulado desde el modo demo, sin recompilar.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(rootProject.layout.projectDirectory.dir("sitios").asFile.path)
    }
}

// Room escribe acá el esquema de cada versión de la base; se commitea para poder escribir migraciones.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

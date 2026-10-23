plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kronos.focus"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kronos.focus"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Solo teléfonos de 64 bits: el motor de IA local trae librerías nativas pesadas.
        ndk { abiFilters += "arm64-v8a" }
    }
    // Firma FIJA: todas las versiones llevan la misma firma, así cada APK nuevo se instala encima (conserva tus datos).
    signingConfigs {
        create("kronos") {
            storeFile = file("kronos-signing.jks")
            storePassword = "kronos-focus"
            keyAlias = "kronos"
            keyPassword = "kronos-focus"
        }
    }
    buildTypes {
        release {
            // Optimizada (R8 + sin código de depuración de Compose): mucho más fluida que la versión debug.
            // Se firma con la clave debug para poder instalarla directamente.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("kronos")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Fase 2: escaneo offline (modelo empaquetado) + cámara
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    val camerax = "1.4.0"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    // Verificación de evidencia con un modelo de visión local (LiteRT-LM)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
}

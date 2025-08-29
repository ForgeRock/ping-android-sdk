plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.authenticatorapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pingidentity.authenticatorapp"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    lint {
        disable += "NullSafeMutableLiveData"
        // To avoid lint errors during the build
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // Matching the Compose plugin version
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Ping SDK dependencies
    implementation(project(":mfa:oath"))
    implementation(project(":mfa:push"))
    implementation(project(":journey"))

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)

    // CameraX dependencies for QR scanning
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.barcode.scanning)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // HTTP client for reverse geocoding API calls
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Image loading
    implementation(libs.coil.compose)

    // Firebase Cloud Messaging for push notifications
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Maps for location display - using free OpenStreetMap
    implementation(libs.osmdroid.android)

    // Biometric
    implementation(libs.androidx.biometric)
}

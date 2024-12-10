plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

apply<AndroidBuildGradlePlugin>()

android {
    namespace = "com.pingidentity.device.integrity"

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
}
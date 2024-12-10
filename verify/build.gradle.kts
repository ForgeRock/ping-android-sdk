plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

apply<AndroidBuildGradlePlugin>()

android {
    namespace = "com.pingidentity.verify"

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
}

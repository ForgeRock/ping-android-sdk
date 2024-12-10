plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

apply<AndroidBuildGradlePlugin>()

android {
    namespace = "com.pingidentity.device.binding"

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":foundation:journey-plugin"))

    implementation(libs.androidx.biometric.ktx)
    implementation(libs.nimbus.jose.jwt)

}
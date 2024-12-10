plugins {
    alias(libs.plugins.androidLibrary)
}

apply<AndroidBuildGradlePlugin>()

android {
    namespace = "com.pingidentity.device"
}

dependencies {
    api(project(":foundation:device:device-id"))
    api(project(":foundation:device:device-binding"))
}
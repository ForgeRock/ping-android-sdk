package com.pingidentity.device.profile.detector

fun DefaultRootDetector(): MutableList<RootDetector<*>>.() -> Unit = {
    add(BuildTagsDetector())
    add(BusyBoxProgramFileDetector())
    add(DangerousPropertyDetector())
    add(NativeDetector())
    add(PermissionDetector())
    add(RootApkDetector())
    add(RootAppDetector())
    add(RootRequiredAppDetector())
    add(RootCloakingAppDetector())
    add(RootProgramFileDetector())
    add(SuCommandDetector())
}
package com.pingidentity.device.id

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest

const val ANDROID_KEYSTORE = "AndroidKeyStore"
const val keyAlias = "com.pingidentity.device.id"

@ExperimentalStdlibApi
object DefaultDeviceIdentifier : DeviceIdentifier {

    private var id: String

    init {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(keyAlias)) {
            val generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE)
            val specBuilder: KeyGenParameterSpec.Builder = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
            generator.initialize(specBuilder.build())
            id = MessageDigest.getInstance("SHA256")
                .digest(generator.generateKeyPair().public.encoded).toHexString()
        } else {
            id = MessageDigest.getInstance("SHA256")
                .digest(keyStore.getCertificate(keyAlias).publicKey.encoded).toHexString()
        }
    }

    override fun id() = id

    override fun name(): String {
        return Build.MODEL
    }
}
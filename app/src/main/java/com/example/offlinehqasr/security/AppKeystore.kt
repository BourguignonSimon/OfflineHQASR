package com.example.offlinehqasr.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object AppKeystore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    const val AUDIO_KEY_ALIAS = "offlinehqasr_audio_aes"
    const val EXPORT_KEY_ALIAS = "offlinehqasr_export_aes"
    const val MASTER_KEY_ALIAS = "offlinehqasr_master_aes"

    @Synchronized
    fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null)
        if (existing is SecretKey) {
            return existing
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specBuilder.setIsStrongBoxBacked(false)
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }
}

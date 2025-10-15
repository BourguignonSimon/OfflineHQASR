package com.example.offlinehqasr.security

import android.content.Context
import java.io.File
import java.security.SecureRandom

object DatabaseKeyProvider {

    fun obtainPassphrase(context: Context): ByteArray {
        val dir = File(context.filesDir, "security")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val keyFile = File(dir, "db.pass")
        val metadataFile = EncryptionMetadataStore.metadataFileFor(keyFile)
        val existingMetadata = EncryptionMetadataStore.read(metadataFile)
        if (keyFile.exists() && existingMetadata != null) {
            val encrypted = keyFile.readBytes()
            return AesGcmCipher.decrypt(existingMetadata, encrypted)
        }

        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val payload = AesGcmCipher.encrypt(AppKeystore.MASTER_KEY_ALIAS, random)
        keyFile.writeBytes(payload.ciphertext)
        EncryptionMetadataStore.write(metadataFile, payload.metadata)
        return random
    }
}

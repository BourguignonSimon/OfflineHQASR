package com.example.offlinehqasr.security

import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec

object AesGcmCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun wrapForEncryption(
        alias: String,
        output: OutputStream,
        aad: ByteArray? = null
    ): EncryptingStream {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, AppKeystore.getOrCreateKey(alias))
        aad?.let { cipher.updateAAD(it) }
        val metadata = EncryptionMetadata(
            alias = alias,
            iv = cipher.iv,
            tagLengthBits = 128,
            aad = aad
        )
        return EncryptingStream(CipherOutputStream(output, cipher), metadata)
    }

    fun wrapForDecryption(
        metadata: EncryptionMetadata,
        input: InputStream
    ): CipherInputStream {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(metadata.tagLengthBits, metadata.iv)
        cipher.init(Cipher.DECRYPT_MODE, AppKeystore.getOrCreateKey(metadata.alias), spec)
        metadata.aad?.let { cipher.updateAAD(it) }
        return CipherInputStream(input, cipher)
    }

    fun encrypt(
        alias: String,
        plaintext: ByteArray,
        aad: ByteArray? = null
    ): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, AppKeystore.getOrCreateKey(alias))
        aad?.let { cipher.updateAAD(it) }
        val ciphertext = cipher.doFinal(plaintext)
        val metadata = EncryptionMetadata(
            alias = alias,
            iv = cipher.iv,
            tagLengthBits = 128,
            aad = aad
        )
        return EncryptedPayload(ciphertext, metadata)
    }

    fun decrypt(metadata: EncryptionMetadata, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(metadata.tagLengthBits, metadata.iv)
        cipher.init(Cipher.DECRYPT_MODE, AppKeystore.getOrCreateKey(metadata.alias), spec)
        metadata.aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(ciphertext)
    }
}

data class EncryptingStream(
    val stream: CipherOutputStream,
    val metadata: EncryptionMetadata
)

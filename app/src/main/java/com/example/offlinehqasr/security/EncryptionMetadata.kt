package com.example.offlinehqasr.security

data class EncryptionMetadata(
    val alias: String,
    val iv: ByteArray,
    val tagLengthBits: Int = 128,
    val aad: ByteArray? = null,
    val version: Int = 1
)

data class EncryptedPayload(
    val ciphertext: ByteArray,
    val metadata: EncryptionMetadata
)

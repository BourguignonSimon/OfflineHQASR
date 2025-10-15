package com.example.offlinehqasr.security

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object SecureFileUtils {
    private const val WAV_HEADER_SIZE = 44L

    fun readMetadata(file: File): EncryptionMetadata? {
        val metadataFile = EncryptionMetadataStore.metadataFileFor(file)
        return EncryptionMetadataStore.read(metadataFile)
    }

    fun persistMetadata(file: File, metadata: EncryptionMetadata) {
        val metadataFile = EncryptionMetadataStore.metadataFileFor(file)
        EncryptionMetadataStore.write(metadataFile, metadata)
    }

    fun clearMetadata(file: File) {
        val metadataFile = EncryptionMetadataStore.metadataFileFor(file)
        if (metadataFile.exists()) {
            metadataFile.delete()
        }
    }

    fun decryptWavToTemp(context: Context, source: File): File {
        val metadata = readMetadata(source) ?: return source
        val temp = File.createTempFile("dec_", ".wav", context.cacheDir)
        FileInputStream(source).use { input ->
            FileOutputStream(temp).use { output ->
                val header = ByteArray(WAV_HEADER_SIZE.toInt())
                val read = input.read(header)
                if (read > 0) {
                    output.write(header, 0, read)
                }
                val cipherStream = AesGcmCipher.wrapForDecryption(metadata, input)
                cipherStream.use { decrypted ->
                    decrypted.copyTo(output)
                }
            }
        }
        return temp
    }
}

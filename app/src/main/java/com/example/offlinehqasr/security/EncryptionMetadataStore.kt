package com.example.offlinehqasr.security

import android.util.Base64
import org.json.JSONObject
import java.io.File

object EncryptionMetadataStore {

    fun metadataFileFor(dataFile: File): File {
        val parent = dataFile.parentFile ?: throw IllegalStateException("File has no parent: ${dataFile.absolutePath}")
        return File(parent, dataFile.name + ".meta")
    }

    fun write(file: File, metadata: EncryptionMetadata) {
        val parent = file.parentFile
        parent?.mkdirs()
        val json = JSONObject()
        json.put("version", metadata.version)
        json.put("alias", metadata.alias)
        json.put("iv", Base64.encodeToString(metadata.iv, Base64.NO_WRAP))
        json.put("tagBits", metadata.tagLengthBits)
        metadata.aad?.let {
            json.put("aad", Base64.encodeToString(it, Base64.NO_WRAP))
        }
        file.writeText(json.toString(), Charsets.UTF_8)
    }

    fun read(file: File): EncryptionMetadata? {
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val alias = json.optString("alias")
            if (alias.isBlank()) return null
            val iv = Base64.decode(json.optString("iv"), Base64.NO_WRAP)
            val tagBits = json.optInt("tagBits", 128)
            val aadEncoded = json.optString("aad", null)
            val aad = if (!aadEncoded.isNullOrBlank()) Base64.decode(aadEncoded, Base64.NO_WRAP) else null
            EncryptionMetadata(
                alias = alias,
                iv = iv,
                tagLengthBits = tagBits,
                aad = aad,
                version = json.optInt("version", 1)
            )
        } catch (e: Exception) {
            null
        }
    }
}

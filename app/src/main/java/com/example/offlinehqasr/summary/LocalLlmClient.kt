package com.example.offlinehqasr.summary

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class LocalLlmClient private constructor(
    private val modelRoot: File,
    private val chatExecutable: File?
) {
    fun generateStructuredSummary(prompt: String, durationMs: Long): String? {
        Log.d(TAG, "Génération LLM demandée (durée=${durationMs}ms)")
        val stubFile = File(modelRoot, "structured_summary.json")
        if (stubFile.exists()) {
            return runCatching { stubFile.readText() }
                .onFailure { Log.w(TAG, "Lecture du stub LLM impossible", it) }
                .getOrNull()
        }

        val binary = chatExecutable ?: return null
        if (!binary.canExecute()) {
            Log.w(TAG, "Binaire MLC non exécutable: ${binary.absolutePath}")
            return null
        }

        return runCatching {
            val process = ProcessBuilder(binary.absolutePath)
                .directory(modelRoot)
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter().use { writer ->
                writer.appendLine("PROMPT_START")
                writer.appendLine(prompt)
                writer.appendLine("PROMPT_END")
            }
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.appendLine(line)
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "mlc_chat_cli a retourné le code $exitCode")
                return null
            }
            output.toString().trim().ifEmpty { null }
        }.onFailure {
            Log.w(TAG, "Échec exécution mlc_chat_cli", it)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "LocalLlmClient"

        fun create(context: Context): LocalLlmClient? {
            val root = File(context.filesDir, "models/mlc")
            if (!root.exists()) {
                Log.i(TAG, "Répertoire MLC introuvable: ${root.absolutePath}")
                return null
            }
            val chat = File(root, "mlc_chat_cli")
            return LocalLlmClient(root, chat.takeIf { it.exists() })
        }
    }
}

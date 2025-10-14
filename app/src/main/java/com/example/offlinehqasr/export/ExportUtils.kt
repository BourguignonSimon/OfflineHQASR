package com.example.offlinehqasr.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.offlinehqasr.data.AppDb
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

object ExportUtils {

    fun toMarkdown(ctx: Context, recId: Long): String {
        val db = AppDb.get(ctx)
        val rec = db.recordingDao().getById(recId) ?: return ""
        val tr = db.transcriptDao().getByRecording(recId)
        val sum = db.summaryDao().getByRecording(recId)?.json?.let { JSONObject(it) }
        val segs = db.segmentDao().getByRecording(recId)

        val sb = StringBuilder("# Session: ").append(File(rec.filePath).name.escapeMarkdown()).append('\n\n')
        if (sum != null) {
            sb.append("## Résumé\n")
            sb.append("- Titre: ").append(sum.optString("title").escapeMarkdown()).append('\n')
            val summary = sum.optJSONObject("summary")
            if (summary != null) {
                sb.append("- Contexte: ").append(summary.optString("context").escapeMarkdown()).append('\n')
                val bullets = summary.optJSONArray("bullets")
                if (bullets != null) {
                    sb.append("- Points clés:\n")
                    for (i in 0 until bullets.length()) {
                        sb.append("  - ").append(bullets.getString(i).escapeMarkdown()).append('\n')
                    }
                }
            }
            sb.append("\n")
        }
        sb.append("## Transcript\n")
        sb.append((tr?.text ?: "(vide)").escapeMarkdown()).append("\n\n")
        sb.append("## Segments\n")
        for (s in segs) {
            sb.append("- [").append(s.startMs).append("–").append(s.endMs).append("] ").append(s.text.escapeMarkdown()).append('\n')
        }
        return sb.toString()
    }

    fun exportAllToMarkdown(ctx: Context): String {
        val outDir = File(ctx.filesDir, "exports"); outDir.mkdirs()
        val db = AppDb.get(ctx)
        val recs = db.recordingDao().getAll()
        val zipFile = File(outDir, "export_markdown_${System.currentTimeMillis()}.zip")
        val zos = java.util.zip.ZipOutputStream(FileOutputStream(zipFile))
        try {
            for (r in recs) {
                val md = toMarkdown(ctx, r.id)
                val entry = java.util.zip.ZipEntry(File(r.filePath).nameWithoutExtension + ".md")
                zos.putNextEntry(entry)
                zos.write(md.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        } finally {
            zos.close()
        }
        return zipFile.absolutePath
    }

    fun exportOneToJson(ctx: Context, recId: Long): String {
        val outDir = File(ctx.filesDir, "exports"); outDir.mkdirs()
        val db = AppDb.get(ctx)
        val rec = db.recordingDao().getById(recId) ?: error("Recording missing")
        val tr = db.transcriptDao().getByRecording(recId)
        val sum = db.summaryDao().getByRecording(recId)?.json ?: "{}"
        val segs = db.segmentDao().getByRecording(recId)

        val exportJson = buildSessionJson(
            rec,
            tr,
            JSONObject(sum),
            segs,
            Build.MODEL ?: "unknown"
        )

        val out = File(outDir, File(rec.filePath).nameWithoutExtension + ".json")
        out.writeText(exportJson.toString(2), Charsets.UTF_8)
        return out.absolutePath
    }

    internal fun buildSessionJson(
        rec: com.example.offlinehqasr.data.entities.Recording,
        transcript: com.example.offlinehqasr.data.entities.Transcript?,
        summaryJson: JSONObject,
        segments: List<com.example.offlinehqasr.data.entities.Segment>,
        deviceName: String
    ): JSONObject {
        val root = JSONObject()
        root.put("id", rec.id.toString())
        root.put("device", deviceName)

        val audio = JSONObject()
        audio.put("path", rec.filePath)
        audio.put("duration_s", rec.durationMs / 1000.0)
        root.put("audio", audio)

        val sttObj = summaryJson.optJSONObject("stt") ?: JSONObject()
        if (!sttObj.has("engine")) sttObj.put("engine", "vosk")
        if (!sttObj.has("language")) sttObj.put("language", "auto")
        root.put("stt", sttObj)

        root.put("transcript", transcript?.text ?: "")

        val segArr = JSONArray()
        segments.forEach { segment ->
            segArr.put(
                JSONObject()
                    .put("t0", segment.startMs / 1000.0)
                    .put("t1", segment.endMs / 1000.0)
                    .put("text", segment.text)
            )
        }
        root.put("segments", segArr)

        val summaryNode = summaryJson.optJSONObject("summary") ?: JSONObject()
        val bullets = summaryNode.optJSONArray("bullets") ?: JSONArray()
        val summaryWrapper = JSONObject()
        summaryWrapper.put("bullets", bullets)
        summaryWrapper.put("keywords", summaryJson.optJSONArray("keywords") ?: JSONArray())
        root.put("summary", summaryWrapper)

        return root
    }

    fun importVoskModel(ctx: Context, uri: Uri): String {
        val cr: ContentResolver = ctx.contentResolver
        val models = File(ctx.filesDir, "models"); models.mkdirs()
        cr.openInputStream(uri)?.use { ins ->
            return importVoskZip(models, ins)
        }
        error("Import failed")
    }

    fun importWhisperModel(ctx: Context, uri: Uri): String {
        val cr: ContentResolver = ctx.contentResolver
        val models = File(ctx.filesDir, "models"); models.mkdirs()
        val name = queryName(cr, uri) ?: "whisper_${System.currentTimeMillis()}"
        cr.openInputStream(uri)?.use { ins ->
            return importWhisperModel(models, ins, name)
        }
        error("Import failed")
    }

    private fun queryName(cr: ContentResolver, uri: Uri): String? {
        val c = cr.query(uri, null, null, null, null) ?: return null
        c.use {
            val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIdx >= 0) return it.getString(nameIdx)
        }
        return null
    }

    private fun importVoskZip(modelsDir: File, input: InputStream): String {
        val tempDir = File(modelsDir, "vosk_import_${System.currentTimeMillis()}")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()
        val canonicalTemp = tempDir.canonicalFile
        return try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.name.startsWith("__MACOSX")) {
                        val outFile = File(tempDir, entry.name).canonicalFile
                        if (!outFile.path.startsWith(canonicalTemp.path)) {
                            throw SecurityException("Entrée ZIP invalide: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { os -> zis.copyTo(os) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val resolved = resolveVoskModelRoot(tempDir)
            val target = File(modelsDir, "vosk")
            if (target.exists()) target.deleteRecursively()
            resolved.copyRecursively(target, overwrite = true)
            target.name
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun resolveVoskModelRoot(dir: File): File {
        if (looksLikeVoskModel(dir)) return dir
        val children = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        children.forEach { child ->
            if (looksLikeVoskModel(child)) return child
        }
        children.forEach { child ->
            val candidate = resolveVoskModelRoot(child)
            if (looksLikeVoskModel(candidate)) return candidate
        }
        return dir
    }

    private fun looksLikeVoskModel(dir: File): Boolean {
        return File(dir, "conf").exists() || File(dir, "model.conf").exists() || File(dir, "graph").exists()
    }

    private fun importWhisperModel(modelsDir: File, input: InputStream, originalName: String): String {
        val whisperDir = File(modelsDir, "whisper"); whisperDir.mkdirs()
        val sanitized = originalName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "model_${System.currentTimeMillis()}" }
        val normalized = if (sanitized.startsWith("user_")) sanitized else "user_$sanitized"
        val baseName = normalized.substringBeforeLast('.')
        val extension = normalized.substringAfterLast('.', "")
        var candidate = if (extension.isEmpty()) File(whisperDir, baseName) else File(whisperDir, "$baseName.$extension")
        if (candidate.exists()) {
            val suffix = System.currentTimeMillis()
            candidate = if (extension.isEmpty()) {
                File(whisperDir, "${baseName}_$suffix")
            } else {
                File(whisperDir, "${baseName}_$suffix.$extension")
            }
        }
        candidate.outputStream().use { os -> input.copyTo(os) }
        return candidate.name
    }

    private fun String.escapeMarkdown(): String {
        return this
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)")
    }
}

package com.example.offlinehqasr.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile
import com.example.offlinehqasr.data.AppDb
import okio.Buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

object ExportUtils {

    fun toMarkdown(ctx: Context, recId: Long): String {
        val db = AppDb.get(ctx)
        val rec = db.recordingDao().getById(recId) ?: return ""
        val tr = db.transcriptDao().getByRecording(recId)
        val sum = db.summaryDao().getByRecording(recId)?.json?.let { JSONObject(it) }
        val segs = db.segmentDao().getByRecording(recId)

        val sb = StringBuilder("# Session: ").append(File(rec.filePath).name).append('\n\n')
        if (sum != null) {
            sb.append("## Résumé\n")
            sb.append("- Titre: ").append(sum.optString("title")).append('\n')
            val summary = sum.optJSONObject("summary")
            if (summary != null) {
                sb.append("- Contexte: ").append(summary.optString("context")).append('\n')
                val bullets = summary.optJSONArray("bullets")
                if (bullets != null) {
                    sb.append("- Points clés:\n")
                    for (i in 0 until bullets.length()) {
                        sb.append("  - ").append(bullets.getString(i)).append('\n')
                    }
                }
            }
            sb.append("\n")
        }
        sb.append("## Transcript\n")
        sb.append(tr?.text ?: "(vide)").append("\n\n")
        sb.append("## Segments\n")
        for (s in segs) {
            sb.append("- [").append(s.startMs).append("–").append(s.endMs).append("] ").append(s.text).append('\n')
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

        val root = JSONObject()
        root.put("file", rec.filePath)
        root.put("createdAt", rec.createdAt)
        root.put("durationMs", rec.durationMs)
        root.put("transcript", tr?.text ?: "")
        root.put("summary", JSONObject(sum))
        val segArr = org.json.JSONArray()
        for (s in segs) {
            val o = JSONObject()
            o.put("startMs", s.startMs)
            o.put("endMs", s.endMs)
            o.put("text", s.text)
            segArr.put(o)
        }
        root.put("segments", segArr)

        val out = File(outDir, File(rec.filePath).nameWithoutExtension + ".json")
        out.writeText(root.toString(2), Charsets.UTF_8)
        return out.absolutePath
    }

    fun copyAndMaybeUnzip(ctx: Context, uri: Uri): String {
        val cr: ContentResolver = ctx.contentResolver
        val name = queryName(cr, uri) ?: "import_${System.currentTimeMillis()}"
        val models = File(ctx.filesDir, "models"); models.mkdirs()
        val target = File(models, name)
        cr.openInputStream(uri)?.use { ins ->
            if (name.endsWith(".zip")) {
                // Decide target folder by heuristic (vosk model zip usually contains model folder)
                val voskDir = File(models, "vosk"); voskDir.mkdirs()
                ZipInputStream(ins).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(voskDir, entry.name)
                        if (entry.isDirectory) outFile.mkdirs() else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { os -> zis.copyTo(os) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                return "vosk"
            } else {
                val whisperDir = File(models, "whisper"); whisperDir.mkdirs()
                val out = File(whisperDir, name)
                out.outputStream().use { os -> ins.copyTo(os) }
                return name
            }
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
}

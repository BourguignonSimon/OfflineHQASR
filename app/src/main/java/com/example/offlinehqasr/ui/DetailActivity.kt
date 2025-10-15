package com.example.offlinehqasr.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.databinding.ActivityDetailBinding
import com.example.offlinehqasr.export.ExportUtils
import com.example.offlinehqasr.summary.StructuredSummaryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DetailActivity : AppCompatActivity(), DetailHeaderAdapter.Callbacks {

    private lateinit var binding: ActivityDetailBinding
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var headerAdapter: DetailHeaderAdapter
    private var segmentAdapter: SegmentAdapter = SegmentAdapter(emptyList()) { jumpTo(it.startMs) }
    private var recordingPath: String? = null
    private var recordingId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordingId = intent.getLongExtra("recordingId", -1)
        if (recordingId == -1L) { finish(); return }

        headerAdapter = DetailHeaderAdapter(this)
        binding.detailList.layoutManager = LinearLayoutManager(this)
        binding.detailList.adapter = ConcatAdapter(headerAdapter, segmentAdapter)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDb.get(this@DetailActivity)
            val rec = db.recordingDao().getById(recordingId) ?: return@launch
            val segments = db.segmentDao().getByRecording(recordingId)
            val summary = db.summaryDao().getByRecording(recordingId)
            recordingPath = rec.filePath
            val headerContent = DetailHeaderAdapter.HeaderContent(
                title = File(rec.filePath).name,
                meta = buildMeta(rec.durationMs, rec.createdAt),
                summary = summary?.let { parseSummary(it.json) }
            )
            launch(Dispatchers.Main) {
                headerAdapter.submit(headerContent)
                segmentAdapter = SegmentAdapter(segments) { seg ->
                    jumpTo(seg.startMs)
                }
                binding.detailList.adapter = ConcatAdapter(headerAdapter, segmentAdapter)
            }
        }
    }

    private fun play(path: String) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
            }
        }
        mediaPlayer?.start()
    }

    private fun pause() { mediaPlayer?.pause() }

    private fun jumpTo(ms: Long) {
        if (mediaPlayer == null) {
            recordingPath?.let { play(it) }
        }
        mediaPlayer?.let {
            it.seekTo(ms.toInt())
            if (!it.isPlaying) it.start()
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    override fun onPlay() {
        recordingPath?.let { play(it) }
    }

    override fun onPause() {
        pause()
    }

    override fun onExport() {
        val id = recordingId
        if (id <= 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            val path = ExportUtils.exportOneToJson(this@DetailActivity, id)
            launch(Dispatchers.Main) {
                Toast.makeText(
                    this@DetailActivity,
                    getString(R.string.export_json_success, path),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun buildMeta(durationMs: Long, createdAt: Long): String {
        val duration = formatDuration(durationMs)
        val date = formatDate(createdAt)
        return getString(R.string.detail_header_duration, duration) + " • " +
            getString(R.string.detail_header_created, date)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun parseSummary(json: String): DetailHeaderAdapter.SummaryPreview? {
        val structured = StructuredSummaryParser.parseOrNull(json) ?: return null
        return DetailHeaderAdapter.SummaryPreview(
            context = structured.summary.context,
            bullets = structured.summary.bullets,
            topics = structured.topics,
            keywords = structured.keywords,
            actions = structured.actions.map { action ->
                val extras = listOfNotNull(
                    action.due.takeIf { it.isNotBlank() }?.let { "échéance: $it" },
                    action.priority.takeIf { it.isNotBlank() }?.let { "priorité: $it" },
                    action.status.takeIf { it.isNotBlank() }?.let { "statut: $it" }
                )
                buildString {
                    append(action.who.ifBlank { "—" })
                    append(" · ")
                    append(action.what.ifBlank { "Action à préciser" })
                    if (extras.isNotEmpty()) {
                        append(" (" + extras.joinToString(", ") + ")")
                    }
                }
            },
            decisions = structured.decisions.map { decision ->
                val formattedTime = if (decision.timestampMs > 0) {
                    formatTimestamp(decision.timestampMs)
                } else null
                listOfNotNull(decision.description, decision.owner.takeIf { it.isNotBlank() }, formattedTime)
                    .joinToString(" • ")
            },
            participants = structured.participants.map { participant ->
                if (participant.role.isBlank()) participant.name else "${participant.name} (${participant.role})"
            },
            tags = structured.tags,
            sentiments = structured.sentiments.map { sentiment ->
                buildString {
                    append(sentiment.target.ifBlank { "Général" })
                    append(": ")
                    append(sentiment.value.ifBlank { "neutre" })
                    append(" (${String.format(Locale.getDefault(), "%.0f%%", sentiment.score * 100)})")
                }
            },
            citations = structured.citations.map { citation ->
                val window = formatRange(citation.startMs, citation.endMs)
                "\"${citation.quote}\" — ${citation.speaker.ifBlank { "Intervenant" }} [$window]"
            },
            timings = structured.timings.map { timing ->
                val window = formatRange(timing.startMs, timing.endMs)
                "${timing.label}: $window"
            }
        )
    }

    private fun formatTimestamp(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun formatRange(start: Long, end: Long): String {
        val startStr = formatTimestamp(start)
        val endStr = formatTimestamp(end)
        return "$startStr → $endStr"
    }
}

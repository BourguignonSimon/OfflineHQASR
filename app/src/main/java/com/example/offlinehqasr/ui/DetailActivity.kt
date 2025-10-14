package com.example.offlinehqasr.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.data.entities.Segment
import com.example.offlinehqasr.databinding.ActivityDetailBinding
import com.example.offlinehqasr.export.ExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recordingId = intent.getLongExtra("recordingId", -1)
        if (recordingId == -1L) { finish(); return }

        binding.segmentsList.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDb.get(this@DetailActivity)
            val rec = db.recordingDao().getById(recordingId) ?: return@launch
            val segments = db.segmentDao().getByRecording(recordingId)
            launch(Dispatchers.Main) {
                binding.titleText.text = File(rec.filePath).name
                binding.segmentsList.adapter = SegmentAdapter(segments) { seg ->
                    jumpTo(seg.startMs)
                }

                binding.playBtn.setOnClickListener {
                    play(rec.filePath)
                }
                binding.pauseBtn.setOnClickListener { pause() }
                binding.exportJsonBtn.setOnClickListener {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val path = ExportUtils.exportOneToJson(this@DetailActivity, recordingId)
                        launch(Dispatchers.Main) {
                            Toast.makeText(this@DetailActivity, "JSON: $path", Toast.LENGTH_LONG).show()
                        }
                    }
                }
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
}

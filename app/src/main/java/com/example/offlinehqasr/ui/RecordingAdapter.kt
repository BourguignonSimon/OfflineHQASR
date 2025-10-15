package com.example.offlinehqasr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.entities.Recording
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter(private val onClick: (Recording) -> Unit) : RecyclerView.Adapter<RecordingAdapter.VH>() {

    private val items = mutableListOf<RecordingListItem>()

    fun submit(newItems: List<RecordingListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.recordingTitle)
        val meta: TextView = v.findViewById(R.id.recordingMeta)
        val snippet: TextView = v.findViewById(R.id.recordingSnippet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val recording = item.recording
        holder.title.text = recording.filePath.substringAfterLast('/')
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val meta = sdf.format(Date(recording.createdAt)) + " • " + formatDuration(recording.durationMs)
        holder.meta.text = meta
        val snippetText = item.snippet?.takeIf { it.isNotBlank() }
        if (snippetText != null) {
            holder.snippet.visibility = View.VISIBLE
            holder.snippet.text = snippetText
        } else {
            holder.snippet.visibility = View.GONE
            holder.snippet.text = ""
        }
        holder.itemView.setOnClickListener { onClick(recording) }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

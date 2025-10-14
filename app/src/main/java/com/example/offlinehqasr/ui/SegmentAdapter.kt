package com.example.offlinehqasr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.entities.Segment
import java.util.Locale

class SegmentAdapter(private val items: List<Segment>, private val onClick: (Segment) -> Unit)
    : RecyclerView.Adapter<SegmentAdapter.VH>() {

    class VH(v: View): RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.segmentText)
        val time: TextView = v.findViewById(R.id.segmentTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_segment, parent, false)
        return VH(v)
    }
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.text.text = s.text
        holder.time.text = holder.itemView.context.getString(
            R.string.segment_time_range,
            formatTime(s.startMs),
            formatTime(s.endMs)
        )
        holder.itemView.setOnClickListener { onClick(s) }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

package com.example.offlinehqasr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.entities.Recording
import java.text.SimpleDateFormat
import java.util.*

class RecordingAdapter(private val items: List<Recording>, private val onClick: (Recording) -> Unit)
    : RecyclerView.Adapter<RecordingAdapter.VH>() {

    class VH(v: View): RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(android.R.id.text1)
        val sub: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.title.text = it.filePath.substringAfterLast('/')
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.sub.text = sdf.format(Date(it.createdAt)) + " • " + (it.durationMs/1000) + "s"
        holder.itemView.setOnClickListener { onClick(it) }
    }
}

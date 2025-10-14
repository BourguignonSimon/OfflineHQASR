package com.example.offlinehqasr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.entities.Segment

class SegmentAdapter(private val items: List<Segment>, private val onClick: (Segment) -> Unit)
    : RecyclerView.Adapter<SegmentAdapter.VH>() {

    class VH(v: View): RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(android.R.id.text1)
        val time: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.text.text = s.text
        holder.time.text = "${s.startMs}ms → ${s.endMs}ms"
        holder.itemView.setOnClickListener { onClick(s) }
    }
}

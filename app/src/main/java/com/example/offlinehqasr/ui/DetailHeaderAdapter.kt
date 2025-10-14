package com.example.offlinehqasr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinehqasr.R

class DetailHeaderAdapter(
    private val callbacks: Callbacks
) : RecyclerView.Adapter<DetailHeaderAdapter.VH>() {

    private var content: HeaderContent? = null

    interface Callbacks {
        fun onPlay()
        fun onPause()
        fun onExport()
    }

    fun submit(content: HeaderContent) {
        this.content = content
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail_header, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = if (content == null) 0 else 1

    override fun onBindViewHolder(holder: VH, position: Int) {
        val data = content ?: return
        holder.title.text = data.title
        holder.meta.text = data.meta

        holder.playButton.setOnClickListener { callbacks.onPlay() }
        holder.pauseButton.setOnClickListener { callbacks.onPause() }
        holder.exportButton.setOnClickListener { callbacks.onExport() }

        if (data.summary == null) {
            holder.summaryStatus.visibility = View.VISIBLE
            holder.summaryContent.visibility = View.GONE
        } else {
            holder.summaryStatus.visibility = View.GONE
            holder.summaryContent.visibility = View.VISIBLE
            holder.summaryContext.text = data.summary.context.ifBlank { "—" }
            holder.summaryBullets.text = if (data.summary.bullets.isEmpty()) {
                "—"
            } else {
                data.summary.bullets.joinToString(separator = "\n") { "• $it" }
            }
            holder.summaryTopics.text = data.summary.topics.takeIf { it.isNotEmpty() }?.joinToString(
                separator = ", "
            ) ?: "—"
            holder.summaryKeywords.text = data.summary.keywords.takeIf { it.isNotEmpty() }?.joinToString(
                separator = ", "
            ) ?: "—"
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.headerTitle)
        val meta: TextView = view.findViewById(R.id.headerMeta)
        val playButton: View = view.findViewById(R.id.playButton)
        val pauseButton: View = view.findViewById(R.id.pauseButton)
        val exportButton: View = view.findViewById(R.id.exportButton)
        val summaryStatus: TextView = view.findViewById(R.id.summaryStatus)
        val summaryContent: LinearLayout = view.findViewById(R.id.summaryContent)
        val summaryContext: TextView = view.findViewById(R.id.summaryContext)
        val summaryBullets: TextView = view.findViewById(R.id.summaryBullets)
        val summaryTopics: TextView = view.findViewById(R.id.summaryTopics)
        val summaryKeywords: TextView = view.findViewById(R.id.summaryKeywords)
    }

    data class HeaderContent(
        val title: String,
        val meta: String,
        val summary: SummaryPreview?
    )

    data class SummaryPreview(
        val context: String,
        val bullets: List<String>,
        val topics: List<String>,
        val keywords: List<String>
    )
}

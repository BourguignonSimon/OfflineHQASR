package com.example.offlinehqasr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            val resources = holder.summaryContext.resources
            val placeholder = resources.getString(R.string.summary_none_placeholder)
            holder.summaryContext.text = data.summary.context.ifBlank { placeholder }
            holder.summaryBullets.text = data.summary.bullets
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n") { "• $it" }
                ?: placeholder
            holder.summaryActions.text = data.summary.actions
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n") { "• $it" }
                ?: placeholder
            holder.summaryDecisions.text = data.summary.decisions
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n") { "• $it" }
                ?: placeholder
            holder.summaryTopics.text = data.summary.topics
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = ", ")
                ?: placeholder
            holder.summaryKeywords.text = data.summary.keywords
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = ", ")
                ?: placeholder
            holder.summaryParticipants.text = data.summary.participants
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n") { "• $it" }
                ?: placeholder
            holder.summaryTags.text = data.summary.tags
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = ", ")
                ?: placeholder
            holder.summarySentiments.text = data.summary.sentiments
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n") { "• $it" }
                ?: placeholder
            holder.summaryCitations.text = data.summary.citations
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n") { "• $it" }
                ?: placeholder
            holder.summaryTimings.text = data.summary.timings
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n") { "• $it" }
                ?: placeholder
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.headerTitle)
        val meta: TextView = view.findViewById(R.id.headerMeta)
        val playButton: View = view.findViewById(R.id.playButton)
        val pauseButton: View = view.findViewById(R.id.pauseButton)
        val exportButton: View = view.findViewById(R.id.exportButton)
        val summaryStatus: TextView = view.findViewById(R.id.summaryStatus)
        val summaryContent: View = view.findViewById(R.id.summaryContent)
        val summaryContext: TextView = view.findViewById(R.id.summaryContext)
        val summaryBullets: TextView = view.findViewById(R.id.summaryBullets)
        val summaryActions: TextView = view.findViewById(R.id.summaryActions)
        val summaryDecisions: TextView = view.findViewById(R.id.summaryDecisions)
        val summaryTopics: TextView = view.findViewById(R.id.summaryTopics)
        val summaryKeywords: TextView = view.findViewById(R.id.summaryKeywords)
        val summaryParticipants: TextView = view.findViewById(R.id.summaryParticipants)
        val summaryTags: TextView = view.findViewById(R.id.summaryTags)
        val summarySentiments: TextView = view.findViewById(R.id.summarySentiments)
        val summaryCitations: TextView = view.findViewById(R.id.summaryCitations)
        val summaryTimings: TextView = view.findViewById(R.id.summaryTimings)
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
        val keywords: List<String>,
        val actions: List<String>,
        val decisions: List<String>,
        val participants: List<String>,
        val tags: List<String>,
        val sentiments: List<String>,
        val citations: List<String>,
        val timings: List<String>
    )
}

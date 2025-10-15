package com.example.offlinehqasr.summary

data class StructuredSummary(
    val title: String,
    val summary: SummarySection,
    val actions: List<Action>,
    val decisions: List<Decision>,
    val citations: List<Citation>,
    val sentiments: List<Sentiment>,
    val participants: List<Participant>,
    val tags: List<String>,
    val keywords: List<String>,
    val topics: List<String>,
    val timings: List<Timing>,
    val durationMs: Long
) {
    data class SummarySection(
        val context: String,
        val bullets: List<String>
    )

    data class Action(
        val who: String,
        val what: String,
        val due: String,
        val priority: String,
        val status: String,
        val confidence: Double,
        val relatedSegments: List<Segment>
    )

    data class Decision(
        val description: String,
        val owner: String,
        val timestampMs: Long,
        val confidence: Double
    )

    data class Citation(
        val quote: String,
        val speaker: String,
        val startMs: Long,
        val endMs: Long
    )

    data class Sentiment(
        val target: String,
        val value: String,
        val score: Double
    )

    data class Participant(
        val name: String,
        val role: String
    )

    data class Timing(
        val label: String,
        val startMs: Long,
        val endMs: Long
    )

    data class Segment(
        val startMs: Long,
        val endMs: Long
    )
}

package com.example.offlinehqasr.summary

import android.content.Context
import android.util.Log
import com.example.offlinehqasr.data.entities.Summary
import org.json.JSONObject

object PremiumPrompt {
    val JSON_STRUCT_PROMPT = """
RÔLE: Tu es un secrétaire de réunion expert spécialisé dans le suivi de réunions professionnelles.
OBJECTIF: À partir de la transcription suivante, renvoie EXCLUSIVEMENT un JSON valide respectant exactement ce schéma :
{
  "title": string,
  "summary": {
    "context": string,
    "bullets": string[]
  },
  "actions": [{
    "who": string,
    "what": string,
    "due": string,
    "priority": string,
    "status": string,
    "confidence": number,
    "relatedSegments": [{"startMs": number, "endMs": number}]
  }],
  "decisions": [{
    "description": string,
    "owner": string,
    "timestampMs": number,
    "confidence": number
  }],
  "citations": [{
    "quote": string,
    "speaker": string,
    "startMs": number,
    "endMs": number
  }],
  "sentiments": [{
    "target": string,
    "value": string,
    "score": number
  }],
  "participants": [{
    "name": string,
    "role": string
  }],
  "tags": string[],
  "keywords": string[],
  "topics": string[],
  "timings": [{
    "label": string,
    "startMs": number,
    "endMs": number
  }],
  "durationMs": number
}
CONTRAINTES:
- utilise exclusivement le français,
- cite fidèlement les faits et chiffres,
- n'invente rien hors transcription,
- aucune prose hors JSON, pas de code block.
TRANSCRIPTION:
<<<TRANSCRIPT>>>
""".trimIndent()
}

object Summarizer {
    private const val TAG = "Summarizer"

    fun summarizeToJson(
        context: Context,
        text: String,
        durationMs: Long
    ): Summary {
        val decoratedTranscript = "Durée estimée (ms): $durationMs\n$text"
        val prompt = PremiumPrompt.JSON_STRUCT_PROMPT.replace("<<<TRANSCRIPT>>>", decoratedTranscript)
        val validator = StructuredSummaryValidator()
        val llm = LocalLlmClient.create(context)
        val llmJson = llm?.generateStructuredSummary(prompt, durationMs)
        val candidate = llmJson
            ?.let { runCatching { JSONObject(it) }.onFailure { Log.w(TAG, "JSON LLM invalide", it) }.getOrNull() }
            ?.takeIf { validator.isValid(it) }

        if (candidate != null) {
            return Summary(0, 0, candidate.toString())
        }

        if (llm == null) {
            Log.w(TAG, "Modèle LLM local absent, utilisation du fallback heuristique")
        } else {
            Log.w(TAG, "Sortie LLM rejetée, fallback heuristique appliqué")
        }

        val fallback = FallbackSummarizer.generate(text, durationMs)
        return Summary(0, 0, fallback.toString())
    }
}

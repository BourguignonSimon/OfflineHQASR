# RELEASE_NOTES

## v0.6.2
- Mise à jour toolchain (AGP 8.6.1 / Gradle 8.7) et nettoyage .gitignore
- Workflow GitHub Actions (assembleDebug, test, lint)
- README refondu + exemples d’exports JSON/Markdown
- Paramètres Whisper: activation runtime, sélection modèle, langue préférée
- Action "Purger toutes les données" avec confirmation
- Internationalisation FR/EN de l’UI
- Export JSON enrichi (audio, stt, segments, summary) + échantillons docs/samples
- Suite de tests unitaires/instrumentés (normalisation, fusion segments, pipeline export)

## v0.6.0
- UI Détail: lecteur audio, segments cliquables, export JSON
- Import modèles via menu (Vosk zip, Whisper gguf)
- Export Markdown (zip) pour toutes les sessions
- Room + WorkManager stabilisés
- Notifications d’enregistrement

## v0.5.0
- Ajout Summarizer offline (naïf) et structure JSON
- Export JSON d’une session
- Nettoyage code et packaging

## v0.4.0
- Intégration VoskEngine pour transcription offline
- Segmentation basique par mots avec timecodes

## v0.3.0
- Service d’enregistrement en WAV 48 kHz PCM
- Sauvegarde fichiers en stockage privé

## v0.2.0
- Room: tables recordings, transcripts, summaries, segments
- Liste des enregistrements + navigation

## v0.1.0
- Squelette projet Android (Kotlin, Material, RecyclerView)

# OfflineHQASR

OfflineHQASR is a 100% offline Android application to **record**, **transcribe**, **summarise** and **export** meetings directly on-device.

```
[Microphone] -> WAV (files/audio)
                 |
                 v
          Transcription
        [Vosk | Whisper]
                 |
                 v
        Segments + Summary
                 |
                 v
       JSON / Markdown export
```

## 1. Prerequisites

- Android Studio **Ladybug** / **Koala** (Giraffe minimum)
- Android SDK 26+ (minSdk 26, targetSdk 34)
- JDK 17

## 2. Project setup

```bash
git clone https://github.com/BourguignonSimon/OfflineHQASR.git
cd OfflineHQASR
./gradlew assembleDebug
```

First import may take a couple of minutes while Gradle resolves dependencies.

## 3. Speech models

### Vosk (default)
- Recommended: [`vosk-model-small-fr-0.22.zip`](https://alphacephei.com/vosk/models) or any Vosk package compatible with your target language (FR/EN tested).
- Import inside the app: overflow menu → **Import Vosk model**. Point to the original ZIP file. The app extracts and installs it under `files/models/vosk`.

### Whisper (optional)
- Supports GGML / GGUF models (tiny/base recommended for <4 GB RAM).
- Import: overflow menu → **Import Whisper model** and pick the `.ggml`/`.gguf` file.
- Activate runtime toggle in **Settings → Speech engine** (coming soon) or via the build flag `-PuseWhisperJni=true` until the settings screen ships.
- Keep an eye on memory usage; large models (>small) require ≥6 GB RAM.

## 4. Using the app

1. Tap the 🎙️ button to start recording. Grant microphone (and notification on Android 13+) permissions if requested.
2. Stop recording. WorkManager transcribes the audio and generates an offline summary.
3. Open the session to review audio playback, timestamped segments, and the generated summary (title/context/bullets/keywords/actions).
4. Export sessions to JSON or Markdown ZIP from the overflow menu.

## 5. Exports

Exports live in `files/exports/`. Each session can be exported independently (`.json`) or in bulk (`export_markdown_<timestamp>.zip`).

### JSON schema (excerpt)

```json
{
  "id": "session-uuid",
  "device": {
    "manufacturer": "Google",
    "model": "Pixel 7"
  },
  "audio": {
    "path": "files/audio/2024-05-12_10-15-30.wav",
    "duration_s": 187.4,
    "sample_rate_hz": 16000
  },
  "stt": {
    "engine": "vosk",
    "language": "fr-FR",
    "version": "0.22"
  },
  "segments": [
    { "t0": 0.0, "t1": 5.8, "text": "Bonjour…" }
  ],
  "summary": {
    "title": "Point hebdo produit",
    "context": "Réunion d'équipe produit du lundi matin",
    "bullets": ["Revue des objectifs Q3"],
    "keywords": ["roadmap"],
    "actions": ["Partager la maquette finale"]
  }
}
```

Sample assets are available under [`docs/samples/`](docs/samples/) including a reference JSON, Markdown file, and an archive structure guide without embedding binary ZIPs.

## 6. Architecture

| Layer | Responsibility |
|-------|----------------|
| `ui/` | Activities, adapters, runtime permissions, playback & export actions |
| `recorder/` | Foreground recording service and speech-to-text engines (Vosk / Whisper) |
| `data/` | Room database for recordings, transcripts, summaries, segments |
| `summary/` | Offline heuristics to generate meeting summaries |
| `export/` | Markdown & JSON exporters, model import helpers |

## 7. Privacy & data retention

- All artefacts stay on device storage (`files/` private sandbox).
- No analytics, networking, or cloud uploads.
- Use overflow menu → **Purge local data** to delete audio, models, exports, and database rows in one tap.

## 8. Continuous integration

GitHub Actions workflow [`android.yml`](.github/workflows/android.yml) runs on every push/PR and executes:

```bash
./gradlew assembleDebug test lint
```

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Configuration.fileCollection(Spec)` or similar Gradle errors | Ensure Gradle wrapper (8.6) matches AGP (8.4) and run `./gradlew --stop && ./gradlew clean`. |
| Model not detected after import | Make sure you selected the original Vosk ZIP or Whisper `.ggml/.gguf` file. |
| Whisper import succeeds but app crashes on transcription | Switch to a smaller model (tiny/base) or disable Whisper fallback. |

## 10. Roadmap

- Runtime Whisper toggle inside the settings screen
- English & French localisation (strings already available)
- Device benchmarks for different STT models
- Optional diarisation for multi-speaker sessions
- Chapter-based summaries and timeline navigation

## 11. Licence

MIT – see [`LICENSE`](LICENSE).

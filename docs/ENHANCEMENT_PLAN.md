# Enhancement Roadmap

This document consolidates the enhancement opportunities identified during the initial review and incorporates the latest product decisions.

## Build & Compilation
- Provide concrete implementations (or migrations) for `RecordService.start(context)` and `RecordService.stop(context)` so that the calls from `MainActivity` compile.
- Add the missing `java.io.RandomAccessFile` import where the WAV header is rewritten during stop to avoid unresolved references.

## Permissions & Lifecycle
- Request the `POST_NOTIFICATIONS` runtime permission on Android 13+ so the foreground recording notification can appear for first-time users.
- Persist microphone permission and service state across configuration changes (e.g., re-check in `onResume`) to keep the record button in sync with the actual recording status.

## Model Management
- Detect the real model directory after unzipping a Vosk package (some archives wrap the model inside another folder) and update the engine configuration accordingly.
- Surface actionable errors when the model folder is missing or corrupted so WorkManager jobs report failure instead of crashing via `require` in `VoskEngine`.
- When importing models through SAF, persist URI permissions and handle name collisions to avoid partial overwrites.

## Transcription Pipeline
- Honor `BuildConfig.USE_WHISPER` (and the JNI flag) by dynamically selecting between `VoskEngine` and `WhisperEngine`; Whisper should act as an automatic alternative whenever the JNI layer is present, not just via a developer-only switch.
- Close heavy native resources such as the Vosk `Model` after each transcription to prevent leaks across multiple recordings.
- Improve `TranscribeWork` error handling (return `Result.retry()` on transient issues, guard against negative `recordingId`) to keep the work queue healthy.

## Data Presentation & UX
- Aggregate Vosk word-level results into readable segments or sentences before persisting them so the detail view is easier to scan.
- Display the generated summary inside `DetailActivity` using a simplified presentation (title, context, bullet highlights) instead of raw JSON.
- Format timestamps (e.g., `mm:ss`) and show transcript text directly for better navigation.
- Revisit the combination of `ScrollView` + `RecyclerView` to avoid measurement issues with long transcripts.

## Export & Storage
- Escape Markdown content when exporting transcripts so special characters do not break formatting.
- Extend export error messaging to cover missing models, insufficient storage, or SAF permission revocations.

## Localization & Language Support
- Ensure both French and English models are first-class citizens across prompts, summaries, and UI strings. Prepare the model loader to scale to additional languages in future iterations.

## Testing & Documentation
- Expand the testing plan (unit/manual) with concrete steps for transcription accuracy, retry scenarios, and export validation.
- Update the README with troubleshooting for model placement, Whisper activation, permissions, and language-specific guidance.

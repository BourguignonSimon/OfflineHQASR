# MVP Delivery Plan

This playbook inventories the current implementation, defines the workstreams required to ship a testable MVP, and sequences the execution so the team can converge quickly.

## 1. Capabilities already in place

- **Foreground recording service** – the service spins up a foreground notification, captures 48 kHz PCM audio to WAV, persists the session in Room, and enqueues transcription work once the loop stops.【F:app/src/main/java/com/example/offlinehqasr/recorder/RecordService.kt†L44-L127】
- **Automatic transcription pipeline (Vosk-first)** – `TranscribeWork` selects Whisper when available, otherwise Vosk, processes the file and persists transcript + segments before triggering summarisation.【F:app/src/main/java/com/example/offlinehqasr/recorder/TranscribeWork.kt†L18-L49】
- **Vosk segmentation & parsing** – the current engine streams WAV data into Vosk, aggregates word ranges into segments with timestamps, and builds the transcript text.【F:app/src/main/java/com/example/offlinehqasr/recorder/VoskEngine.kt†L19-L91】
- **Baseline summary integration** – the summariser produces a JSON payload (title, context, bullets, keywords/topics) stored alongside each recording and surfaced in the detail header.【F:app/src/main/java/com/example/offlinehqasr/summary/Summarizer.kt†L26-L47】【F:app/src/main/java/com/example/offlinehqasr/ui/DetailActivity.kt†L32-L88】
- **Model import/export tooling** – the UI lets users import Vosk ZIPs or Whisper models via SAF, and export recordings to Markdown or JSON files.【F:app/src/main/java/com/example/offlinehqasr/ui/MainActivity.kt†L42-L120】【F:app/src/main/java/com/example/offlinehqasr/export/ExportUtils.kt†L15-L120】
- **Room persistence & UI shell** – recordings, transcripts, summaries, and segments are stored in Room, displayed in the main list/detail screens, and playable via the integrated media player.【F:app/src/main/java/com/example/offlinehqasr/data/Entities.kt†L5-L43】【F:app/src/main/java/com/example/offlinehqasr/data/Dao.kt†L6-L45】【F:app/src/main/java/com/example/offlinehqasr/ui/MainActivity.kt†L49-L118】【F:app/src/main/java/com/example/offlinehqasr/ui/DetailActivity.kt†L21-L117】

## 2. Workstreams & backlog for MVP

The requirements document sets the bar for the MVP.【F:docs/PRODUCT_REQUIREMENTS.md†L7-L93】 Each workstream below lists concrete tasks with clear inputs/outputs so contributors can pick them up immediately.

### 2.1 Audio capture & conditioning

| ID | Task | Prerequisites | Deliverable | Validation |
| --- | --- | --- | --- | --- |
| AC1 | Implement adaptive microphone selection (wired, Bluetooth SCO, internal) in `RecordService` with graceful fallback when the preferred device disconnects.【F:app/src/main/java/com/example/offlinehqasr/recorder/RecordService.kt†L75-L127】 | AudioManager permissions, runtime Bluetooth checks | Updated capture pipeline with device change notifications to UI | Manual tests: plug/unplug wired headset, toggle Bluetooth mid-recording |
| AC2 | Upgrade WAV writer to stereo when the upstream device exposes two channels; maintain mono fallback otherwise.【F:docs/PRODUCT_REQUIREMENTS.md†L21-L27】 | AC1 | 48 kHz stereo WAV files (with metadata) stored in session folder | Inspect WAV headers, play back on-device |
| AC3 | Insert gain normalisation and RNNoise-based denoise block before persisting audio; expose switches for future tuning.【F:docs/PRODUCT_REQUIREMENTS.md†L21-L27】 | AC1 | Kotlin wrapper around RNNoise/ONNX with streaming API | Unit tests over captured buffers; golden audio comparison |
| AC4 | Add VAD-driven flow monitoring to raise foreground notification + UI banner when silence threshold or permission errors indicate a drop.【F:docs/PRODUCT_REQUIREMENTS.md†L24-L27】 | AC1, AC3 | Runtime alerts surfaced through `MainActivity` | Instrumentation test simulating permission revoke |

### 2.2 Service lifecycle hardening

| ID | Task | Prerequisites | Deliverable | Validation |
| --- | --- | --- | --- | --- |
| SL1 | Introduce explicit state machine for `RecordService.start/stop`, covering idempotent restarts and notification permission gating.【F:app/src/main/java/com/example/offlinehqasr/recorder/RecordService.kt†L44-L166】 | None | Updated service with exhaustive states + logs | Unit tests mocking lifecycle transitions |
| SL2 | Broadcast state updates via `LiveData`/`Flow` so UI components react instantly to service events (start, pause, error).【F:app/src/main/java/com/example/offlinehqasr/ui/MainActivity.kt†L62-L104】 | SL1 | UI view-model updates and persisted session state | Espresso test verifying banner visibility |

### 2.3 Transcription robustness (Vosk-first)

| ID | Task | Prerequisites | Deliverable | Validation |
| --- | --- | --- | --- | --- |
| TR1 | Implement windowed decoding (e.g., 30 s) with context stitching so long sessions stay within memory limits.【F:app/src/main/java/com/example/offlinehqasr/recorder/VoskEngine.kt†L19-L95】 | AC workstream complete | Updated `VoskEngine` chunker | Compare transcripts vs. baseline; ensure <5% WER drift |
| TR2 | Add structured error handling for missing/corrupt models and requeue logic with bounded retries in `TranscribeWork`.【F:app/src/main/java/com/example/offlinehqasr/recorder/TranscribeWork.kt†L23-L49】 | TR1 | WorkManager job with retry policy + telemetry | Automated test injecting corrupt model folder |
| TR3 | Capture processing duration + device metrics for the 60’→30’ SLA and surface in session detail header.【F:docs/PRODUCT_REQUIREMENTS.md†L28-L34】 | TR1 | Persisted metrics in Room | UI check; instrumentation reading DB |

### 2.4 Structured summarisation

| ID | Task | Prerequisites | Deliverable | Validation |
| --- | --- | --- | --- | --- |
| SS1 | Integrate selected on-device LLM runtime (e.g., MLC, ONNX) with 3–4B instruct model packaged via import flow.【F:docs/PRODUCT_REQUIREMENTS.md†L35-L38】 | Model packaging guidelines, AC & TR streams stable | Kotlin façade exposing `summarise()` API | Smoke test summarising canned transcript |
| SS2 | Enforce full JSON schema (actions, decisions, quotes, sentiments, participants, tags, keywords, timings) and persist to Room.【F:docs/PRODUCT_REQUIREMENTS.md†L35-L38】 | SS1 | Schema-validated JSON + migration script | Unit tests verifying schema + fallback path |
| SS3 | Implement fallback summariser (existing heuristic) with explicit warning toast when LLM unavailable. | SS2 | Resilient summary pipeline | Manual test removing model |

### 2.5 Secure storage & export

| ID | Task | Prerequisites | Deliverable | Validation |
| --- | --- | --- | --- | --- |
| SE1 | Generate AES-GCM keys in Android Keystore, wrap WAV read/write in streaming cipher utilities.【F:app/src/main/java/com/example/offlinehqasr/recorder/RecordService.kt†L89-L123】【F:docs/PRODUCT_REQUIREMENTS.md†L49-L54】 | AC workstream stable | `CryptoStore` helper + encrypted audio files | Unit tests with deterministic IV; manual decrypt check |
| SE2 | Migrate Room database to SQLCipher (or Room Security) with passphrase sealed by Keystore.【F:app/src/main/java/com/example/offlinehqasr/data/AppDb.kt†L11-L25】 | SE1 | Encrypted DB + migration script + recovery strategy | Instrumented DB open/close tests |
| SE3 | Ensure exports (MD/JSON) are optionally encrypted; wipe metadata on delete request.【F:docs/PRODUCT_REQUIREMENTS.md†L49-L54】 | SE1, SE2 | Updated `ExportUtils` with encryption flag | Manual export/import verification |

### 2.6 Search, filters & UI polish

| ID | Task | Prerequisites | Deliverable | Validation |
| --- | --- | --- | --- | --- |
| UI1 | Add FTS5 virtual table synced with transcripts and segments; expose DAO MATCH queries.【F:app/src/main/java/com/example/offlinehqasr/data/AppDb.kt†L11-L25】【F:docs/PRODUCT_REQUIREMENTS.md†L42-L47】 | TR workstream | New DAO + migration | Unit test verifying MATCH results |
| UI2 | Update main list screen to support query box, filters (date, duration, tags, participants) and relevance ordering.【F:app/src/main/java/com/example/offlinehqasr/ui/MainActivity.kt†L49-L118】 | UI1, SS2 | Enhanced RecyclerView adapters + chips | Espresso UI filter test |
| UI3 | Expand detail screen to display structured summary fields, SLA metrics, and encryption state badges.【F:app/src/main/java/com/example/offlinehqasr/ui/DetailActivity.kt†L21-L117】 | SS2, TR3, SE1 | Updated layouts + binding logic | Snapshot test |

### 2.7 Model management & error surfacing

| ID | Task | Prerequisites | Deliverable | Validation |
| --- | --- | --- | --- | --- |
| MM1 | Extend import flow with checksum validation, progress indicator, and failure recovery (rollback partial copy).【F:app/src/main/java/com/example/offlinehqasr/ui/MainActivity.kt†L80-L120】 | None | UX with progress dialog + logs | Manual test with invalid ZIP |
| MM2 | Surface WorkManager failures via notifications + in-app inbox so the user can relaunch tasks.【F:app/src/main/java/com/example/offlinehqasr/recorder/TranscribeWork.kt†L30-L49】 | MM1 | Error reporting channel + persisted status | Instrumented test injecting worker failure |

### 2.8 Quality engineering

| ID | Task | Prerequisites | Deliverable | Validation |
| --- | --- | --- | --- | --- |
| QE1 | Update `TESTING.md` with manual checklists covering microphone loss, Bluetooth toggles, battery limits, corrupt audio, and export flows.【F:docs/PRODUCT_REQUIREMENTS.md†L63-L68】 | Inputs from all streams | Comprehensive manual QA doc | Review + sign-off |
| QE2 | Add instrumentation/unit tests targeting service lifecycle, transcription retries, summary schema validation, and encryption helpers. | QE1 | Automated suite executed in CI | CI green run |

## 3. Execution sequence & milestone tracking

1. **Foundation sprint (Week 1)** – close SL1, AC1, TR1 prerequisites so the capture/transcribe loop is stable; begin SE1 scaffolding.
2. **Audio fidelity sprint (Week 2)** – deliver AC2–AC4, finish SE1, and start QE1 to capture manual test matrix.
3. **Pipeline resilience sprint (Week 3)** – land TR2–TR3, MM1, MM2, and wire initial telemetry for SLA tracking.
4. **Structured insights sprint (Week 4)** – ship SS1–SS3, UI1, UI3, and DB migrations (SE2). Ensure fallback summary remains stable.
5. **Search & polish sprint (Week 5)** – wrap UI2, SE3, QE2, and run full regression on physical devices ahead of beta.

Milestone exit criteria:

- ✅ Capture pipeline delivers stereo (where supported), normalised and denoised audio with runtime error surfacing.
- ✅ Transcription completes a 60-minute session within 30 minutes on the reference device, logging metrics into Room.
- ✅ Structured summary JSON complies with agreed schema and renders correctly in the detail screen.
- ✅ Audio, database, and exports are encrypted end-to-end, with documented wipe procedure.
- ✅ Search filters operate on encrypted data (after decrypt-on-access) with coverage in automated tests.

## 4. Roles, artefacts & tracking cadence

- **Tech lead** – owns sequencing, code reviews, cross-stream integration, and definition-of-done gating.
- **Android engineer** – implements capture, lifecycle, UI tasks; pairs with QA on instrumentation coverage.
- **ML engineer** – packages RNNoise and local LLM models, benchmarks performance, and tunes inference settings.
- **Security/Privacy reviewer** – validates Keystore usage, SQLCipher configuration, and wipe semantics.
- **QA owner** – maintains manual test playbooks, drives beta sign-off, and records WER/SLA metrics.

Tracking routine:

- Daily stand-up (15 min) focused on blockers across AC/TR/SE/UI streams.
- Twice-weekly backlog refinement to re-estimate tasks, update dependencies, and slot follow-up bugs.
- End-of-sprint demo + retro to validate milestone exit criteria and adjust subsequent sprint scope.

Artefacts to keep current:

- Sprint board (Kanban or Jira) mirroring the tables above with status columns (Todo / In progress / In review / Done).
- Shared test audio corpus with expected outputs for regression.
- Schema contract document for summary JSON and encrypted storage metadata.

## 5. Phase 2+ backlog (post-MVP)

The following improvements align with the roadmap but are not blocking the MVP release:

- **Enhanced background resilience** – smarter throttling, doze-aware capture, and thermal-aware scheduling beyond the MVP guardrails.【F:docs/PRODUCT_REQUIREMENTS.md†L10-L19】
- **Whisper JNI integration** – replace Vosk with whisper.cpp GGUF models (large-v3/medium) once the native layer and memory heuristics are in place.【F:docs/PRODUCT_REQUIREMENTS.md†L16-L18】【F:app/src/main/java/com/example/offlinehqasr/recorder/TranscribeWork.kt†L30-L49】
- **Advanced LLM outputs** – chapter breakdowns, participant attribution, entity graphs, and sentiment deep-dives using larger local models.【F:docs/PRODUCT_REQUIREMENTS.md†L35-L40】
- **Multilingual expansion & translation** – extend beyond FR/EN once the baseline is stable.【F:docs/PRODUCT_REQUIREMENTS.md†L31-L32】
- **Audit trail & secure sharing** – tamper-evident logs, encrypted sharing flows, and adapters for third-party offline storage (Nextcloud, exports métiers).【F:docs/PRODUCT_REQUIREMENTS.md†L49-L55】【F:docs/PRODUCT_REQUIREMENTS.md†L77-L82】
- **UI refinements** – thematic grouping, project workspaces, richer status dashboards, and accessibility polishing after MVP feedback.【F:docs/PRODUCT_REQUIREMENTS.md†L42-L47】

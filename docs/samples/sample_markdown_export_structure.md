# Markdown Export Structure

```
export_markdown_2024-05-12T10-15-30Z.zip
├── README.md
├── session_2024-05-12.md
└── assets/
    └── session_2024-05-12.wav
```

* `README.md` — high level description of the archive contents and generation timestamp.
* `session_*.md` — Markdown transcript with metadata header, summary, and timestamped segments.
* `assets/*.wav` — original audio attachments referenced from the Markdown file.

This structure mirrors the archives produced by the app so you can replicate or validate exports without distributing large binary files in the repository.

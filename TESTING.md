# TESTING

## Unitaires (idées)
- `Summarizer.summarizeToJson` retourne JSON valide
- `ExportUtils.toMarkdown` contient sections attendues
- DAO Room: insert/get pour Recording, Transcript, Segment, Summary

## Manuels
1. **Enregistrement**: démarrer/arrêter. Fichier WAV présent dans `files/audio`.
2. **Import Vosk**: menu → zip FR. Status doit afficher `Vosk: OK`.
3. **Transcription**: après enregistrement, segments et transcript doivent apparaître.
4. **Résumé**: Détail → vérifier résumé synthétisé dans export JSON.
5. **Export Markdown**: menu → zip généré dans `files/exports`.
6. **Import Whisper**: menu → `.gguf` copié dans `files/models/whisper`.


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
7. **Sélection entrée micro**: connecter successivement casque filaire et Bluetooth. Lancer un enregistrement et vérifier dans les logs `RecordService` que la source détectée correspond (`wired-headset`, `bt-sco`, `builtin`).
8. **Perte micro**: retirer le périphérique ou révoquer l'autorisation pendant l'enregistrement. La bannière rouge doit s'afficher avec le message adapté et l'enregistrement se termine proprement.
9. **Bruit de fond**: enregistrer un environnement bruyant et comparer le WAV avec et sans pipeline (normalisation + noise-gate) pour confirmer la réduction du souffle.


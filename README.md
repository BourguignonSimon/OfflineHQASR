# OfflineHQASR (v0.6.0)

Application Android native 100% offline pour enregistrer, transcrire (Vosk par défaut), résumer et exporter.

## Build rapide
1. Ouvrir ce dossier dans **Android Studio** (Giraffe ou plus récent).
2. Laisser Gradle télécharger les dépendances.
3. Lancer sur un smartphone (minSdk 26, target 34).

## Premier run
- Menu ⋮ → **Importer modèle Vosk** et choisir `vosk-model-small-fr-0.22.zip` (le zip sera décompressé dans `files/models/vosk`).
- Appuyer sur **micro** pour enregistrer. Arrêter.
- La **transcription** est lancée via WorkManager, puis un **résumé** est généré (offline).
- Ouvrir la session → **lecture audio**, segments cliquables, **export JSON**. Menu: **Exporter Markdown** (zip).

## Dossiers privés
- Audio: `files/audio/*.wav`
- Modèles: `files/models/vosk` et `files/models/whisper`
- Exports: `files/exports/*.zip|*.json`

## Sécurité & chiffrement
- Les enregistrements WAV sont chiffrés en continu via AES-GCM avec une clé non exportable du Keystore Android. Les métadonnées (`*.wav.meta`) contiennent l'IV nécessaire et doivent être conservées avec chaque fichier.
- La base `app.db` (transcripts, résumés, segments) est protégée par SQLCipher avec un mot de passe stocké uniquement chiffré dans l'espace privé de l'application.
- Les exports Markdown/JSON sont générés sur un flux chiffré AES-GCM. Pour toute sauvegarde manuelle, conserver le couple fichier + `.meta` associé afin de permettre la ré-importation par l'application.

## Whisper (optionnel)
- Voir `whisper/README.md`. Possibilité d’activer via `-PuseWhisperJni=true` et `BuildConfig.USE_WHISPER`.

## Notes
- Résumé: implémentation **naïve offline** par défaut. Remplacez par un LLM local si souhaité (voir `PROMPTS.md`).


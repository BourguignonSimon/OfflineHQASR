# OfflineHQASR v0.6.2

Application Android 100 % hors-ligne pour enregistrer, transcrire (Vosk par défaut, Whisper en option), résumer et exporter vos réunions.

## Installation
- Android Studio Koala Feature Drop (ou plus récent) avec Android Gradle Plugin 8.6.1 et Gradle 8.7.
- SDK Android : minSdk 26, targetSdk 34.
- Cloner le dépôt puis construire via :
  ```bash
  ./gradlew assembleDebug
  ```
- Vérifier l’outillage avec `./gradlew --version` (également mentionné dans la PR).

## Import de modèles
### Vosk (défaut)
1. Depuis l’application, menu ⋮ → **Importer un modèle Vosk**.
2. Sélectionner le ZIP téléchargé (ex. `vosk-model-small-fr-0.22.zip`).
3. Le fichier est décompressé automatiquement dans `files/models/vosk`.

### Whisper (optionnel)
- Accepte les modèles GGML/GGUF.
- Activer **Utiliser Whisper (beta)** dans Paramètres puis choisir le chemin du modèle.
- Recommandations RAM :
  - `tiny` : < 2 Go, transcription rapide.
  - `base` : 4 Go et plus, bon compromis.
  - `small` : ≥ 6 Go pour meilleure précision.
- Aucune recompilation nécessaire.

## Utilisation
1. **Enregistrer** : appuyer sur le micro pour capturer l’audio (stocké dans `files/audio/`).
2. **Transcrire** : Vosk démarre automatiquement ; Whisper prend la main si activé et disponible.
3. **Résumer** : génération de puces hors-ligne.
4. **Relire** : consulter les segments, ajuster le texte, écouter l’audio.
5. **Exporter** : produire JSON ou Markdown depuis l’écran session.

## Export
- Schéma JSON minimal :
  ```json
  {
    "id": "session-uuid",
    "device": "Pixel-7",
    "audio": {"path": "files/audio/2025-10-14.wav", "duration_s": 123.4},
    "stt": {"engine": "vosk", "language": "fr"},
    "segments": [{"t0": 0.0, "t1": 3.2, "text": "Bonjour à tous."}],
    "summary": {"bullets": ["Point A", "Point B"], "keywords": ["mot-clé A", "mot-clé B"]}
  }
  ```
- Exemple Markdown : [docs/samples/sample_session.md](docs/samples/sample_session.md)
- JSON exemple : [docs/samples/sample_session.json](docs/samples/sample_session.json)

## Confidentialité
- Aucun trafic réseau : toutes les opérations se font sur l’appareil.
- Données stockées dans le stockage privé (`files/`).
- Bouton **Purger tout** (Paramètres) pour supprimer audio, exports et fichiers temporaires.

## Dépannage
1. **Erreur AGP/Gradle** : relancer `File > Sync Project with Gradle Files` et vérifier `./gradlew --version`.
2. **Modèle Vosk introuvable** : réimporter le ZIP, vérifier l’espace disque, redémarrer l’app.
3. **Whisper manque de mémoire** : sélectionner un modèle plus petit ou désactiver Whisper (fallback Vosk automatique).
4. **Export échoue** : vérifier l’autorisation stockage interne et l’espace libre, puis relancer l’export.
5. **Micro inactif** : confirmer l’autorisation d’enregistrement dans Paramètres Android > Applications > OfflineHQASR.
6. **Tests instrumentation échouent** : lancer `./gradlew connectedAndroidTest` sur un appareil physique en mode développeur.

## Build & CI
- Commande locale : `./gradlew assembleDebug test lint`
- Pipeline GitHub : [Android CI](.github/workflows/android.yml)

## Exemples d’export
Consultez le dossier [docs/samples/](docs/samples/) pour les exports JSON et Markdown prêts à l’emploi.

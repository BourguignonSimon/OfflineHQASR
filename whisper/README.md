# Whisper JNI (optionnel)

Cette application embarque un **placeholder** pour Whisper JNI. Pour l’activer:

1. Ajoutez votre binaire JNI (whisper.cpp/ggml) et liaisons Kotlin/Java.
2. Placez votre modèle `.gguf` via le menu **Importer modèle Whisper**. Le fichier sera copié dans `files/models/whisper`.
3. Construisez avec:
   ```bash
   ./gradlew assembleDebug -PuseWhisperJni=true
   ```
4. Dans le code, remplacez l'appel dans `TranscribeWork` par `WhisperEngine.transcribeFile` si `BuildConfig.USE_WHISPER` est `true`.

> Important: assurez-vous qu'aucune régression n’affecte la transcription Vosk par défaut.

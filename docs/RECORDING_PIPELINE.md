# Chaîne d'enregistrement audio

Ce document décrit la logique mise en place pour sélectionner la meilleure entrée micro, appliquer les traitements et remonter les erreurs à l'interface.

## Sélection dynamique de la source

`RecordService` récupère les périphériques disponibles via `AudioManager`. La priorité est la suivante :

1. Microphone de casque filaire (`TYPE_WIRED_HEADSET`).
2. Casque Bluetooth SCO (`TYPE_BLUETOOTH_SCO`).
3. Micro interne (`TYPE_BUILTIN_MIC`).
4. Fallback vers la source par défaut si aucun périphérique n'est détecté.

Le périphérique choisi est forcé via `AudioRecord.Builder#setPreferredDevice`, ce qui évite de démarrer l'enregistrement sur la mauvaise entrée si plusieurs sources sont connectées.

## Chaîne de traitement PCM

Les échantillons 16 bits sont convertis en mémoire puis traités avant l'écriture WAV :

1. **Normalisation adaptative** – `GainNormalizer` maintient le niveau RMS autour d'une cible (≈ -15 dBFS) avec une attaque rapide et un relâchement lent.
2. **Réduction de bruit RNNoise/ONNX** – `RnNoiseDenoiser` tente d'initialiser un modèle ONNX (`rnnoise.onnx`). En l'absence de runtime ou de modèle, un noise-gate adaptatif est appliqué pour conserver un comportement déterministe. Remplacer l'asset par un modèle RNNoise exporté en ONNX permet d'activer l'inférence automatiquement.

## Gestion des erreurs d'acquisition

`AudioRecord.read` est surveillé pour détecter les cas critiques :

- `ERROR_INVALID_OPERATION` : autre application ayant pris le contrôle du micro.
- `ERROR_BAD_VALUE` : flux invalide ou buffer corrompu.
- `ERROR_DEAD_OBJECT` : périphérique déconnecté (Bluetooth, jack retiré, etc.).
- `SecurityException` : perte d'autorisation micro (retirée depuis les paramètres système).

À la moindre erreur, le service diffuse `ACTION_RECORDING_ERROR` avec un code précis. `MainActivity` affiche alors une bannière persistante jusqu'à dismissal manuel, et l'enregistrement est stoppé proprement (header WAV corrigé, ressource `AudioRecord` libérée).

## Scénarios de test manuel

1. **Casque Bluetooth SCO**
   - Appairer un casque Bluetooth.
   - Démarrer l'enregistrement : vérifier dans les logs que la source `bt-sco` est sélectionnée.
   - Couper le casque pendant l'enregistrement : la bannière doit s'afficher avec le message *Périphérique micro déconnecté*.

2. **Casque filaire**
   - Brancher un kit main-libre.
   - Lancer l'enregistrement : le log indique `wired-headset` et le signal enregistré doit provenir du micro du casque.

3. **Perte d'autorisation**
   - Lancer un enregistrement.
   - Aller dans les paramètres Android et retirer l'autorisation micro.
   - Retourner à l'application : l'enregistrement s'arrête et la bannière affiche *Autorisation micro perdue*.

4. **Conflit avec une autre app**
   - Lancer un enregistrement puis démarrer une application concurrente utilisant le micro (appel VoIP par exemple).
   - Le service doit détecter `invalid_operation` et afficher le message correspondant.

5. **Bruit de fond constant**
   - Enregistrer dans un environnement bruyant : comparer les niveaux avant/après traitement (gain constant + noise-gate).

Consigner les résultats et conserver les logs `RecordService` pour validation.

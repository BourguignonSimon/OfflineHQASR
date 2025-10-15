# Exigences consolidées pour le MVP de transcription hors-ligne

Ce document synthétise les décisions actées suite à la phase de cadrage. Il sert de référence
fonctionnelle et technique pour livrer le MVP de l'application Android de transcription et
d'organisation de conversations audio 100 % locale.

## 1. Vision produit & usages
- **Cas d'usage cibles** : réunions, entretiens, mémos, notes personnelles. L'application doit rester simple pour un usage single-user.
- **Durée des sessions** : 30 à 60 minutes, avec découpe automatique en blocs de 30 minutes concaténables.
- **Fonctionnement en arrière-plan** : supporté via Foreground Service ; les améliorations avancées d'arrière-plan sont planifiées en phase 2.
- **Annotations** : réalisées uniquement après coup. La version générée (transcription + résumé) demeure immuable pour audit.

## 2. Plateforme & contraintes matérielles
- **Plateforme** : Android ciblant API level 35 (Android 15). Support garanti pour les 12 derniers mois de versions officielles, avec minSdk 26 pour la compatibilité.
- **Appareil de référence** : smartphones Snapdragon 8 Gen 2/3 avec 8–12 Go RAM (ex. Honor Magic6 Pro).
- **Modèles Whisper** : large-v3 Q5_0 prioritaire ; fallback medium Q5_0 pour les appareils < 8 Go RAM.
- **Form factor** : smartphones priorisés ; support tablettes/pliables reporté.
- **Thermique/autonomie** : traitements différés (WorkManager) avec throttling quand la température est élevée.
- **Stockage** : pas d'optimisation ni rotation automatique au lancement ; options activables ultérieurement.

## 3. Enregistrement audio
- **Sources** : micro interne, micro filaire et Bluetooth avec sélection automatique.
- **Pré-écoute** : non prévue en V1.
- **Gestion des ruptures** : détection en temps réel et bannière d'alerte (perte de permission, micro déconnecté).
- **Format** : WAV PCM 48 kHz / 16-bit (stéréo si disponible).
- **Pré-traitement** : normalisation + réduction de bruit légère avant l'étape ASR.

## 4. Transcription & pipeline IA
- **Mode de qualité** : unique mode haute précision, pas d'option « rapide ».
- **Segmentation** : segmentation automatique Whisper (fenêtrage + reprise mémoire) pour les longues sessions.
- **Langues** : transcription FR/EN native si le modèle le permet. Pas de traduction automatique.
- **Low-power** : non implémenté.
- **SLA de traitement** : transcription + résumé pour 60 minutes d'audio en ≤ 30 minutes hors-ligne.

## 5. Résumés & structuration locale
- **Granularité** : résumé global unique en V1 (chapitres/intervenants en V2).
- **Sortie JSON** : inclut actions, décisions, citations, sentiments, participants, tags, topics, keywords, timings.
- **Corrections** : pas de modification sur l'original ; éventuelle régénération sur copie dérivée.
- **Entités métiers** : non gérées en V1 ; prévues pour V2.
- **Règles métiers** : filtrage simple uniquement.

## 6. Organisation, recherche & UX
- **Filtres/tri** : date, durée, pertinence FTS, tags, participants (si détectés).
- **Vue segments** : chronologie prioritaire ; découpage par thèmes en V2.
- **Projets/dossiers** : dossiers virtuels ou tags projet disponibles au lancement.
- **Personnalisation** : mode sombre automatique et police système, peu d'options.
- **Hors-ligne** : navigation et fonctionnalités totalement offline ; mises à jour de modèles manuelles.

## 7. Sécurité & confidentialité
- **Conformité** : RGPD by design grâce à l'usage strictement local. Pas d'exigence ISO/HDS pour V1 personnelle.
- **Chiffrement** : fichiers audio/transcriptions chiffrés en AES-GCM avec clés Android Keystore (non exportables).
- **Protection d'accès** : verrouillage par PIN/biométrie de l'application et/ou des enregistrements sensibles.
- **Suppression** : effacement best-effort de tous les artefacts (audio, transcript, résumé, index).
- **Audit trail** : journal local minimal (création, lecture, export) non modifiable.

## 8. Opérations & maintenance
- **Distribution** : APK en sideload. Publication store envisagée plus tard.
- **Mises à jour modèles** : import manuel (zip/GGUF) via écran dédié.
- **Outil qualité** : écran de test local (SNR, niveau, échantillonnage, WER rapide).
- **Maintenance** : assurée par l'utilisateur propriétaire, cadence mensuelle ou ad hoc.
- **Télémétrie** : journaux locaux non envoyés, export manuel sur demande.

## 9. Tests & validation
- **Métriques** : WER pour l'ASR, ROUGE/BLEU proxy pour les résumés, satisfaction utilisateur.
- **Corpus** : jeux d'audios internes couvrant réunions types et environnements variés.
- **Scénarios manuels** : perte de permission micro, interruption, batterie faible, appel entrant, bascule Bluetooth, fichier corrompu.
- **Tests automatiques** : exécution sur émulateur et appareils réels (tests instrumentés) dans la boucle de validation.
- **Validation produit** : bêta personnelle → OK → décision MVP (go/no-go individuel).

## 10. Planning & gouvernance
- **Périmètre MVP** : enregistrement, transcription offline, résumé JSON global, recherche FTS, export MD/JSON, import modèle, sécurité de base, écran détail.
- **Budget** : temps personnel et matériel existant.
- **Dépendances** : revue juridique (consentement) et sécurité légère.
- **Indicateurs de succès** : usage hebdomadaire, gain de temps sur la prise de notes, zéro fuite de données, stabilité.
- **Décision** : arbitrage final par le propriétaire du projet (qualité > temps > coût).

## 11. Annexes & évolutions
- **Politique de confidentialité** : document embarqué, succinct, rappelant l'absence de cloud.
- **Mode preuve** : génération locale d'un hash SHA-256 + horodatage + log signé.
- **Partage local** : export de fichiers chiffrés via USB ; solutions type AirDrop prévues plus tard.
- **Intégrations futures** : prévoir des adapters pour Nextcloud offline et exports métiers.
- **Support utilisateur** : FAQ embarquée + guide 1-page utilisable hors connexion.

## 12. Recommandations techniques associées
- **Enregistrement** : Foreground Service avec détection d'erreurs/permissions, format WAV 48 kHz stéréo.
- **Pré-traitement** : normalisation + denoise léger (RNNoise/ONNX en option).
- **ASR** : Whisper JNI avec modèle large-v3 Q5_0 en priorité, fallback medium Q5_0 ; segmentation automatique.
- **Résumé** : LLM local 3–4B quantifié (int8) générant un JSON structuré.
- **Organisation** : Room + FTS5, dossiers virtuels, vue chronologique.
- **Sécurité** : Keystore + chiffrement fichiers AES-GCM, PIN/biométrie, wipe best-effort, audit minimal.
- **Ops** : sideload APK, import manuel des modèles, aucune dépendance réseau.
- **Performance** : traitement différé via WorkManager, objectif 60' audio → ≤ 30' transcription + résumé.


# Questions de cadrage pour finaliser le projet

> **Statut (MVP)** : Toutes les questions ont été tranchées et consolidées dans le document `docs/PRODUCT_REQUIREMENTS.md`.
> Ce fichier reste disponible comme historique de découverte ; aucune question supplémentaire n'est ouverte à ce stade.

Ce document recense les questions critiques à adresser avec les parties prenantes afin de verrouiller le périmètre fonctionnel, technique et opérationnel de la solution 100 % locale de transcription et d'organisation de conversations audio sur Android.

## Vision produit & usages
1. Quels sont les cas d'usage prioritaires (réunions professionnelles, entretiens, prise de notes personnelle, mémos vocaux, etc.) ?
2. Souhaite-t-on gérer des conversations continues (plusieurs heures) ou des sessions courtes ?
3. Faut-il supporter l'enregistrement en tâche de fond lorsque l'écran est éteint ou que l'utilisateur utilise d'autres applications ?
4. Souhaite-t-on permettre la prise de notes manuelle ou l'annotation pendant/avant/après la transcription ?
5. Quels sont les profils utilisateurs ciblés (grand public, professionnels réglementés, équipes internes) et leurs attentes en termes de simplicité vs. richesse fonctionnelle ?

## Plateforme & contraintes matérielles
6. Quelles versions minimales d'Android et quelles gammes d'appareils devons-nous supporter (SoC, GPU, RAM, stockage) ?
7. Dispose-t-on d'estimations sur la distribution RAM/CPU des appareils cibles pour calibrer le choix de modèles Whisper et LLM ?
8. A-t-on besoin d'une compatibilité tablettes/pliables ou seulement smartphones ?
9. Existe-t-il des contraintes thermiques ou d'autonomie à respecter lors des traitements lourds (transcription, résumé) ?
10. Le stockage local doit-il être optimisé pour les appareils à faible espace libre (compression, rotation automatique, purge) ?

## Enregistrement audio
11. L'application doit-elle gérer plusieurs sources audio (micro interne, micro filaire, Bluetooth) et leur sélection automatique ?
12. Souhaite-t-on une pré-écoute ou un monitoring du signal audio pendant l'enregistrement ?
13. Faut-il détecter et signaler les ruptures de flux audio (perte de micro, permissions) en temps réel ?
14. Y a-t-il des contraintes spécifiques sur le format de fichier final (WAV, FLAC) ou sur l'échantillonnage ?
15. Doit-on prévoir une normalisation ou un nettoyage du signal (réduction de bruit, égalisation) avant transcription ?

## Transcription & pipeline IA
16. Souhaite-t-on proposer plusieurs niveaux de qualité (rapide vs. précis) configurables par l'utilisateur ?
17. Faut-il gérer automatiquement la segmentation de longs enregistrements en blocs pour Whisper afin d'éviter les dépassements mémoire ?
18. Souhaite-t-on offrir la traduction ou la transcription multilingue automatique dans la même session ?
19. Devons-nous prévoir un mode "low-power" (modèle plus petit, quantification agressive) déclenché lorsque la batterie est faible ?
20. Quel est le temps de traitement hors-ligne acceptable par les utilisateurs après un enregistrement de 60 minutes ?

## Résumés & structuration locale
21. Quelle granularité attend-on pour les résumés (global, par chapitre, par intervenant) ?
22. Quels champs doivent figurer dans le JSON structuré (actions, décisions, citations, sentiments, participants, tags personnalisés) ?
23. Souhaite-t-on permettre la correction manuelle du JSON ou la régénération d'une section ?
24. Faut-il générer des entités nommées/relations spécifiques (CRM, dossier patient, etc.) pour des intégrations futures ?
25. Doit-on prévoir des règles métiers particulières pour filtrer, redresser ou compléter les données extraites ?

## Organisation, recherche & UX
26. Quels filtres et options de tri sont nécessaires dans l'interface (date, durée, participants, tags, pertinence FTS) ?
27. Souhaite-t-on une visualisation chronologique des segments clés ou un découpage par thèmes ?
28. Faut-il offrir des dossiers/projets virtuels pour organiser les conversations ?
29. Quel niveau de personnalisation de l'interface est attendu (thèmes, police, mode sombre) ?
30. L'application doit-elle fonctionner entièrement hors-ligne pour la navigation ou autorise-t-on des métadonnées en ligne (par ex. mises à jour de modèles) ?

## Sécurité & confidentialité
31. Y a-t-il des exigences spécifiques de conformité (RGPD, ISO 27001, HDS, etc.) à satisfaire ?
32. Les utilisateurs doivent-ils pouvoir importer/exporter leurs clés de chiffrement ou effectuer une sauvegarde chiffrée externe ?
33. Doit-on implémenter une protection par code/PIN/biométrie pour accéder à l'application ou à certains enregistrements ?
34. Comment gère-t-on la suppression définitive (wipe) d'un enregistrement et de ses dérivés (transcription, résumé, index) ?
35. Faut-il un audit trail local des accès et modifications pour prouver l'intégrité des données ?

## Opérations & maintenance
36. Comment distribue-t-on l'application (APK direct, magasin d'entreprise, store public) sans rompre la promesse offline ?
37. Quel est le processus prévu pour les mises à jour des modèles IA (téléchargement manuel, sideload, carte SD) ?
38. Devons-nous fournir un outil interne pour tester la qualité audio/transcription avant mise en production ?
39. Qui assurera la maintenance corrective/évolutive et selon quel rythme de release ?
40. Faut-il prévoir une télémétrie locale (non envoyée) pour diagnostiquer les problèmes utilisateur ?

## Tests & validation
41. Quelles métriques de qualité de transcription et de résumé devons-nous suivre (WER, ROUGE, satisfaction utilisateur) ?
42. Existe-t-il un corpus audio de référence pour valider les performances sur les langues/dialectes cibles ?
43. Quels scénarios de tests manuels doivent être documentés (perte de permission, interruption d'appel, batterie faible) ?
44. Souhaite-t-on intégrer des tests automatisés sur appareils réels ou émulateurs dans le pipeline interne ?
45. Quelles sont les étapes de validation produit avant lancement (beta interne, pilote restreint, go/no-go) ?

## Planning & gouvernance
46. Quelle est la date cible de MVP et quelles fonctionnalités sont indispensables pour cette échéance ?
47. Quel budget (temps, équipe, matériel) est alloué pour atteindre la version finale ?
48. Y a-t-il des dépendances externes (équipes sécurité, juridique, UX) qui doivent être intégrées au planning ?
49. Quels indicateurs de succès (adoption, temps gagné, NPS) doivent être mesurés post-lancement ?
50. Qui est le décideur final pour arbitrer les compromis qualité/temps/coût si des choix difficiles se présentent ?

## Annexes potentielles
51. Doit-on préparer une politique de confidentialité spécifique pour rassurer sur l'usage strictement local ?
52. Souhaite-t-on un mode "preuve" pour certifier que les données n'ont jamais quitté l'appareil (logs signés, hash) ?
53. Faut-il intégrer une fonctionnalité de partage local (AirDrop-like, USB) des transcriptions chiffrées ?
54. Prévoit-on des intégrations futures (Nextcloud offline, export vers outils métiers) et faut-il les anticiper dans l'architecture ?
55. Avons-nous besoin d'une stratégie de support utilisateur (FAQ, guide embarqué, assistance) utilisable hors-ligne ?

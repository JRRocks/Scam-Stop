# Passation — Chantier B, voix de l'annonce

Tenue par la session cloud du chantier B. Mise à jour : 2026-11-23, matin (UTC).

## Blocage en cours (à lire d'abord)

**`huggingface.co` est refusé par le réseau de la session cloud** (réponse 403 du proxy). Or
les deux seules voix françaises Piper dont toute la chaîne de licences est propre ne se
téléchargent que là :

- `fr_FR-siwis-medium` **réentraînée fin 2026** (base `hifi_tts_92`, CC BY 4.0) ;
- `fr_FR-mls-medium` (entraînée de zéro, CC BY 4.0).

À ouvrir dans l'environnement cloud de la session : `huggingface.co` et ses
hôtes de fichiers (`cas-bridge.xethub.hf.co`, `cdn-lfs.hf.co`, `cdn-lfs-us-1.hf.co`,
`cdn-lfs-eu-1.hf.co`). Sans cela, l'aperçu ne peut faire entendre que Kokoro (licence propre,
lourde et lente) et l'ancienne copie de siwis (même locutrice, licence douteuse, voir plus bas).

## Branche et commit

- Branche : `claude/stoic-carson-hkd4n6`, partie de `main` @ `32e8659`.
- État : **aperçu pas encore publié**. Aucun code d'interface, aucun texte de ressource, aucune
  icône n'a été touché. `versionCode` et `versionName` inchangés.

## Réalisé

1. **Banc d'écoute** (`tools/voice-bench/`, mode d'emploi dans son `README.md`) : synthèse par
   voix candidate, ligne téléphonique simulée (haut-parleur, pièce, bruit, micro, AMR-WB ou
   AMR-NB, stockage G.711 300–3400 Hz, AMR-NB, 8 kHz), transcription par Whisper, part des mots
   compris. Validé : il sépare nettement une voix robot d'une voix neuronale (tableau plus bas).
2. **Capture réelle de l'écran actuel** par test Robolectric
   (`app/src/test/java/com/scamstop/app/capture/`), pas encore poussée : elle attend la
   vérification complète `./gradlew test lint assembleDebug` avec ses dépendances de test.
3. **Traitement du son en Kotlin** (`GreetingAudio.kt`, pur, sans Android) : écrit, pas encore
   testé ni branché, donc pas encore poussé.
4. **Recherche Bouygues Telecom / B&You** et **recherche licences** : résultats ci-dessous.

## Mesures (partielles, 2026-11-22)

Part des mots compris par l'auditeur automatique Whisper `turbo`, six phrases d'annonce, voix
sans traitement. `tel-quel` : le son sorti de la voix. Les autres colonnes : après la ligne
simulée, de la plus douce à la plus dure.

| Voix | tel-quel | ligne | proche | courant | difficile |
|---|---:|---:|---:|---:|---:|
| Piper upmc « Jessica » | 97 % | 98 % | 96 % | 96 % | 67 % |
| Piper tom | 99 % | 99 % | 97 % | 96 % | 64 % |
| Piper siwis-medium (ancienne copie) | 98 % | 98 % | 97 % | 98 % | 52 % |
| eSpeak NG (voix robot de référence) | 93 % | 84 % | 79 % | 60 % | 10 % |

Jessica et eSpeak : cinq phrases sur six (machine redémarrée en cours de mesure, reprise en
cours). Restent à mesurer : Kokoro, les voix propres en licence, le traitement du son (pauses,
débit, coupe-bas, aigus, compression) et les versions allégées (fp16, int8).

Poids et vitesse relevés : bibliothèques natives arm64 27 Mo (10 Mo compressées) ; modèle
siwis-medium 63 Mo, 32 Mo en fp16, 19 Mo en int8 ; APK actuel 31 Mo. Sur Raspberry Pi 4
(documentation sherpa-onnx), une voix Piper `medium` calcule 1 s de parole en 0,36 à 0,77 s,
Kokoro en 3,2 à 7,6 s. Aucune mesure sur téléphone.

## Licences : ce qui change le choix des voix

Source : documentation du projet Piper lue le 2026-11-23, `OHF-Voice/piper1-gpl`,
`docs/LICENSING.md` et `docs/COMMERCIAL_USE.md`.

- La licence écrite sur la fiche d'une voix ne couvre que son propre jeu de données, pas la
  voix dont elle a été affinée. La base anglaise « lessac » vient d'un corpus dont la licence
  « limits use to "research and exploration of speech technologies" and excludes commercial use
  of the resulting synthesis ».
- Voix françaises dont toute la chaîne est permissive, selon Piper : `fr_FR-siwis-medium`
  **réentraînée** (base `hifi_tts_92`, CC BY 4.0, « Requires Piper 1.5 or higher ») et
  `fr_FR-mls-medium` (de zéro, CC BY 4.0, 125 locuteurs de livres audio).
- Hors liste : `upmc` (affinée de lessac), `siwis-low` et `gilles-low` (affinées de « ryan »,
  non commercial), `tom` (fiche « See URL », poids en AGPL-3.0 avec une clause d'anonymat de
  l'auteur en plus).
- **L'archive `vits-piper-fr_FR-siwis-medium` publiée par sherpa-onnx est l'ancienne copie**
  (sa fiche dit « Finetuned from U.S. English lessac voice ») : elle ne peut pas être livrée
  dans une appli GPL-3.0-or-later sans doute sur sa licence.
- Kokoro-82M (voix `ff_siwis`) : poids sous Apache-2.0, disponible sans Hugging Face.
- Moteur : sherpa-onnx 1.13.8 (Apache-2.0), dernière version qui embarque le phonétiseur
  espeak-ng (GPL-3.0-or-later) ; la 2.x l'a retiré. ONNX Runtime : MIT.

## Bouygues Telecom / B&You : installer l'annonce

Les pages d'aide de l'opérateur n'ont pas pu être ouvertes depuis la session (site hors de la
liste autorisée) : ce qui suit vient des extraits qu'en restitue un moteur de recherche.

Sourcé (officiel, par extraits) :
- Enregistrer : appeler le **660**, « Personnaliser votre répondeur » (touche 3), « Annonce
  d'accueil » (touche 1), encore 1, enregistrer, valider par # (page d'assistance
  `configurer-repondeur-mobile`).
- Revenir à l'annonce d'origine : 660, puis 3, 1, 6.
- Depuis un autre téléphone : composer son numéro, # pendant l'annonce, code à 4 chiffres, #.
- Messagerie vocale visuelle : incluse, dans l'appli Téléphone ; rien sur l'annonce.

Non trouvé : durée maximale de l'annonce ; tout dépôt de fichier pour le mobile grand public
(documenté seulement pour l'offre Entreprises) ; format de stockage.

Supposé : pas de voie numérique ; l'annonce passe par le micro pendant l'appel au 660.

## Décisions de l'utilisateur (ses mots)

- 2026-11-22 : « La voix auto n'est même pas humainement reconnaissable. » Mauvaise aux deux
  endroits : dans l'appli et, pire, une fois enregistrée sur la messagerie. Opérateur à traiter
  en premier : Bouygues Telecom / B&You.
- Aucune décision encore sur la voix, le poids ni l'écran : l'aperçu n'est pas publié.

## Choix du développeur

- Moteur embarqué visé : sherpa-onnx 1.13.8 et son paquet Android, récupérés à la compilation
  avec somme SHA-256, jamais versionnés (le modèle pèse 19 à 63 Mo).
- Le banc compte les mots compris par un auditeur automatique ; « on dirait une personne » se
  juge à l'oreille, sur la page d'aperçu.
- Captures d'écran : Robolectric 4.18.1 en SDK 35 (la CI compile en JDK 17), activité hôte
  déclarée par le test plutôt que par `ui-test-manifest`.

## Vérifications

- `./gradlew test lint assembleDebug` sur `main` @ `32e8659` dans la session : `BUILD
  SUCCESSFUL` (JDK 21, SDK Platform 36).
- `./gradlew :app:testDebugUnitTest --tests '*GreetingCaptureTest*'` : `BUILD SUCCESSFUL`, deux
  PNG de l'écran actuel (clair, sombre), relus à l'œil.
- Pas encore relancé sur l'ensemble des changements en attente.

## Essais à faire sur le téléphone

Aucun pour l'instant : rien n'est livré.

## Problèmes et dette

- La machine de la session a redémarré pendant les mesures : reprise en cours.
- La sous-recherche « licences » s'est arrêtée sur une limite d'usage ; ses constats ont été
  repris et vérifiés à la source pour Piper, pas pour les pages de `gnu.org` ni de DataShare.

## Idées pour plus tard

- Proposer d'enregistrer sa propre voix (non demandé).
- Héberger le modèle de voix dans une version GitHub du dépôt, pour ne pas dépendre d'un
  fichier tiers qui peut être remplacé.

## Prochaine étape

1. Obtenir l'accès à `huggingface.co`, télécharger et mesurer les deux voix propres.
2. Publier `docs/previews/voix-annonce.html`, les MP3 et le PNG, puis poser les questions.

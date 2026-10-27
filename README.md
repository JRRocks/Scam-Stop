# Scam Stop

Application Android qui **rejette tout appel vocal dont le numéro n'est pas dans vos
contacts**. Les SMS ne sont jamais touchés. Hors-ligne, aucune donnée collectée.

## Fonctionnement

- Scam Stop prend le rôle Android « Identification de l'appelant et spam »
  (`CallScreeningService`). Il ne demande **pas** la permission Contacts : sans elle,
  Android ne lui transmet que les appels de numéros inconnus, qui sont tous rejetés.
- L'appel est rejeté comme si vous aviez raccroché : l'opérateur bascule l'appelant
  sur votre messagerie vocale.
- **Pause minutée** (15 min, 1 h, 3 h ou jusqu'au lendemain 8:00) : le blocage reprend
  tout seul, sans risque d'oublier de le réactiver (attente d'un livreur, etc.).
- **Tuile Réglages rapides** : un appui met en pause 1 h ou reprend, un appui long
  ouvre les autres durées.
- **Appels rejetés** : la liste des numéros rejetés, avec Rappeler, Envoyer un SMS,
  Ajouter aux contacts ou Autoriser ce numéro. La notification (facultative) y mène.
- **Numéros autorisés** : des numéros qui sonnent normalement sans être dans vos
  contacts (« Livreure », un artisan…). Les formes `06…`, `+33 6…` et `0033 6…` d'un
  même numéro sont reconnues.
- Le journal et les numéros autorisés restent sur le téléphone, dans le stockage
  chiffré par votre code de déverrouillage : ni envoyés, ni sauvegardés dans le cloud.

## Annonce vocale (répondeur)

Une application Android ne peut pas injecter de son dans un appel. Le message parvient
donc à l'appelant via **l'annonce d'accueil de votre messagerie** :

1. Dans Scam Stop > Annonce vocale, ajustez le texte, **Écouter** pour vérifier, puis
   **Créer le fichier .wav** (fichier dans Téléchargements, voix de synthèse Google
   hors-ligne en français).
2. **Partager** envoie le `.wav` vers un PC ou une tablette.
3. **Ouvrir le clavier**, appui long sur 1, menu « annonce d'accueil » personnalisée.
4. Pendant l'enregistrement, faites jouer le `.wav` par l'autre appareil près du micro,
   puis **C'est fait**.

## Limites

- Android ne transmet pas les **numéros masqués / indisponibles** aux applications de
  filtrage. Pour les bloquer : application Téléphone > Paramètres > Numéros bloqués >
  Bloquer les appels inconnus/privés (l'app propose un raccourci quand le téléphone le
  permet).
- Le renvoi vers la messagerie dépend de l'opérateur (renvoi « si occupé »).

## Compiler

Interface en Jetpack Compose (Material 3), Android 10 (API 29) minimum.

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test lint assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`.

## Licence et origine

Dérivé de [Non-Contact Call Blocker](https://github.com/franciscogouveia/android-call-blocker)
© 2026 Francisco de Gouveia (commit `0331d1e`), sous licence
[GNU GPL](LICENSE) version 3 ou ultérieure (`GPL-3.0-or-later`).
Modifications Scam Stop : traduction française, rejet de l'appel (au lieu d'un simple
blocage) pour un renvoi immédiat vers la messagerie, générateur d'annonce vocale,
interface Compose, pause minutée et tuile, journal des appels rejetés, numéros
autorisés. Ces modifications sont distribuées sous la même licence.

Icônes : [Material Symbols](https://github.com/google/material-design-icons) de Google,
licence Apache 2.0.

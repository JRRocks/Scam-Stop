# Scam Stop

Application Android minimale qui **rejette tout appel vocal dont le numéro n'est pas dans
vos contacts**. Les SMS ne sont jamais touchés. Hors-ligne, aucune donnée collectée.

## Fonctionnement

- Scam Stop prend le rôle Android « Identification de l'appelant et spam »
  (`CallScreeningService`). Il ne demande **pas** la permission Contacts : sans elle,
  Android ne lui transmet que les appels de numéros inconnus, qui sont tous rejetés.
- L'appel est rejeté comme si vous aviez raccroché : l'opérateur bascule l'appelant
  sur votre messagerie vocale.
- Une notification indique chaque numéro rejeté (facultatif).
- Un interrupteur coupe le blocage sans retirer le rôle (attente d'un livreur, etc.).

## Annonce vocale (répondeur)

Une application Android ne peut pas injecter de son dans un appel. Le message parvient
donc à l'appelant via **l'annonce d'accueil de votre messagerie** :

1. Dans Scam Stop, ajustez le texte, **Écouter** pour vérifier, puis
   **Enregistrer en .wav** (fichier dans Téléchargements, voix de synthèse Google
   hors-ligne en français).
2. Appelez votre messagerie (appui long sur 1), menu « annonce d'accueil » personnalisée.
3. Pendant l'enregistrement, faites lire le `.wav` par un autre appareil près du micro.

## Limites

- Android ne transmet pas les **numéros masqués / indisponibles** aux applications de
  filtrage. Pour les bloquer : application Téléphone > Paramètres > Bloquer des numéros >
  Bloquer les appels inconnus/privés.
- Le renvoi vers la messagerie dépend de l'opérateur (renvoi « si occupé »).

## Compiler

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test lint assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`.

## Licence et origine

Dérivé de [Non-Contact Call Blocker](https://github.com/franciscogouveia/android-call-blocker)
© 2026 Francisco de Gouveia (commit `0331d1e`), sous licence
[GNU GPL](LICENSE) version 3 ou ultérieure (`GPL-3.0-or-later`).
Modifications Scam Stop : traduction française, rejet de l'appel (au lieu d'un simple
blocage) pour un renvoi immédiat vers la messagerie, générateur d'annonce vocale.
Ces modifications sont distribuées sous la même licence.

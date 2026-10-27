# Scam Stop

Rejette les appels vocaux hors contacts (`CallScreeningService`), SMS non touchés,
générateur d'annonce vocale pour la messagerie. Base : franciscogouveia/android-call-blocker
(GPL-3.0-or-later) — garder `LICENSE` et la mention d'origine du `README.md`.
Ne jamais ajouter `READ_CONTACTS` : c'est son absence qui fait qu'Android ne transmet que
les appels inconnus au service.

Compiler / vérifier sur le PC Windows (`java` n'est pas dans le PATH) :

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test lint assembleDebug
```

En session cloud (Linux) : JDK 17+ et Android SDK Platform 36 requis (`ANDROID_HOME` ou
`local.properties` non versionné avec `sdk.dir=...`), puis `./gradlew test lint assembleDebug`.
Pas de lecteur `G:` dans le cloud : les étapes APK et miroir Drive ci-dessous se font au
retour sur le PC ; dans le cloud, livrer par commit sur une branche.

# Règles de livraison — Scam Stop

## Après chaque changement livré (sans demander)
1. **Vérifs** : `./gradlew test lint` avant de livrer.
2. **Version** : incrémenter `versionName` et `versionCode` (`app/build.gradle.kts`) à chaque
   dépôt, pour qu'un APK n'en écrase jamais un autre.
3. **APK** : le déposer dans
   `G:\My Drive\2 - Jean\Claude Code\APK release\ScamStop.<versionName>.apk`.
   Ce dossier est commun à tous les projets. Jamais d'APK dans le dossier miroir.
4. **Miroir Drive** (sens unique local → Drive) :
   robocopy "C:\Users\Taiepii\Desktop\Claude Code\Scam Stop" "G:\My Drive\2 - Jean\Claude Code\Scam Stop" /MIR /XD .git .gradle .kotlin build .idea node_modules __pycache__ .pytest_cache .venv venv /XF .env local.properties keystore.properties *.apk /R:1 /W:1
   Les codes de sortie 0 à 7 veulent dire succès. `/MIR` supprime côté Drive : vérifier le chemin de destination.
   La racine peut être `My Drive` ou `Mon Drive`.
5. **Message final** : indiquer la version déposée, le chemin de l'APK et le résultat du resync.

## Git
- Remote : `origin` = https://github.com/JRRocks/Scam-Stop, branche `main`.
- Commit après chaque changement livré et vérifié, avec un message court et clair en anglais.
- **Push sur `origin main` seulement si je le demande.** Pas de force-push, pas de `--no-verify`.
- Ne jamais commiter de secrets (`.env`, `local.properties`, `keystore.properties`, clés API) ni les APK.

## Rappels
- Règles Drive globales : `G:\My Drive\2 - Jean\Claude Code\CLAUDE.md`.

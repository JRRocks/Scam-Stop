# Passation — chantier A « Laisser passer les vrais appelants »

État au 2026-11-23. Ce fichier se supprime une fois le chantier intégré.

## Où en est-on

- **Aperçu montré, « oui » pas encore reçu.** Aucun écran, aucun texte de ressource, aucune icône n'est codé.
- **Mécanique invisible faite et testée** : D1, D2 et D3 décident déjà dans le service. Les appels qu'une
  exception laisse sonner sont notés, mais les écrans actuels ne les montrent pas tant que le journal
  n'est pas validé.
- Branche `claude/practical-heisenberg-byt0jy`, partie de `main` @ `32e8659`. Version non touchée.

| Commit | Contenu |
|---|---|
| `0dc9dae`, `da27bb4` | Aperçu : `docs/previews/vrais-appelants.html`, cinq images, `vrais-appelants.md` |
| `b8e5abf` | Règles de sauvegarde : toutes les zones de stockage exclues |
| `208b18f` | Logique D2 et D3, stockage, service, tests, diagnostic réservé au debug |
| `e1537fb` | Le service répond même si le pays du réseau ne peut pas être lu |
| `6c54aa0` | Test du vrai service sous Robolectric ; dépendances de test dans `app/build.gradle.kts` |
| `68781da` | Rendu des écrans actuels en images par un test (`app/build/captures/`, non versionné) |

## L'aperçu et les quatre questions

Lien à ouvrir sur téléphone : `docs/previews/vrais-appelants.md` (GitHub l'affiche avec ses cinq images).
Page d'origine : `docs/previews/vrais-appelants.html` (statique, sans script, claire et sombre, vérifiée à 400 px).
Les écrans « Aujourd'hui » sont reconstruits d'après le code : pas d'émulateur dans la session.

| Question | Recommandation | Autres variantes |
|---|---|---|
| 1. L'accueil : où D2 et D3 se voient, s'ils se coupent | **A** : un groupe « Laisser sonner » avec deux interrupteurs allumés d'office ; « Numéros autorisés » rejoint ce groupe ; le bandeau « Protection active » change de texte selon les interrupteurs | B : même groupe, sans interrupteur · C : seul le bandeau change |
| 2. Le journal | **A** : une seule liste « Journal des appels », chaque appel qui a sonné dit pourquoi | B : deux listes séparées |
| 3. Les numéros appelés | **A** : visibles dans « Numéros autorisés », avec « Retirer » et « Toujours autoriser » | B : pas montrés |
| 4. L'annonce et le SMS | **A** : dire « Rappelez tout de suite : votre deuxième appel sonnera » | B : texte inchangé · C : inviter à rappeler sans promettre |

Réponse attendue : « 1 A, 2 A, 3 A, 4 A », ou « oui à tout ». Une question laissée sans réponse prend la recommandation.
Aucune nouvelle autorisation Android n'est nécessaire (preuve plus bas), donc pas de question là-dessus.

## Décisions de l'utilisateur

- Le problème, avec ses mots : « Mon application est trop brute sur le refus des numéros […]. Mais j'ai besoin
  qu'il agisse ainsi car les vrais fraudeurs. »
- D1 à D4 du 2026-11-22 (`docs/DECISIONS.md` sur `main`) : premier appel rejeté d'un coup ; « Ceux que j'ai
  appelés » (30 jours) ; « Le 2e appel passe » (5 minutes, jamais un numéro étranger ni signalé usurpé) ; pas de
  grande notification avec un bouton « Rappeler ».
- Questions 1 à 4 : pas encore de réponse.

## Choix du développeur (révisables sans débat)

Règle de D2, « Ceux que j'ai appelés » :

- Le numéro est retenu au moment où l'appel est composé, même sans réponse, s'il a au moins trois chiffres.
- Il sonne pendant 30 jours pleins après le dernier appel vers lui, bornes comprises. Le rappeler repart pour 30 jours.
- Aucune exclusion par type : étranger, 08 ou numéro court, c'est l'utilisateur qui l'a appelé.
- Sauf si le réseau signale l'appel entrant comme usurpé : il reste rejeté. Cette ligne est écrite dans l'aperçu.

Règle de D3, « Le 2e appel passe » :

- Les 5 minutes partent du rejet, bornes comprises. Tout appel du même numéro dans ces 5 minutes sonne (2e, 3e…).
  Après, le prochain appel est rejeté et ouvre 5 nouvelles minutes.
- Seulement pour un numéro « ordinaire » du plan français : fixe (01 à 05), mobile (06, 07), box (09).
- « Français » veut dire plan de numérotation de l'Arcep : `+33`, et l'outre-mer qui le partage, `+590`, `+594`,
  `+596`, `+262`, `+508`. Nouvelle-Calédonie, Polynésie française et Wallis-et-Futuna ont leur propre plan :
  traités comme étrangers.
- Exclus : numéros en 08, numéros courts, et les tranches que les automates d'appel doivent utiliser
  (01 62, 01 63, 02 70, 02 71, 03 77, 03 78, 04 24, 04 25, 05 68, 05 69, 09 48, 09 49, et 09 475 à 09 479 outre-mer).
- Un appel signalé usurpé ne passe jamais, et prive ce numéro de 2e appel pendant 5 minutes.
- Un numéro sans indicatif (06…) ne compte comme français que sur un réseau français. À l'étranger, c'est un
  numéro local du pays : pas de 2e appel.

Reconnaissance du « même numéro » :

- Les formes `06…`, `+33 6…`, `0033 6…`, `+33 (0)6…` d'un numéro français sont ramenées à ses neuf chiffres nationaux.
- Un numéro français n'est jamais pris pour un numéro étranger qui finit par les mêmes chiffres, et deux numéros
  étrangers se comparent sur tous leurs chiffres. Sans cela, `+34 969 12 34 56` passait pour le `09 69 12 34 56`
  que l'utilisateur a appelé.
- Hors de ces cas, la règle déjà en place (`PhoneNumbers.same`) sert telle quelle. Les « Numéros autorisés »
  gardent leur règle d'origine.

Données :

- Numéros appelés et rejets récents vivent dans le même fichier de préférences que le journal (stockage protégé
  par le code de déverrouillage). 200 numéros appelés et 50 rejets au plus.
- Ils s'effacent quand le processus démarre et à chaque appel noté. Entre deux, une entrée périmée ne compte
  déjà plus : la décision vérifie la date. Une entrée datée du futur (horloge fausse) est gardée et ne compte pas.
- Les deux interrupteurs existent déjà dans le stockage, allumés par défaut. En éteindre un efface ce qu'il
  retenait. Sans écran pour l'instant.

Service :

- La réponse à Android part avant toute écriture. Un test vérifie cet ordre.
- Le journal garde une seule liste, avec la raison de chaque appel qui a sonné. Les lignes de rejet gardent le
  format des versions précédentes.

Diagnostic réservé au debug :

- Dans les compilations debug seulement (dossier `app/src/debug`, version vide dans `app/src/release`) : une ligne
  « Diagnostic du filtrage » en bas de l'accueil ouvre les 40 derniers appels transmis par Android au service
  (sens, forme du numéro, vérification du réseau, décision, délai de réponse) et permet de copier le rapport.
- Les numéros y sont masqués avant d'être enregistrés : début et deux derniers chiffres seulement.

Hors du périmètre annoncé, à connaître pour l'intégration :

- `app/src/main/res/xml/data_extraction_rules.xml` (commit `b8e5abf`, isolé pour pouvoir être retiré). Constat :
  Android parcourt chaque zone de stockage à part et compare les chemins exclus un à un
  (`BackupAgent.fullBackupFileTree`). Exclure `root` seul laissait donc partir les préférences (journal, numéros
  autorisés, réglages) dans un transfert d'appareil à appareil, qui ignore `allowBackup="false"` pour une appli
  visant Android 12 ou plus (`BackupEligibilityRules.isAppBackupAllowed`). Le fichier nomme maintenant toutes les
  zones. Conséquence : sur un nouveau téléphone, l'appli repart vide. À retirer si l'utilisateur préfère que ses
  numéros autorisés suivent.
- Nouveaux dossiers : `app/src/debug`, `app/src/release`, `app/src/testDebug`.
- `ui/HomeScreen.kt` et `ui/RejectedCallsScreen.kt` : un filtre invisible (seuls les rejets s'affichent) et
  l'appel du diagnostic debug. `strings.xml` et le manifeste ne sont pas touchés.
- `app/build.gradle.kts` : un bloc à part pour Robolectric 4.16, `androidx.test:core` 1.7.0, `ui-test-junit4` et
  `ui-test-manifest` (debug), et `unitTests.isIncludeAndroidResources`. L'autre chantier peut ajouter les mêmes lignes : n'en garder qu'une copie.
  Au premier lancement des tests, Robolectric télécharge une archive d'Android d'environ 200 Mo depuis Maven
  Central. Dans la session cloud, Maven Central a répondu 429 : l'archive a été posée à la main dans `~/.m2`
  depuis le miroir de Google (fiche `android-en-session-cloud` de la Library).

## Preuves

D2 fonctionne sans nouvelle autorisation :

- Documentation Android, lue directement.
  [CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService) :
  « The framework binds to the CallScreeningService implemented by the user-chosen app filling the
  RoleManager#ROLE_CALL_SCREENING role when incoming calls are received (prior to ringing) and when outgoing calls
  are placed. » Et : « only calls which are not in the user's contacts are passed for screening, unless the
  CallScreeningService has been granted Manifest.permission#READ_CONTACTS permission by the user. For outgoing
  calls, no post-dial digits are passed. »
  [Guide](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls) : « Call the
  onScreenCall() function for any new incoming or outgoing calls when the number is not in the user's contact list. »
- Code d'Android. `platform/packages/services/Telecomm`,
  `src/com/android/server/telecom/CallsManager.java`, méthode `startOutgoingCall` : pour un numéro `tel:`, Telecom
  cherche le numéro dans les contacts puis exécute
  `if ((!isInContacts) || (permission == PackageManager.PERMISSION_GRANTED)) { bindForOutgoingCallerId(theCall); }`.
  Sous Android 10 : `if (!isInContacts)`. `bindForOutgoingCallerId` lie l'appli du rôle par
  `CallScreeningServiceHelper`, qui appelle `screenCall` avec `ParcelableCallUtils.toParcelableCallForScreening` :
  sens `DIRECTION_OUTGOING` et numéro composé. Côté appli, `frameworks/base`,
  `telecomm/java/android/telecom/CallScreeningService.java` appelle `onScreenCall` puis répond lui-même pour un
  appel sortant.
- Tests de compatibilité d'Android (CTS), `ScreeningServiceNoContactsPermissionTest` :
  `testNoPermissionAndNoContactOutgoing` exige que le service soit appelé ; `testNoPermissionAndHasContactOutgoing`
  exige qu'il ne le soit pas pour un contact.
- Limite de cette preuve : `android.googlesource.com` est refusé par le réseau de la session cloud. Le code a
  été lu sur des miroirs GitHub : `aosp-mirror/platform_frameworks_base` (miroir de Google) et, pour Telecomm et
  CTS, les dépôts LineageOS, CalyxOS et GrapheneOS, qui portent les commits et les étiquettes d'AOSP d'Android 10
  à la série 2026. Le même passage a été relu sur cinq branches. Aucun essai sur téléphone : un fabricant peut
  avoir modifié Telecom.

Exclusions de D3 :

- `www.arcep.fr` est lui aussi refusé par le réseau de la session. Les pages de l'Arcep ont été lues par le
  moteur de recherche de la session (extraits et résumés), pas en lecture directe.
- Confirmé par deux pages de l'Arcep qui se recoupent : les douze tranches métropolitaines des « numéros
  polyvalents vérifiés », l'obligation pour les automates d'appel depuis le 1er janvier 2023, les numéros mobiles
  réservés aux communications entre personnes, les tranches outre-mer 09 475 à 09 479, les indicatifs et préfixes
  d'outre-mer, le format `+508` à six chiffres de Saint-Pierre-et-Miquelon.
  Pages : [fiche pour les professionnels](https://www.arcep.fr/mes-demarches-et-services/entreprises/fiches-pratiques/plan-numerotation-professionnels.html),
  [dossier La numérotation](https://www.arcep.fr/la-regulation/grands-dossiers-thematiques-transverses/la-numerotation.html),
  [décision 2022-1583](https://www.arcep.fr/uploads/tx_gsavis/22-1583.pdf),
  [décision 2025-2215](https://www.arcep.fr/uploads/tx_gsavis/25-2215.pdf).
- **À confirmer en lecture directe depuis le PC** : la décision 2025-2215, en vigueur au 1er janvier 2026, donne
  100 000 numéros polyvalents vérifiés de plus à chaque territoire d'outre-mer. Ces nouvelles tranches ne sont pas
  dans la liste du code (`FrenchNumbers.AUTOMATED_RANGES`, une seule constante à compléter). Effet tant qu'elles
  manquent : un automate qui appellerait depuis l'une d'elles et rappellerait dans les 5 minutes sonnerait.
- Numéros en 08 et courts : le plan les classe à part des numéros territorialisés (« numéros spéciaux et
  courts »). L'Arcep interdit depuis 2012 d'afficher un numéro en 089 comme numéro d'appelant (décision
  2012-0856, contre les appels qui poussent à rappeler un numéro surtaxé). Le texte exact n'a pas été lu.

## Vérifications

| Commande ou contrôle | Résultat |
|---|---|
| `./gradlew test lint assembleDebug` sur `main` avant tout changement (JDK 21, SDK 36) | `BUILD SUCCESSFUL` |
| `./gradlew test lint assembleDebug` sur `68781da` | `BUILD SUCCESSFUL` ; 102 tests en debug, 99 en release, 0 échec ; lint : 0 erreur, 5 avertissements de version de dépendance (4 présents avant, 1 pour Robolectric) |
| GitHub Actions (workflow Android) sur `e1537fb` puis `dc4a47d` | runs n° 7 et n° 8 : réussis (tests, lint, compilation debug ; le n° 8 exécute le test du vrai service) |
| Écrans « Aujourd'hui » de l'aperçu comparés au rendu réel (`CurrentScreensCaptureTest`, 360 dp, français) | mêmes textes, mêmes icônes, même ordre. Écarts : lignes un peu plus hautes dans l'appli ; « Me prévenir à chaque appel rejeté » y tient sur une ligne ; la version debug ajoute la ligne de diagnostic en bas de l'accueil |
| Aperçu rendu par Chromium à 400 px et 1000 px, clair et sombre | aucun débordement horizontal, aucune balise de script |

- Un test a trouvé un vrai défaut pendant l'écriture (le chiffre 6 des mobiles manquait dans la liste des numéros
  ordinaires). Corrigé dans le code, pas dans le test.
- Cas couverts par les tests : bornes de 5 minutes et de 30 jours à la milliseconde, formes d'un même numéro,
  numéro absent, usurpé (au 1er ou au 2e appel), étranger, outre-mer, automates, 08 et courts, numéro sans
  indicatif à l'étranger, numéro ressemblant, pause, protection coupée, numéro autorisé, appel sortant, 3e appel,
  appel après la fenêtre, mémoire relue après un redémarrage, horloge reculée, mémoire pleine, ordre « répondre
  puis écrire », formats de stockage des versions précédentes.
- Le vrai service et le vrai stockage sont exécutés par `ScreeningServiceTest` sous Robolectric (Android 15
  simulé) : les appels lui arrivent par son interface de liaison, comme Telecom les envoie. Cinq cas : rejet puis
  2e appel qui sonne, numéro composé qui sonne après un redémarrage, étranger, usurpé et masqué rejetés à chaque
  fois, pause sans trace, exception éteinte qui efface sa mémoire.
- **Pas essayé** : un vrai téléphone, de vrais appels, le vrai Telecom d'un fabricant.

- La compilation GitHub du dernier commit de la branche se lit dans l'onglet Actions du dépôt. Le commit qui
  ajoute ce fichier ne touche à aucun code.

## Essais à faire sur le téléphone

À faire une fois l'APK livré par le PC. Il faut un second téléphone dont le numéro n'est pas dans les contacts.

1. **Android transmet-il les appels sortants ?** Appeler ce second téléphone, raccrocher. Ouvrir Scam Stop, tout
   en bas « Diagnostic du filtrage » : une ligne « sortant · retenu pour 30 jours » doit être apparue. Si rien
   n'apparaît, D2 ne peut pas fonctionner sur ce téléphone sans lire le journal d'appels : le dire avant d'aller
   plus loin.
2. **D2.** Depuis le second téléphone, rappeler : ça doit sonner dès le premier appel.
3. **D3.** Depuis un téléphone jamais appelé et hors contacts : premier appel rejeté ; rappeler dans les
   5 minutes : ça sonne ; attendre plus de 5 minutes et rappeler : rejeté.
4. **Rien ne change.** Protection en pause : ça sonne, rien dans le journal. Numéro autorisé : ça sonne.
5. **Redémarrage.** Appeler le second téléphone, redémarrer, déverrouiller, se faire rappeler : ça sonne.
6. **Délai.** Dans le diagnostic, relever « réponse en … ms » d'un appel entrant : Android accorde 5 000 ms.
7. **Numéro masqué.** Un appel en numéro masqué sonne-t-il, est-il rejeté, apparaît-il dans le diagnostic ?
   Le code d'Android laisse penser qu'il est transmis sans numéro, alors que sa documentation dit le contraire.

## Problèmes et dette

- **Android 10.** Dans le code d'Android 10 (`CallScreeningServiceController.bindCarrierService`), quand
  l'opérateur n'a pas d'appli de filtrage, le service reçoit aussi les appels des contacts : Scam Stop les
  rejetterait. L'appli accepte Android 10 (`minSdk = 29`). Antérieur à ce chantier. À trancher : exiger Android 11,
  ou prévenir dans l'appli.
- **Texte de l'annonce.** L'écran « Annonce vocale » enregistre le texte affiché dès sa première ouverture.
  Changer `default_greeting` ne change donc rien chez quelqu'un qui a déjà ouvert cet écran. Pour les
  variantes 4 A et 4 C, soit le chantier de la voix remplace le texte enregistré quand il est identique à l'ancien
  texte par défaut, soit l'utilisateur colle le nouveau. Ces fichiers ne sont pas à ce chantier.
- **Annonce et interrupteur.** Si l'annonce promet que le 2e appel sonne et que l'utilisateur éteint
  « Le 2e appel passe », la promesse devient fausse. Rien ne le signale.
- **Numéros autorisés.** Leur comparaison par la fin du numéro a le défaut corrigé ici pour D2 : un numéro
  étranger qui finit comme un numéro autorisé passe. Antérieur, non touché.
- **Rappeler un inconnu l'autorise.** Rappeler un numéro rejeté depuis le journal le range parmi « ceux que j'ai
  appelés » pour 30 jours. C'est D2 ; la variante 3 A permet de le retirer.
- **Effacement.** Pas de minuterie : une entrée périmée est effacée au prochain appel traité ou au prochain
  démarrage de l'appli.
- **Réseau des sessions cloud.** `www.arcep.fr` et `android.googlesource.com` sont refusés par l'environnement.
- Le premier commit d'aperçu (`0dc9dae`) nomme un modèle dans sa ligne de co-auteur ; les suivants non.

## Idées pour plus tard (non construites)

- Un numéro dont le 2e appel a sonné pourrait rester autorisé jusqu'au soir.
- Appliquer aux « Numéros autorisés » la comparaison stricte des numéros.
- Un environnement cloud propre à Scam Stop, avec le SDK Android déjà installé et les sites officiels autorisés.

## Prochaine étape

1. Recevoir les réponses aux questions 1 à 4, de l'utilisateur ou relayées avec ses mots.
2. Coder fidèlement l'aperçu validé : accueil, journal, numéros autorisés, textes ; trois icônes Material Symbols
   (`phone_callback`, `repeat`, `phone_in_talk`, licence Apache 2.0).
3. Montrer le rendu réel à côté de l'aperçu, avec l'outil de capture déjà en place
   (`app/src/testDebug/.../capture/`), puis mettre à jour le `README.md` (Fonctionnement, Limites).
4. Compléter cette passation, pousser.

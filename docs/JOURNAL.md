# Journal — Scam Stop

Les entrées les plus récentes sont en haut. Une entrée ne se réécrit pas : une erreur se corrige par une
nouvelle entrée.

## 2026-11-22 · Cadrage : vrais appelants et voix de l'annonce

- Branche et commit : `main` (PC). Mémoire du projet seulement, aucun code touché.
- Objectif : ne plus perdre l'appel d'un vrai appelant inconnu sans rouvrir la porte aux fraudeurs ;
  rendre l'annonce vocale naturelle et compréhensible.
- Réalisé : quatre questions de cadrage ; création de `docs/` (journal, décisions, plan) ; deux sessions
  cloud demandées depuis `main`, « vrais appelants » et « voix de l'annonce » (démarrage non encore
  confirmé à l'heure de cette entrée).
- Décisions : D1 à D9 (`DECISIONS.md`).
- Vérifications : dernière compilation GitHub de `main` réussie (2026-10-28). Aucune compilation
  aujourd'hui : pas de code touché. Contrôle « rien de privé » passé avant le commit ; les commits du PC
  portent désormais une identité `noreply`.
- Non vérifié sur téléphone : rien de nouveau.
- Problèmes et dette : les secrets de signature ne sont pas posés sur GitHub, donc un APK compilé par
  GitHub ne s'installe pas par-dessus l'appli ; l'APK à installer se compile sur le PC. Une session cloud
  ne prévient pas la session du PC quand elle s'arrête : son état se lit sur sa branche
  (`docs/passation-*.md`, `docs/previews/`).
- Prochaine étape : montrer à l'utilisateur les aperçus des deux sessions, relayer son « oui », puis
  intégrer (plan, partie C).

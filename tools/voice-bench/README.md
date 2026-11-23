# Banc d'écoute de l'annonce vocale

De quoi **entendre et mesurer** une voix avant de la mettre dans l'appli : le banc lit des
phrases d'annonce avec chaque voix candidate, les abîme comme le fait une ligne téléphonique,
puis fait transcrire le résultat par un auditeur automatique et compte les mots compris.

Il ne sert qu'au développement : rien de ce dossier n'entre dans l'APK.

## Ce qu'il fait

1. **Synthèse** (`voicebench/synth.py`) : voix Piper et Kokoro par sherpa-onnx 1.13.8, plus deux
   voix « robot » de référence (eSpeak NG, SVOX Pico).
2. **Préparation** (`voicebench/dsp.py`) : le traitement appliqué avant la ligne (pauses, débit,
   coupe-bas, aigus, compression, niveau). C'est le prototype de ce que fait l'appli en Kotlin.
3. **Ligne simulée** (`voicebench/channel.py`) : haut-parleur d'un autre appareil, pièce
   (réverbération), bruit ambiant, micro du téléphone, codec de l'appel montant (AMR-WB ou
   AMR-NB), stockage de la messagerie (G.711 loi A, bande 300 à 3400 Hz), codec de l'appel
   descendant (AMR-NB), 8 kHz. Quatre conditions nommées :

   | Clé | Ce qu'elle représente |
   |---|---|
   | `ligne` | le fichier arriverait intact sur la messagerie (dépôt numérique idéal) |
   | `proche` | haut-parleur à 6 cm du micro, pièce calme |
   | `courant` | haut-parleur à 25 cm, pièce ordinaire |
   | `difficile` | petit haut-parleur poussé à fond, à 60 cm, pièce bruyante, mauvais réseau |

4. **Écoute** (`voicebench/listen.py`) : transcription par Whisper (`turbo`, et `small` comme
   auditeur moins tolérant), comparée au texte lu. Le chiffre donné est la part des mots compris.

## Ce qu'il ne reproduit pas

- **La voix réelle d'un téléphone** : les moteurs de synthèse d'Android (Google, Samsung…) ne
  tournent pas hors d'un téléphone. Les deux voix « robot » sont des repères, pas ces moteurs.
- **La vraie messagerie de l'opérateur** : format de stockage, traitements et niveau réels
  inconnus. Le stockage G.711 est une hypothèse courante, pas une donnée de l'opérateur.
- **Le traitement de la voix par le téléphone pendant l'appel** (réduction de bruit, annulation
  d'écho, gain automatique) : propre à chaque modèle, et souvent sévère avec un son qui ne vient
  pas d'une bouche proche du micro.
- **Le vrai haut-parleur, la vraie pièce, le téléphone de l'appelant et son oreille.**
- **Le jugement « on dirait une personne »** : un auditeur automatique dit si les mots passent,
  pas si la voix paraît humaine. Ce jugement-là se fait à l'oreille, sur la page d'aperçu.

Tous les réglages de la simulation sont des hypothèses écrites dans `voicebench/channel.py`.
La synthèse tire un peu au hasard à chaque lecture : d'un passage à l'autre, les chiffres
bougent de quelques points.

## Lancer

Linux, Python 3.11 ou plus récent, `ffmpeg` avec les encodeurs AMR (sous Ubuntu :
`apt-get install ffmpeg libavcodec-extra espeak-ng libttspico-utils`).

```bash
cd tools/voice-bench
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
./fetch_models.sh
python run_bench.py --models .cache/models --out out --voices siwis-medium,tom-medium,espeak
python report.py out/rows.jsonl
```

`fetch_models.sh` télécharge environ 1,7 Go depuis les versions publiées de sherpa-onnx sur
GitHub et vérifie chaque archive par sa somme SHA-256. Compter une à deux minutes de calcul par
voix et par phrase sur quatre cœurs.

- `--variants` : `brut` (la voix telle qu'elle sort), `traite` (préparée pour la ligne),
  `traite-lent`… la liste est dans `run_bench.py`.
- `texts.txt` : les phrases lues. La première ligne, `@default_greeting`, reprend le texte par
  défaut de l'appli dans `app/src/main/res/values/strings.xml`.
- Les sons de la première phrase sont gardés dans `out/samples/`, tels quels et après la ligne.

## Licences des outils du banc

sherpa-onnx (Apache-2.0), Whisper (MIT), pyroomacoustics (MIT), NumPy et SciPy (BSD),
eSpeak NG (GPL-3.0-or-later), SVOX Pico (Apache-2.0). Les voix et leurs licences sont décrites
dans le `README.md` du projet.

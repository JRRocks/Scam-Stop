#!/usr/bin/env bash
# Downloads the voices and the automatic listener into .cache/models (not versioned).
# Every archive is checked against a pinned SHA-256: a re-uploaded file stops the script.
#
#   ./fetch_models.sh            # voices compared on the preview page + Whisper turbo and small
#   ./fetch_models.sh shipped    # only the voice built into the app + Whisper turbo
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p .cache/archives .cache/models
base=https://github.com/k2-fsa/sherpa-onnx/releases/download

fetch() { # <release tag> <archive> <sha256>
  local archive=".cache/archives/$2"
  if [ ! -s "$archive" ]; then
    curl -fsSL --retry 3 -o "$archive.part" "$base/$1/$2"
    mv "$archive.part" "$archive"
  fi
  echo "$3  $archive" | sha256sum --check --quiet
  tar xjf "$archive" -C .cache/models
  echo "ok $2"
}

fetch tts-models vits-piper-fr_FR-siwis-medium.tar.bz2 375909aa30842b3a4efa10b1beb1d761af792960ae6873b4d53889f96c66195b
fetch asr-models sherpa-onnx-whisper-turbo.tar.bz2 b11acbbcd660b44a8e0df33724feb5aaa709cf65668f2823d59f656312544f22
[ "${1:-all}" = "shipped" ] && exit 0

fetch asr-models sherpa-onnx-whisper-small.tar.bz2 486a46afbb7ba798507190ffe02fea2dd726049af212e774537efac6afb210a6
fetch tts-models vits-piper-fr_FR-siwis-medium-fp16.tar.bz2 d682e8e80019cfc5e9c9e2cefdf9de4a750e1bd56006d9dd6e68e1c2bb7e22fe
fetch tts-models vits-piper-fr_FR-siwis-medium-int8.tar.bz2 3909cff9b3cfd4820c66aa13bf554315c82e34899c161f0b446ece372bc4b5ec
fetch tts-models vits-piper-fr_FR-siwis-low.tar.bz2 3d69170c160c8375c4123901a72a3845222b39456d39ab74f5bbd7310952b5af
fetch tts-models vits-piper-fr_FR-upmc-medium.tar.bz2 e9830a331a16f6cc5ef3116a287065e015d3495c3f56b974889a266da7f89a7f
fetch tts-models vits-piper-fr_FR-tom-medium.tar.bz2 3d8258ef8466b5d5f2cc46c06e1415d3d01c9cbaeed8b6d5b3d152073672472c
fetch tts-models vits-piper-fr_FR-tom-medium-fp16.tar.bz2 787bd2aeab3ab46a1617a6b3940f3eb60aebd26a0060aa14a0022c35b1ad0ec7
fetch tts-models vits-piper-fr_FR-tom-medium-int8.tar.bz2 b158f884eb4231b8dddd372c3dc506949630c992a8e48ca692a90323e8bb4e09
fetch tts-models vits-piper-fr_FR-gilles-low.tar.bz2 9f4fdd2650f52638e5d890d330b4ee3a30fc6bb91d6ae6f37e639d0f474bc521
fetch tts-models kokoro-multi-lang-v1_0.tar.bz2 60703707bc05ccd48d60e1eb723f38b07d6e474795a0b6a588d7220400e0f7a4

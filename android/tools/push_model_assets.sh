#!/bin/bash
# Stage the exported ONNX model + its reference into the app's assets.
#
# Model binaries are GITIGNORED (root CLAUDE.md) — a fresh clone has none. Export them
# first:  ml/.venv/bin/python ml/src/training/run_dl.py
#
# The assets directory is gitignored too, so this never puts a model binary in the repo.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SPEC_VERSION="$(grep -oE 'SPEC_VERSION: (v[0-9]+\.[0-9]+\.[0-9]+)' "$REPO/docs/feature-spec.md" | head -1 | awk '{print $2}')"
MODEL="$REPO/ml/data/processed/temporal_cnnlstm_${SPEC_VERSION}.onnx"
REFERENCE="$REPO/fixtures/model_reference.json"
ASSETS="$REPO/android/app/src/main/assets"

if [[ ! -f "$MODEL" ]]; then
  echo "ERROR: no model for $SPEC_VERSION at $MODEL" >&2
  echo "       run: ml/.venv/bin/python ml/src/training/run_dl.py" >&2
  exit 1
fi
if [[ ! -f "$REFERENCE" ]]; then
  echo "ERROR: $REFERENCE missing — the same export writes it." >&2
  exit 1
fi

mkdir -p "$ASSETS"
cp "$MODEL" "$ASSETS/stress_model.onnx"
cp "$REFERENCE" "$ASSETS/model_reference.json"

echo "staged for $SPEC_VERSION:"
echo "  $(basename "$MODEL")  ->  assets/stress_model.onnx"
echo "  model_reference.json  ->  assets/model_reference.json"
echo
echo "run the on-device parity test with:"
echo "  cd android && ./gradlew :app:connectedDebugAndroidTest"

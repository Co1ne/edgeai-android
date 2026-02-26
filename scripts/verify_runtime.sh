#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

FILES=(
  "$ROOT_DIR/runtime/models/asr/ggml-base.bin"
  "$ROOT_DIR/runtime/models/llm/model.gguf"
  "$ROOT_DIR/runtime/models/_manifest/models.json"
)

for f in "${FILES[@]}"; do
  if [[ ! -f "$f" ]]; then
    echo "MISSING: $f"
    exit 1
  fi
  size=$(stat -c%s "$f")
  if [[ "$size" -le 0 ]]; then
    echo "INVALID SIZE: $f"
    exit 1
  fi
  echo "OK: $f ($size bytes)"
done

if [[ -f "$ROOT_DIR/runtime/RELEASE_NOTES.txt" ]]; then
  size=$(stat -c%s "$ROOT_DIR/runtime/RELEASE_NOTES.txt")
  echo "OK: $ROOT_DIR/runtime/RELEASE_NOTES.txt ($size bytes)"
else
  echo "INFO: $ROOT_DIR/runtime/RELEASE_NOTES.txt not found (optional)"
fi

so_count=$(find "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a" -maxdepth 1 -type f -name "*.so" | wc -l)
if [[ "$so_count" -lt 1 ]]; then
  echo "MISSING: .so files in app/src/main/jniLibs/arm64-v8a"
  exit 1
fi

echo "OK: found $so_count .so files"

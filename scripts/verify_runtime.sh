#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ROOT="$ROOT_DIR/runtime/models"
MANIFEST="$MODEL_ROOT/_manifest/models.json"

file_size_bytes() {
  if stat -c%s "$1" >/dev/null 2>&1; then
    stat -c%s "$1"
  else
    stat -f%z "$1"
  fi
}

if [[ ! -f "$MANIFEST" ]]; then
  echo "MISSING: $MANIFEST"
  exit 1
fi

FILES=()
while IFS= read -r f; do
  FILES+=("$f")
done < <(find "$MODEL_ROOT" -type f | sort)
if [[ "${#FILES[@]}" -eq 0 ]]; then
  echo "MISSING: no model files under $MODEL_ROOT"
  exit 1
fi

for f in "${FILES[@]}"; do
  if [[ ! -f "$f" ]]; then
    echo "MISSING: $f"
    exit 1
  fi
  size=$(file_size_bytes "$f")
  if [[ "$size" -le 0 ]]; then
    echo "INVALID SIZE: $f"
    exit 1
  fi
  echo "OK: $f ($size bytes)"
done

if [[ -f "$ROOT_DIR/runtime/RELEASE_NOTES.txt" ]]; then
  size=$(file_size_bytes "$ROOT_DIR/runtime/RELEASE_NOTES.txt")
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

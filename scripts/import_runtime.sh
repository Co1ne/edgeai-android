#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$ROOT_DIR/.tmp/runtime-import"

file_size_bytes() {
  if stat -c%s "$1" >/dev/null 2>&1; then
    stat -c%s "$1"
  else
    stat -f%z "$1"
  fi
}

resolve_runtime_tgz() {
  if [[ -n "${RUNTIME_TGZ:-}" && -f "${RUNTIME_TGZ}" ]]; then
    echo "${RUNTIME_TGZ}"
    return 0
  fi

  local candidates=(
    "$ROOT_DIR/../edge-ai-android-runtime-v1.tgz"
    "${EDGE_AI_ROOT:-}/edge-ai-android-runtime-v1.tgz"
    "$HOME/edge-ai/edge-ai-android-runtime-v1.tgz"
  )

  for c in "${candidates[@]}"; do
    if [[ -n "$c" && -f "$c" ]]; then
      echo "$c"
      return 0
    fi
  done

  return 1
}

RUNTIME_TGZ="$(resolve_runtime_tgz)" || {
  echo "ERROR: runtime package not found." >&2
  echo "Tried: \$RUNTIME_TGZ, ../edge-ai-android-runtime-v1.tgz, \$EDGE_AI_ROOT/edge-ai-android-runtime-v1.tgz, \$HOME/edge-ai/edge-ai-android-runtime-v1.tgz" >&2
  exit 1
}

rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"

tar -xzf "$RUNTIME_TGZ" -C "$TMP_DIR"

mkdir -p "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$ROOT_DIR/runtime/models/asr"
mkdir -p "$ROOT_DIR/runtime/models/llm"
mkdir -p "$ROOT_DIR/runtime/models/_manifest"

cp -f "$TMP_DIR"/dist/android/arm64-v8a/*.so "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a/"
cp -f "$TMP_DIR"/models/asr/* "$ROOT_DIR/runtime/models/asr/"
cp -f "$TMP_DIR"/models/llm/* "$ROOT_DIR/runtime/models/llm/"
cp -f "$TMP_DIR/models/_manifest/models.json" "$ROOT_DIR/runtime/models/_manifest/models.json"
cp -f "$TMP_DIR/RELEASE_NOTES.txt" "$ROOT_DIR/runtime/RELEASE_NOTES.txt"

echo "Copied files:"
while IFS= read -r so; do
  printf -- "- %s (%s bytes)\n" "$so" "$(file_size_bytes "$so")"
done < <(find "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a" -maxdepth 1 -type f -name "*.so" | sort)

while IFS= read -r model; do
  printf -- "- %s (%s bytes)\n" "$model" "$(file_size_bytes "$model")"
done < <(find "$ROOT_DIR/runtime/models" -type f | sort)

printf -- "- %s (%s bytes)\n" "$ROOT_DIR/runtime/RELEASE_NOTES.txt" "$(file_size_bytes "$ROOT_DIR/runtime/RELEASE_NOTES.txt")"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_TGZ="$ROOT_DIR/../edge-ai-android-runtime-v1.tgz"
TMP_DIR="$ROOT_DIR/.tmp/runtime-import"

if [[ ! -f "$RUNTIME_TGZ" ]]; then
  echo "ERROR: runtime package not found: $RUNTIME_TGZ" >&2
  exit 1
fi

rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"

tar -xzf "$RUNTIME_TGZ" -C "$TMP_DIR"

mkdir -p "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$ROOT_DIR/runtime/models/asr"
mkdir -p "$ROOT_DIR/runtime/models/llm"
mkdir -p "$ROOT_DIR/runtime/models/_manifest"

cp -f "$TMP_DIR"/dist/android/arm64-v8a/*.so "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a/"
cp -f "$TMP_DIR/models/asr/ggml-base.bin" "$ROOT_DIR/runtime/models/asr/ggml-base.bin"
cp -f "$TMP_DIR/models/llm/model.gguf" "$ROOT_DIR/runtime/models/llm/model.gguf"
cp -f "$TMP_DIR/models/_manifest/models.json" "$ROOT_DIR/runtime/models/_manifest/models.json"
cp -f "$TMP_DIR/RELEASE_NOTES.txt" "$ROOT_DIR/runtime/RELEASE_NOTES.txt"

echo "Copied files:"
find "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a" -maxdepth 1 -type f -name "*.so" -print0 | xargs -0 -I{} sh -c 'printf "- %s (%s bytes)\n" "{}" "$(stat -c%s "{}")"'
printf "- %s (%s bytes)\n" "$ROOT_DIR/runtime/models/asr/ggml-base.bin" "$(stat -c%s "$ROOT_DIR/runtime/models/asr/ggml-base.bin")"
printf "- %s (%s bytes)\n" "$ROOT_DIR/runtime/models/llm/model.gguf" "$(stat -c%s "$ROOT_DIR/runtime/models/llm/model.gguf")"
printf "- %s (%s bytes)\n" "$ROOT_DIR/runtime/models/_manifest/models.json" "$(stat -c%s "$ROOT_DIR/runtime/models/_manifest/models.json")"
printf "- %s (%s bytes)\n" "$ROOT_DIR/runtime/RELEASE_NOTES.txt" "$(stat -c%s "$ROOT_DIR/runtime/RELEASE_NOTES.txt")"

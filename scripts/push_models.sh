#!/usr/bin/env bash
set -euo pipefail

PKG_NAME="${1:-com.edgeaivoice}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ROOT="$ROOT_DIR/runtime/models"
TARGET_BASE="/sdcard/Android/data/${PKG_NAME}/files/models"

required=(
  "$MODEL_ROOT/asr/ggml-base.bin"
  "$MODEL_ROOT/llm/model.gguf"
  "$MODEL_ROOT/_manifest/models.json"
)

for f in "${required[@]}"; do
  [[ -f "$f" ]] || { echo "Missing local model file: $f"; exit 1; }
done

adb start-server >/dev/null
adb shell "mkdir -p $TARGET_BASE/asr $TARGET_BASE/llm $TARGET_BASE/_manifest"
adb push "$MODEL_ROOT/asr/ggml-base.bin" "$TARGET_BASE/asr/ggml-base.bin"
adb push "$MODEL_ROOT/llm/model.gguf" "$TARGET_BASE/llm/model.gguf"
adb push "$MODEL_ROOT/_manifest/models.json" "$TARGET_BASE/_manifest/models.json"

echo "Device model files:"
adb shell "ls -lh $TARGET_BASE/asr/ggml-base.bin $TARGET_BASE/llm/model.gguf $TARGET_BASE/_manifest/models.json"

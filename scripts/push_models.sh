#!/usr/bin/env bash
set -euo pipefail

PKG_NAME="${1:-com.edgeaivoice}"
SERIAL="${2:-}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ROOT="$ROOT_DIR/runtime/models"
TARGET_BASE="/sdcard/Android/data/${PKG_NAME}/files/models"

MANIFEST="$MODEL_ROOT/_manifest/models.json"
[[ -f "$MANIFEST" ]] || { echo "Missing local model manifest: $MANIFEST"; exit 1; }

adb_args=()
if [[ -n "$SERIAL" ]]; then
  adb_args=(-s "$SERIAL")
fi

local_files=()
while IFS= read -r f; do
  local_files+=("$f")
done < <(find "$MODEL_ROOT" -type f | sort)
if [[ "${#local_files[@]}" -eq 0 ]]; then
  echo "Missing local model files under: $MODEL_ROOT"
  exit 1
fi

adb "${adb_args[@]}" start-server >/dev/null
adb "${adb_args[@]}" shell "mkdir -p $TARGET_BASE"

for local_path in "${local_files[@]}"; do
  rel="${local_path#$MODEL_ROOT/}"
  remote="$TARGET_BASE/$rel"
  remote_dir="$(dirname "$remote")"
  adb "${adb_args[@]}" shell "mkdir -p '$remote_dir'"
  adb "${adb_args[@]}" push "$local_path" "$remote"
done

echo "Device model files:"
adb "${adb_args[@]}" shell "find '$TARGET_BASE' -type f"

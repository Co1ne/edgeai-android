#!/usr/bin/env bash
set -euo pipefail

# Backward-compatible runtime push entry.
# Usage:
#   bash scripts/push_runtime.sh [packageName] [serial]

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash "$ROOT_DIR/scripts/push_models.sh" "${1:-com.edgeaivoice}" "${2:-}"

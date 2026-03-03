#!/usr/bin/env bash
set -euo pipefail

# Milestone 3 quick regression runner.
# Usage:
#   bash scripts/m3_regression.sh [loops] [package] [serial]
# Example:
#   bash scripts/m3_regression.sh 30 com.edgeaivoice 5XA6USSGWWPRJFQW

LOOPS="${1:-30}"
PKG="${2:-com.edgeaivoice}"
SERIAL="${3:-}"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB+=( -s "$SERIAL" )
fi

DEBUG_TAP_X=709
DEBUG_TAP_Y=344
M2_TAP_X=213
M2_TAP_Y=2234
RESET_TAP_X=530
RESET_TAP_Y=2234
DONE_TIMEOUT_SEC=120

success=0
failed=0
failed_timeout=0
failed_llm=0
sum_ttft=0
sum_toks=0
metrics_count=0

tap_xy() {
  local x="$1"
  local y="$2"
  "${ADB[@]}" shell input tap "$x" "$y"
}

extract_metrics_totals() {
  local line
  line="$("${ADB[@]}" logcat -d -v brief | rg "M3-METRICS" | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo ""
    return 0
  fi
  local ttft toks
  ttft="$(echo "$line" | awk '{for(i=1;i<=NF;i++){if($i ~ /^ttftE2EMs=/){split($i,a,"="); print a[2]; break}}}')"
  toks="$(echo "$line" | awk '{for(i=1;i<=NF;i++){if($i ~ /^tokPerSec=/){split($i,a,"="); print a[2]; break}}}')"
  echo "$ttft,$toks"
}

launch_debug_ui_ready() {
  "${ADB[@]}" shell am force-stop "$PKG"
  "${ADB[@]}" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 8
  tap_xy "$DEBUG_TAP_X" "$DEBUG_TAP_Y"
  sleep 10
  return 0
}

echo "[m3] launching app: $PKG"
"${ADB[@]}" logcat -c

for i in $(seq 1 "$LOOPS"); do
  echo "[m3] loop $i/$LOOPS"
  if ! launch_debug_ui_ready; then
    failed=$((failed + 1))
    continue
  fi

  before_metrics="$("${ADB[@]}" logcat -d -v brief | rg -c "M3-METRICS" || true)"
  before_fail="$("${ADB[@]}" logcat -d -v brief | rg -c "M1-UI.*LlmFailed" || true)"

  tap_xy "$M2_TAP_X" "$M2_TAP_Y"

  finished=0
  for _ in $(seq 1 "$DONE_TIMEOUT_SEC"); do
    now_metrics="$("${ADB[@]}" logcat -d -v brief | rg -c "M3-METRICS" || true)"
    if (( now_metrics > before_metrics )); then
      finished=1
      break
    fi
    now_fail="$("${ADB[@]}" logcat -d -v brief | rg -c "M1-UI.*LlmFailed" || true)"
    if (( now_fail > before_fail )); then
      finished=2
      break
    fi
    sleep 1
  done

  if (( finished == 1 )); then
    success=$((success + 1))
    pair="$(extract_metrics_totals)"
    ttft="${pair%%,*}"
    toks="${pair##*,}"
    if [[ -n "$ttft" && "$ttft" =~ ^[0-9]+$ ]]; then
      sum_ttft=$((sum_ttft + ttft))
    fi
    if [[ -n "$toks" && "$toks" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
      sum_toks=$(awk -v a="$sum_toks" -v b="$toks" 'BEGIN { printf "%.4f", a+b }')
      metrics_count=$((metrics_count + 1))
    fi
  else
    failed=$((failed + 1))
    if (( finished == 2 )); then
      failed_llm=$((failed_llm + 1))
    else
      failed_timeout=$((failed_timeout + 1))
    fi
  fi
done

echo "[m3] -------- summary --------"
echo "[m3] success=$success failed=$failed loops=$LOOPS"
if (( failed > 0 )); then
  echo "[m3] failed_timeout=$failed_timeout failed_llm=$failed_llm"
fi
if (( metrics_count > 0 )); then
  avg_ttft=$((sum_ttft / metrics_count))
  avg_toks=$(awk -v s="$sum_toks" -v c="$metrics_count" 'BEGIN { printf "%.2f", s/c }')
  echo "[m3] metrics_count=$metrics_count avg_ttft_e2e_ms=$avg_ttft avg_tok_per_sec=$avg_toks"
fi

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
  "runtime/models/asr/ggml-base.bin",
  "runtime/models/llm/model.gguf",
  "runtime/models/_manifest/models.json"
)

foreach ($f in $requiredFiles) {
  $p = Join-Path $root $f
  if (-not (Test-Path $p)) { throw "Missing: $p" }
  $size = (Get-Item $p).Length
  if ($size -le 0) { throw "Invalid size: $p" }
  Write-Output "OK: $f ($size bytes)"
}

$notes = Join-Path $root "runtime/RELEASE_NOTES.txt"
if (Test-Path $notes) {
  $size = (Get-Item $notes).Length
  Write-Output "OK: runtime/RELEASE_NOTES.txt ($size bytes)"
} else {
  Write-Output "INFO: runtime/RELEASE_NOTES.txt not found (optional)"
}

$soDir = Join-Path $root "app/src/main/jniLibs/arm64-v8a"
$so = Get-ChildItem -Path $soDir -Filter *.so -File -ErrorAction SilentlyContinue
if (-not $so -or $so.Count -eq 0) { throw "Missing .so files in $soDir" }
Write-Output "OK: found $($so.Count) .so files"

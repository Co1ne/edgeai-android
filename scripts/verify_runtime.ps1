$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$modelRoot = Join-Path $root "runtime/models"
$manifest = Join-Path $modelRoot "_manifest/models.json"

if (-not (Test-Path $manifest)) { throw "Missing: $manifest" }

$modelFiles = Get-ChildItem -Path $modelRoot -Recurse -File | Sort-Object FullName
if (-not $modelFiles -or $modelFiles.Count -eq 0) { throw "Missing model files in $modelRoot" }

foreach ($f in $modelFiles) {
  $size = $f.Length
  if ($size -le 0) { throw "Invalid size: $($f.FullName)" }
  $rel = $f.FullName.Substring($root.Length + 1)
  Write-Output "OK: $rel ($size bytes)"
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

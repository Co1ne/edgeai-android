param(
  [string]$RuntimeTgz = "../edge-ai-android-runtime-v1.tgz"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$runtimeCandidate = Join-Path $root $RuntimeTgz
if (-not (Test-Path $runtimeCandidate)) {
  throw "Runtime package not found: $runtimeCandidate"
}
$tgzPath = (Resolve-Path $runtimeCandidate).Path

$tmpDir = Join-Path $root ".tmp/runtime-import"
if (Test-Path $tmpDir) { Remove-Item -Recurse -Force $tmpDir }
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

tar -xzf $tgzPath -C $tmpDir
if ($LASTEXITCODE -ne 0) { throw "Archive extraction failed (possibly truncated): $tgzPath" }

$targets = @(
  @{ From = "dist/android/arm64-v8a/*.so"; To = "app/src/main/jniLibs/arm64-v8a" },
  @{ From = "models/asr/ggml-base.bin"; To = "runtime/models/asr/ggml-base.bin" },
  @{ From = "models/llm/model.gguf"; To = "runtime/models/llm/model.gguf" },
  @{ From = "models/_manifest/models.json"; To = "runtime/models/_manifest/models.json" },
  @{ From = "RELEASE_NOTES.txt"; To = "runtime/RELEASE_NOTES.txt" }
)

$copied = @()
foreach ($m in $targets) {
  $srcPattern = Join-Path $tmpDir $m.From
  $dst = Join-Path $root $m.To

  if ($m.From.EndsWith("*.so")) {
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    $items = Get-ChildItem $srcPattern
    if ($items.Count -eq 0) { throw "Missing shared libs in archive" }
    foreach ($i in $items) {
      $target = Join-Path $dst $i.Name
      Copy-Item -Force $i.FullName $target
      $copied += Get-Item $target
    }
  } else {
    $parent = Split-Path -Parent $dst
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    if (-not (Test-Path $srcPattern)) { throw "Missing file in archive: $($m.From)" }
    Copy-Item -Force $srcPattern $dst
    $copied += Get-Item $dst
  }
}

"Copied files:"
$copied | ForEach-Object {
  $rel = $_.FullName.Substring($root.Length + 1)
  "- $rel ($($_.Length) bytes)"
}

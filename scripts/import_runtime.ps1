param(
  [string]$RuntimeTgz = "../edge-ai-android-runtime-v1.tgz"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$runtimeCandidates = @()
if ($RuntimeTgz -ne "") {
  $runtimeCandidates += (Join-Path $root $RuntimeTgz)
}
if ($env:EDGE_AI_ROOT) {
  $runtimeCandidates += (Join-Path $env:EDGE_AI_ROOT "edge-ai-android-runtime-v1.tgz")
}
$runtimeCandidates += (Join-Path $HOME "edge-ai/edge-ai-android-runtime-v1.tgz")

$resolvedTgz = $runtimeCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $resolvedTgz) {
  throw "Runtime package not found. Tried: $($runtimeCandidates -join '; ')"
}
$tgzPath = (Resolve-Path $resolvedTgz).Path

$tmpDir = Join-Path $root ".tmp/runtime-import"
if (Test-Path $tmpDir) { Remove-Item -Recurse -Force $tmpDir }
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

tar -xzf $tgzPath -C $tmpDir
if ($LASTEXITCODE -ne 0) { throw "Archive extraction failed (possibly truncated): $tgzPath" }

$targets = @(
  @{ From = "dist/android/arm64-v8a/*.so"; To = "app/src/main/jniLibs/arm64-v8a" },
  @{ From = "models/asr/*"; To = "runtime/models/asr" },
  @{ From = "models/llm/*"; To = "runtime/models/llm" },
  @{ From = "models/_manifest/models.json"; To = "runtime/models/_manifest/models.json" },
  @{ From = "RELEASE_NOTES.txt"; To = "runtime/RELEASE_NOTES.txt" }
)

$copied = @()
foreach ($m in $targets) {
  $srcPattern = Join-Path $tmpDir $m.From
  $dst = Join-Path $root $m.To

  if ($m.From.EndsWith("*.so") -or $m.From.EndsWith("/*")) {
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    $items = Get-ChildItem $srcPattern -File
    if ($items.Count -eq 0) { throw "Missing files in archive: $($m.From)" }
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

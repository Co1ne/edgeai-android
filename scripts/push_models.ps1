param(
  [string]$PackageName = "com.edgeaivoice",
  [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$modelRoot = Join-Path $root "runtime/models"

$required = @(
  "asr/ggml-base.bin",
  "llm/model.gguf",
  "_manifest/models.json"
)

foreach ($r in $required) {
  $p = Join-Path $modelRoot $r
  if (-not (Test-Path $p)) { throw "Missing local model file: $p" }
}

$adb = "adb"
$targetBase = "/sdcard/Android/data/$PackageName/files/models"
$serialArgs = @()
if ($Serial -ne "") { $serialArgs = @("-s", $Serial) }

& $adb @serialArgs start-server | Out-Null
& $adb @serialArgs shell "mkdir -p $targetBase/asr $targetBase/llm $targetBase/_manifest"

& $adb @serialArgs push (Join-Path $modelRoot "asr/ggml-base.bin") "$targetBase/asr/ggml-base.bin"
& $adb @serialArgs push (Join-Path $modelRoot "llm/model.gguf") "$targetBase/llm/model.gguf"
& $adb @serialArgs push (Join-Path $modelRoot "_manifest/models.json") "$targetBase/_manifest/models.json"

Write-Output "Device model files:"
& $adb @serialArgs shell "ls -lh $targetBase/asr/ggml-base.bin $targetBase/llm/model.gguf $targetBase/_manifest/models.json"

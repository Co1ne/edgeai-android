param(
  [string]$PackageName = "com.edgeaivoice",
  [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$modelRoot = Join-Path $root "runtime/models"

$manifest = Join-Path $modelRoot "_manifest/models.json"
if (-not (Test-Path $manifest)) { throw "Missing local model manifest: $manifest" }
$localFiles = Get-ChildItem -Path $modelRoot -Recurse -File | Sort-Object FullName
if (-not $localFiles -or $localFiles.Count -eq 0) { throw "Missing local model files under: $modelRoot" }

$adb = "adb"
$targetBase = "/sdcard/Android/data/$PackageName/files/models"
$serialArgs = @()
if ($Serial -ne "") { $serialArgs = @("-s", $Serial) }

& $adb @serialArgs start-server | Out-Null
& $adb @serialArgs shell "mkdir -p $targetBase"

foreach ($f in $localFiles) {
  $relative = $f.FullName.Substring($modelRoot.Length + 1).Replace("\\", "/")
  $remote = "$targetBase/$relative"
  $remoteDir = Split-Path $remote -Parent
  & $adb @serialArgs shell "mkdir -p '$remoteDir'"
  & $adb @serialArgs push $f.FullName $remote
}

Write-Output "Device model files:"
& $adb @serialArgs shell "find '$targetBase' -type f"

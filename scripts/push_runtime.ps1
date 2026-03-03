param(
  [string]$PackageName = "com.edgeaivoice",
  [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
powershell -ExecutionPolicy Bypass -File (Join-Path $root "scripts/push_models.ps1") -PackageName $PackageName -Serial $Serial

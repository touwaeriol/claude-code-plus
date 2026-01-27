param(
  [string]$Workspace = (Resolve-Path .).Path,
  [switch]$SkipFrontendBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$extDir = Join-Path $repoRoot "vscode-extension"

Push-Location $extDir
try {
  if ($SkipFrontendBuild) {
    npm run dev:launch -- --workspace $Workspace --skip-frontend-build
  } else {
    npm run dev:launch -- --workspace $Workspace
  }
} finally {
  Pop-Location
}

Write-Host "VS Code launched (dev)."
Write-Host "Workspace: $Workspace"

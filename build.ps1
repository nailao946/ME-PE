# ME-PE (Android) build + release helper.
# Usage:
#   powershell -ExecutionPolicy Bypass -File build.ps1            # assemble debug apk
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Release   # assemble release (needs signing config)
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Push      # build, commit, push
#
# Notes:
#   - Version lives in app\build.gradle.kts (versionCode / versionName). Bump before releasing.
#   - The script does NOT need network when Gradle dependencies are already cached (use --offline first).

param(
    [switch]$Release,
    [switch]$Push,
    [string]$CommitMessage = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Get-Version {
    $code = Get-Content (Join-Path $root "app\build.gradle.kts") -Raw
    $codeName = [regex]::Match($code, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    $codeNum = [regex]::Match($code, 'versionCode\s*=\s*(\d+)').Groups[1].Value
    return @{ Name = $codeName; Code = $codeNum }
}

$v = Get-Version
Write-Host "== ME-PE android build ($($v.Name) / $($v.Code)) ==" -ForegroundColor Cyan

if ($Release) {
    .\gradlew.bat assembleRelease --console=plain
} else {
    # Try offline first (fast, no network); fall back to online resolution.
    .\gradlew.bat assembleDebug --console=plain --offline
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Offline build failed, retrying online..." -ForegroundColor Yellow
        .\gradlew.bat assembleDebug --console=plain
    }
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build FAILED" -ForegroundColor Red
    exit 1
}
Write-Host "Build OK" -ForegroundColor Green

if (-not $Push) { return }

if ([string]::IsNullOrWhiteSpace($CommitMessage)) {
    $CommitMessage = "v$($v.Name): build from build.ps1"
}

git add -A
git commit -m $CommitMessage
git push origin HEAD
if ($LASTEXITCODE -eq 0) {
    Write-Host ("Pushed v" + $v.Name) -ForegroundColor Green
}

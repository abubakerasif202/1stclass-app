[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\pilot-artifacts')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$required = 'KEYSTORE_PATH', 'KEYSTORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD', 'TMS_BASE_URL'
$missing = @($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
if ($missing.Count -gt 0) {
    throw "Missing required environment variables: $($missing -join ', ')"
}

if (-not $env:TMS_BASE_URL.StartsWith('https://', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'TMS_BASE_URL must use HTTPS.'
}
if (-not (Test-Path -LiteralPath $env:KEYSTORE_PATH -PathType Leaf)) {
    throw "KEYSTORE_PATH does not exist: $env:KEYSTORE_PATH"
}

# apksigner ships inside the Android SDK build-tools and is not placed on PATH by the SDK
# installer. Resolve it explicitly so the pilot build fails on a missing SDK rather than
# part-way through, after a full release build has already been produced.
function Resolve-ApkSigner {
    $onPath = Get-Command 'apksigner' -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $roots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
        Where-Object { $_ -and (Test-Path -LiteralPath $_) }

    foreach ($root in $roots) {
        $buildTools = Join-Path $root 'build-tools'
        if (-not (Test-Path -LiteralPath $buildTools)) { continue }
        # Highest build-tools version wins.
        $candidate = Get-ChildItem -LiteralPath $buildTools -Directory -ErrorAction SilentlyContinue |
            Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } -Descending |
            ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
        if ($candidate) { return $candidate }
    }

    throw 'apksigner was not found. Install Android SDK build-tools, or set ANDROID_HOME.'
}

$apkSigner = Resolve-ApkSigner

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    & .\gradlew.bat clean test lint assembleDebug assembleRelease bundleRelease
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }

    $apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
    $aab = Join-Path $projectRoot 'app\build\outputs\bundle\release\app-release.aab'
    if (-not (Test-Path -LiteralPath $apk) -or -not (Test-Path -LiteralPath $aab)) {
        throw 'Expected release APK/AAB outputs were not produced.'
    }

    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $pilotApk = Join-Path $OutputDirectory '1st-Class-Express-Driver-1.0.0-pilot.apk'
    $pilotAab = Join-Path $OutputDirectory '1st-Class-Express-Driver-1.0.0-pilot.aab'
    Copy-Item -LiteralPath $apk -Destination $pilotApk -Force
    Copy-Item -LiteralPath $aab -Destination $pilotAab -Force

    & jarsigner -verify -verbose -certs $pilotAab | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'AAB signature verification failed.' }
    & $apkSigner verify --verbose --print-certs $pilotApk | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }

    Get-Item -LiteralPath $pilotApk, $pilotAab | ForEach-Object {
        $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        [pscustomobject]@{
            Path = $_.FullName
            SizeBytes = $_.Length
            SHA256 = $hash.Hash
        }
    } | Format-Table -AutoSize
}
finally {
    Pop-Location
}

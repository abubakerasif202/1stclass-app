[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$OutputDirectory,
    [int]$RetentionDays = 14
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL)) { throw 'DATABASE_URL must come from the secret manager.' }
$resolved = New-Item -ItemType Directory -Force -Path $OutputDirectory
$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$backup = Join-Path $resolved.FullName "tms-$stamp.dump"
& pg_dump --format=custom --no-owner --no-privileges --file=$backup $env:DATABASE_URL
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed with exit code $LASTEXITCODE" }
$hash = Get-FileHash -LiteralPath $backup -Algorithm SHA256
[pscustomobject]@{ Path = $backup; SizeBytes = (Get-Item $backup).Length; SHA256 = $hash.Hash }
Get-ChildItem -LiteralPath $resolved.FullName -Filter 'tms-*.dump' -File |
    Where-Object LastWriteTimeUtc -lt (Get-Date).ToUniversalTime().AddDays(-$RetentionDays) |
    Remove-Item -Force

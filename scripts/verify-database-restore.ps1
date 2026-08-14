[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$BackupPath,
    [Parameter(Mandatory)] [string]$RestoreDatabaseUrl
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not (Test-Path -LiteralPath $BackupPath -PathType Leaf)) { throw 'Backup file not found.' }
if ([string]::IsNullOrWhiteSpace($RestoreDatabaseUrl)) { throw 'A disposable restore database URL is required.' }
& pg_restore --clean --if-exists --no-owner --no-privileges --dbname=$RestoreDatabaseUrl $BackupPath
if ($LASTEXITCODE -ne 0) { throw "pg_restore failed with exit code $LASTEXITCODE" }
& psql $RestoreDatabaseUrl -v ON_ERROR_STOP=1 -c 'SELECT entity_type, count(*) FROM transport_entities GROUP BY entity_type ORDER BY entity_type;'
if ($LASTEXITCODE -ne 0) { throw 'Restore verification query failed.' }

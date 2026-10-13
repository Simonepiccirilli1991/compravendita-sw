<#
.SYNOPSIS
    Ripristina un backup creato da backup.ps1: database e foto/allegati.

.DESCRIPTION
    ATTENZIONE: sostituisce i dati attuali con quelli del backup.
    Ferma prima l'applicazione; il container del database deve essere avviato.

.PARAMETER Cartella
    Cartella del backup (es. .\backups\2026-10-13_120000).

.EXAMPLE
    .\scripts\restore.ps1 -Cartella .\backups\2026-10-13_120000
#>
param(
    [Parameter(Mandatory = $true)][string]$Cartella
)

$ErrorActionPreference = "Stop"
$radice = Resolve-Path (Join-Path $PSScriptRoot "..")
$container = "compravendita-db"
$database = if ($env:DB_NAME) { $env:DB_NAME } else { "compravendita" }
$utente = if ($env:DB_USER) { $env:DB_USER } else { "compravendita" }

$dump = Join-Path $Cartella "database.dump"
if (-not (Test-Path $dump)) { throw "File $dump non trovato" }

$conferma = Read-Host "I dati attuali del database '$database' verranno sostituiti. Scrivi SI per continuare"
if ($conferma -ne "SI") { Write-Host "Ripristino annullato"; exit 1 }

Write-Host "Ripristino del database..."
docker cp $dump "${container}:/tmp/restore.dump"
if ($LASTEXITCODE -ne 0) { throw "Copia del dump nel container non riuscita: il container è avviato?" }
docker exec $container pg_restore -U $utente -d $database --clean --if-exists --no-owner /tmp/restore.dump
if ($LASTEXITCODE -ne 0) { throw "pg_restore non riuscito" }
docker exec $container rm -f /tmp/restore.dump | Out-Null

$archivio = Join-Path $Cartella "uploads.zip"
if (Test-Path $archivio) {
    Write-Host "Ripristino di foto e allegati..."
    $uploads = Join-Path $radice "data\uploads"
    New-Item -ItemType Directory -Force $uploads | Out-Null
    Expand-Archive -Path $archivio -DestinationPath $uploads -Force
}

Write-Host "Ripristino completato. Puoi riavviare l'applicazione."

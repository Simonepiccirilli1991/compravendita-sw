<#
.SYNOPSIS
    Backup del gestionale: database PostgreSQL (pg_dump, formato custom) e cartella delle foto/allegati.

.DESCRIPTION
    Crea backups\AAAA-MM-GG_HHMMSS\ con:
      - database.dump   dump del database dal container Docker "compravendita-db"
      - uploads.zip     copia di data\uploads (foto articoli e ricevute)
    Il container deve essere avviato (docker compose up -d).

.PARAMETER Destinazione
    Cartella in cui creare i backup (predefinita: .\backups).

.PARAMETER Conserva
    Quanti backup tenere: i più vecchi vengono eliminati (0 = tienili tutti).

.EXAMPLE
    .\scripts\backup.ps1
    .\scripts\backup.ps1 -Destinazione D:\Backup\gestionale -Conserva 10
#>
param(
    [string]$Destinazione = (Join-Path $PSScriptRoot "..\backups"),
    [int]$Conserva = 0
)

$ErrorActionPreference = "Stop"
$radice = Resolve-Path (Join-Path $PSScriptRoot "..")
$container = "compravendita-db"
$database = if ($env:DB_NAME) { $env:DB_NAME } else { "compravendita" }
$utente = if ($env:DB_USER) { $env:DB_USER } else { "compravendita" }

$cartella = Join-Path $Destinazione (Get-Date -Format "yyyy-MM-dd_HHmmss")
New-Item -ItemType Directory -Force $cartella | Out-Null

Write-Host "Dump del database '$database'..."
# il dump viene scritto nel container e poi copiato: il redirect di PowerShell rovinerebbe il file binario
docker exec $container pg_dump -U $utente -d $database -Fc -f /tmp/backup.dump
if ($LASTEXITCODE -ne 0) { throw "pg_dump non riuscito: il container '$container' è avviato?" }
docker cp "${container}:/tmp/backup.dump" (Join-Path $cartella "database.dump")
if ($LASTEXITCODE -ne 0) { throw "Copia del dump non riuscita" }
docker exec $container rm -f /tmp/backup.dump | Out-Null

$uploads = Join-Path $radice "data\uploads"
if (Test-Path $uploads) {
    Write-Host "Archivio di foto e allegati..."
    Compress-Archive -Path (Join-Path $uploads "*") -DestinationPath (Join-Path $cartella "uploads.zip") -Force
}

if ($Conserva -gt 0) {
    Get-ChildItem $Destinazione -Directory | Sort-Object Name -Descending | Select-Object -Skip $Conserva |
        ForEach-Object { Remove-Item $_.FullName -Recurse -Force }
}

Write-Host "Backup completato in $cartella"

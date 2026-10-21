<#
.SYNOPSIS
    Backup lanciato dall'Utilità di pianificazione: esegue backup.ps1 e scrive l'esito in un log.

.DESCRIPTION
    Se il container del database è fermo lo avvia per il tempo del backup e poi lo rimette com'era.
    Docker Desktop deve essere avviato: se non lo è il backup viene saltato e il log lo segnala.
    Log: <Destinazione>\backup.log (le ultime 500 righe).

.PARAMETER Destinazione
    Cartella dei backup (predefinita: .\backups).

.PARAMETER Conserva
    Quanti backup tenere (predefinito 30).
#>
param(
    [string]$Destinazione = (Join-Path $PSScriptRoot "..\backups"),
    [int]$Conserva = 30
)

# "Continue": in PowerShell 5.1 lo stderr dei comandi nativi con "Stop" diventerebbe un errore
$ErrorActionPreference = "Continue"
$container = "compravendita-sw-tazze-db"
New-Item -ItemType Directory -Force $Destinazione | Out-Null
$log = Join-Path $Destinazione "backup.log"

function Scrivi([string]$testo) {
    $riga = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $testo
    Add-Content -Path $log -Value $riga -Encoding UTF8
    Write-Host $riga
}

$avviato = $false
try {
    docker info --format "{{.ServerVersion}}" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Docker Desktop non è avviato: backup saltato" }

    $stato = docker inspect -f "{{.State.Running}}" $container 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Container '$container' inesistente: avvia almeno una volta l'applicazione" }
    if ($stato -ne "true") {
        docker start $container | Out-Null
        $avviato = $true
        # attende che PostgreSQL accetti connessioni
        for ($i = 0; $i -lt 30; $i++) {
            docker exec $container pg_isready -q 2>$null
            if ($LASTEXITCODE -eq 0) { break }
            Start-Sleep -Seconds 1
        }
    }

    $uscita = & (Join-Path $PSScriptRoot "backup.ps1") -Destinazione $Destinazione -Conserva $Conserva 6>&1
    Scrivi ("OK   " + ($uscita | Select-Object -Last 1))
}
catch {
    Scrivi ("ERRORE  " + $_.Exception.Message)
    exit 1
}
finally {
    if ($avviato) { docker stop $container | Out-Null }
    if (Test-Path $log) {
        $righe = Get-Content $log -Encoding UTF8
        if ($righe.Count -gt 500) { $righe | Select-Object -Last 500 | Set-Content $log -Encoding UTF8 }
    }
}

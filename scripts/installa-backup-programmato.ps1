<#
.SYNOPSIS
    Installa (o rimuove) il backup automatico giornaliero nell'Utilità di pianificazione di Windows.

.DESCRIPTION
    Crea l'operazione "Gestionale Compravendita - Backup" per l'utente corrente: ogni giorno all'ora
    indicata esegue backup-programmato.ps1. Se a quell'ora il PC è spento, il backup parte appena
    possibile. L'operazione gira solo con l'utente collegato (serve Docker Desktop avviato).

.PARAMETER Ora
    Ora del backup giornaliero, formato HH:mm (predefinita 21:00).

.PARAMETER Conserva
    Quanti backup tenere (predefinito 30).

.PARAMETER Destinazione
    Cartella dei backup (predefinita: .\backups del progetto). Meglio un disco esterno o una cartella
    sincronizzata nel cloud (es. OneDrive).

.PARAMETER Rimuovi
    Elimina l'operazione pianificata.

.EXAMPLE
    .\scripts\installa-backup-programmato.ps1
    .\scripts\installa-backup-programmato.ps1 -Ora 13:00 -Conserva 60 -Destinazione "D:\Backup gestionale"
    .\scripts\installa-backup-programmato.ps1 -Rimuovi
#>
param(
    [string]$Ora = "21:00",
    [int]$Conserva = 30,
    [string]$Destinazione = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..")) "backups"),
    [switch]$Rimuovi
)

$ErrorActionPreference = "Stop"
$nome = "Gestionale Compravendita - Backup"

if ($Rimuovi) {
    if (Get-ScheduledTask -TaskName $nome -ErrorAction SilentlyContinue) {
        Unregister-ScheduledTask -TaskName $nome -Confirm:$false
        Write-Host "Backup automatico rimosso."
    }
    else {
        Write-Host "Nessun backup automatico installato."
    }
    exit 0
}

$script = Join-Path $PSScriptRoot "backup-programmato.ps1"
$argomenti = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`" " +
    "-Destinazione `"$Destinazione`" -Conserva $Conserva"

$azione = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argomenti -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -Daily -At ([datetime]::ParseExact($Ora, "HH:mm", $null))
$impostazioni = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 30) -MultipleInstances IgnoreNew
$utente = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $nome -Action $azione -Trigger $trigger -Settings $impostazioni -Principal $utente `
    -Description "Backup giornaliero di database e foto del gestionale compravendita" -Force | Out-Null

Write-Host "Backup automatico installato: ogni giorno alle $Ora, in $Destinazione (ultimi $Conserva)."
Write-Host "Esito di ogni backup: $(Join-Path $Destinazione 'backup.log')"
Write-Host "Per provarlo subito: Start-ScheduledTask -TaskName '$nome'"

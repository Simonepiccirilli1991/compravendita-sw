# Crea l'installer Windows di Gestionale Tazze (dist\Gestionale Tazze-<versione>.exe).
# L'installer contiene il programma, Java e PostgreSQL 16: chi lo installa non deve avere altro.
#
# Requisiti (solo sul PC che crea l'installer):
#   - JDK 21 o successivo con jpackage, indicato da JAVA_HOME
#   - WiX Toolset 5:  dotnet tool install --global wix --version 5.0.2
#                     wix extension add -g WixToolset.UI.wixext/5.0.2
#                     wix extension add -g WixToolset.Util.wixext/5.0.2
#
# Uso:  powershell -ExecutionPolicy Bypass -File installer\crea-installer.ps1 [-Versione 1.0.1] [-ConTest]
param(
    # Versione mostrata in "App installate"; per aggiornare un'installazione esistente va aumentata
    [string]$Versione = "1.0.0",
    # Esegue anche i test (mvnw verify, serve Docker per Testcontainers)
    [switch]$ConTest
)
$ErrorActionPreference = "Stop"

$Nome = "Gestionale Tazze"
# Non cambiarlo mai: è ciò che permette a una versione nuova di sostituire quella installata
$UpgradeUuid = "6f1c2a8e-3b4d-4c7a-9e21-7a5d0b9c3f41"
$PgVersione = "16.15-1"

$Radice = Split-Path -Parent $PSScriptRoot
$Cache = Join-Path $PSScriptRoot "cache"
$Lavoro = Join-Path $Radice "target\installer"
$Ingresso = Join-Path $Lavoro "input"
$Dist = Join-Path $Radice "dist"

function Passo($testo) { Write-Host "==> $testo" -ForegroundColor Cyan }

# --- Strumenti -------------------------------------------------------------------------------------------
if (-not $env:JAVA_HOME) { throw "JAVA_HOME non impostata: indica un JDK 21+ con jpackage" }
$Jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $Jpackage)) { throw "jpackage non trovato in $Jpackage" }
if (-not (Get-Command wix -ErrorAction SilentlyContinue)) {
    $env:PATH += ";$env:USERPROFILE\.dotnet\tools"
    if (-not (Get-Command wix -ErrorAction SilentlyContinue)) {
        throw "WiX Toolset non trovato: vedi i requisiti in testa a questo script"
    }
}

# --- PostgreSQL ------------------------------------------------------------------------------------------
$ZipPg = Join-Path $Cache "postgresql-$PgVersione-windows-x64-binaries.zip"
if (-not (Test-Path $ZipPg)) {
    Passo "Scarico PostgreSQL $PgVersione (circa 320 MB, solo la prima volta)"
    New-Item -ItemType Directory -Force $Cache | Out-Null
    & curl.exe -L --fail -o "$ZipPg.part" "https://get.enterprisedb.com/postgresql/postgresql-$PgVersione-windows-x64-binaries.zip"
    if ($LASTEXITCODE -ne 0) { throw "Download di PostgreSQL non riuscito" }
    Move-Item "$ZipPg.part" $ZipPg
}

Passo "Preparo PostgreSQL (solo i file necessari)"
if (Test-Path $Lavoro) { Remove-Item -Recurse -Force $Lavoro }
New-Item -ItemType Directory -Force $Ingresso | Out-Null
& tar.exe -xf $ZipPg -C $Lavoro "pgsql/bin" "pgsql/lib" "pgsql/share" "pgsql/server_license.txt" "pgsql/commandlinetools_3rd_party_licenses.txt"
if ($LASTEXITCODE -ne 0) { throw "Estrazione di PostgreSQL non riuscita" }
$Pgsql = Join-Path $Lavoro "pgsql"
# programmi usati dal gestionale (avvio, backup, ripristino) più psql per l'assistenza
$Programmi = "postgres.exe", "initdb.exe", "pg_ctl.exe", "pg_dump.exe", "pg_restore.exe", "psql.exe"
Get-ChildItem "$Pgsql\bin" -File | Where-Object {
    ($_.Extension -eq ".exe" -and $Programmi -notcontains $_.Name) -or $_.Name -like "wx*" -or $_.Extension -in ".pdb", ".pl", ".lib"
} | Remove-Item -Force
# PostgreSQL richiede il runtime Visual C++, che su un PC appena installato può mancare: si copiano accanto
# ai programmi le stesse DLL ridistribuibili incluse nel JDK
foreach ($dll in "vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll") {
    Copy-Item (Join-Path $env:JAVA_HOME "bin\$dll") "$Pgsql\bin"
}
Get-ChildItem "$Pgsql\lib" -Recurse -File | Where-Object { $_.Extension -ne ".dll" } | Remove-Item -Force
Get-ChildItem "$Pgsql\lib" -Directory | Remove-Item -Recurse -Force
if (Test-Path "$Pgsql\share\doc") { Remove-Item -Recurse -Force "$Pgsql\share\doc" }
Move-Item $Pgsql (Join-Path $Ingresso "pgsql")

# --- Applicazione ----------------------------------------------------------------------------------------
Push-Location $Radice
try {
    if ($ConTest) {
        Passo "Compilo ed eseguo i test"
        & .\mvnw.cmd -B -q verify
    } else {
        Passo "Compilo il gestionale"
        & .\mvnw.cmd -B -q package -DskipTests
    }
    if ($LASTEXITCODE -ne 0) { throw "Build Maven non riuscita" }
} finally {
    Pop-Location
}
$Jar = Get-ChildItem (Join-Path $Radice "target") -Filter "gestionale-*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
Copy-Item $Jar.FullName (Join-Path $Ingresso "gestionale.jar")

# --- Installer -------------------------------------------------------------------------------------------
Passo "Creo l'installer con jpackage (qualche minuto)"
New-Item -ItemType Directory -Force $Dist | Out-Null
$Esistente = Join-Path $Dist "$Nome-$Versione.exe"
if (Test-Path $Esistente) { Remove-Item -Force $Esistente }
& $Jpackage `
    --type exe `
    --name $Nome `
    --app-version $Versione `
    --vendor $Nome `
    --description "Gestionale per la compravendita di tazze e articoli simili" `
    --icon (Join-Path $PSScriptRoot "icona.ico") `
    --input $Ingresso `
    --main-jar gestionale.jar `
    --main-class org.springframework.boot.loader.launch.PropertiesLauncher `
    --java-options "-Dloader.main=it.compravendita.gestionale.desktop.AvvioDesktop" `
    --java-options '-Dgestionale.pgsql=$APPDIR\pgsql' `
    --java-options "-Xmx768m" `
    --java-options "-Dfile.encoding=UTF-8" `
    --win-per-user-install `
    --install-dir "Programs\$Nome" `
    --win-menu `
    --win-menu-group $Nome `
    --win-shortcut `
    --win-upgrade-uuid $UpgradeUuid `
    --dest $Dist
if ($LASTEXITCODE -ne 0) { throw "jpackage non riuscito" }

$Creato = Get-Item $Esistente
Passo ("Installer pronto: {0} ({1:N0} MB)" -f $Creato.FullName, ($Creato.Length / 1MB))

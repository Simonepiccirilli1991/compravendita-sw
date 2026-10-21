# Gestionale Tazze

Gestionale per tracciare acquisti, magazzino e vendite di tazze e articoli simili (tazze termiche e borracce,
bicchieri, set e confezioni regalo, accessori) sui marketplace (Vinted, eBay, Etsy, Subito, …), con calcolo di
netto, ROI e dashboard.

> Versione del branch `tazze`, derivata dal gestionale carte/videogiochi/LEGO (branch `main`): stesse funzioni di
> catalogo, acquisti, magazzino, vendite e dashboard, senza il grading. Usa un progetto Docker separato
> (`compravendita-sw-tazze`, container `compravendita-sw-tazze-db` / `compravendita-sw-tazze-app`, volume
> proprio): i dati delle due versioni non si mescolano mai. Le porte predefinite sono le stesse (8080 e 5432),
> quindi si avvia una versione alla volta, oppure si cambiano `APP_PORT` e `DB_PUBLISHED_PORT` nel file `.env`.
> Il piano di sviluppo è in [PIANO.md](PIANO.md).

## Avvio rapido (tutto in Docker)
Serve solo Docker Desktop avviato. Dalla cartella del progetto:
```bash
docker compose up -d --build     # database + applicazione su http://localhost:8080 (admin / admin)
```
In Docker Desktop compare il progetto **compravendita-sw-tazze**: da lì si ferma e si riavvia con i pulsanti
Stop/Play. Il `--build` serve solo la prima volta e dopo ogni modifica al codice.

## Stack
- Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, Spring Security
- PostgreSQL 16 (Docker)
- Thymeleaf + HTMX, Bootstrap 5, Chart.js (serviti in locale tramite WebJars)

## Prerequisiti
- JDK 21 o superiore (`JAVA_HOME` impostato)
- Docker Desktop avviato

Maven non serve: si usa il wrapper `mvnw` incluso.

## Avvio
```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```
Con il profilo `dev` (predefinito) Spring Boot avvia da solo il container PostgreSQL definito in
`docker-compose.dev.yml` (solo database, stesso volume della versione in Docker) e lo lascia acceso alla
chiusura. In alternativa:
```bash
docker compose -f docker-compose.dev.yml up -d   # solo database
docker compose --profile tools up -d             # tutto + pgAdmin su http://localhost:5050
```
Applicazione: <http://localhost:8080> — credenziali predefinite `admin` / `admin`.

## Configurazione
I valori predefiniti si sovrascrivono con variabili d'ambiente o con un file `.env` nella cartella del progetto
(vedi `.env.example`): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `APP_USERNAME`, `APP_PASSWORD`,
`APP_PORT`, `APP_UPLOAD_DIR`, `DB_PUBLISHED_PORT` (porta del database sul PC), `PGADMIN_PORT`.

## Dati demo
Per provare l'applicazione con un anno di attività di esempio (acquisti, vendite su più piattaforme,
resi, rettifiche e spese):
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
```
I dati demo stanno nello schema PostgreSQL `demo`, separato dai dati reali (schema `public`), e vengono creati
solo se lo schema è vuoto. Per ripartire da zero: `drop schema demo cascade;` (es. da pgAdmin). Il profilo demo
imposta anche commissioni indicative su eBay ed Etsy, sempre e solo nello schema `demo`.

## Esecuzione in Docker
L'immagine dell'applicazione si costruisce dal `Dockerfile`; `docker-compose.yml` avvia database e applicazione:
```bash
docker compose up -d --build     # database + applicazione su http://localhost:8080
docker compose logs -f app
docker compose down              # ferma tutto (i dati restano nel volume pgdata)
```
Nel container l'app usa il profilo `prod`. Credenziali, porta e database si impostano nel file `.env`
(vedi `.env.example`); foto e ricevute restano in `data/uploads`, quindi gli script di backup funzionano allo
stesso modo. Per la demo in container: `SPRING_PROFILES_ACTIVE=prod,demo docker compose up -d`.

## Spese generali
Abbonamenti, materiali, fiere e altri costi dell'attività si registrano in *Spese* (barra laterale). Entrano nel
profitto netto di dashboard, report e riepilogo annuale, ma non nel costo dei singoli articoli; si esportano in
Excel/CSV come le altre liste.

## Import ed export
- **Export:** nelle liste di acquisti, vendite e magazzino i pulsanti *Excel* e *CSV* scaricano i dati con i filtri
  attivi. Il riepilogo annuale (*Report → Riepilogo annuale*) si esporta allo stesso modo. I CSV usano `;` e la
  virgola decimale, quindi si aprono direttamente in Excel in italiano.
- **Import inventario:** *Importa* nella barra laterale. Scarica il modello CSV, compilalo (una riga per lotto) e
  caricalo: l'anteprima segnala gli errori riga per riga e non viene importato nulla finché non sono tutti corretti.

## Backup e ripristino
Gli script salvano il database (con `pg_dump` dentro il container `compravendita-sw-tazze-db`) e la cartella
`data/uploads` con foto e ricevute. Il container deve essere avviato.

Windows (PowerShell):
```powershell
.\scripts\backup.ps1                                       # crea backups\AAAA-MM-GG_HHMMSS\ 
.\scripts\backup.ps1 -Destinazione D:\Backup -Conserva 10   # altrove, tenendo solo gli ultimi 10
.\scripts\restore.ps1 -Cartella .\backups\2026-10-13_120000
```
Linux / macOS:
```bash
./scripts/backup.sh [cartella]
./scripts/restore.sh backups/2026-10-13_120000
```
Il ripristino **sostituisce** i dati attuali e chiede conferma: ferma prima l'applicazione. Se PowerShell blocca
gli script, eseguili con `powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1`.

Senza gli script, con PostgreSQL raggiungibile in locale:
```bash
pg_dump -h localhost -U compravendita -d compravendita -Fc -f gestionale.dump
pg_restore -h localhost -U compravendita -d compravendita --clean --if-exists --no-owner gestionale.dump
```
Conviene copiare i backup anche fuori dal PC (disco esterno o cloud).

### Backup automatico
Installa un backup giornaliero nell'Utilità di pianificazione di Windows (una volta sola):
```powershell
.\scripts\installa-backup-programmato.ps1                       # ogni giorno alle 21:00, ultimi 30 backup
.\scripts\installa-backup-programmato.ps1 -Ora 13:00 -Conserva 60 -Destinazione "D:\Backup gestionale"
.\scripts\installa-backup-programmato.ps1 -Rimuovi              # disattiva
Start-ScheduledTask -TaskName "Gestionale Tazze - Backup"  # prova subito
```
Se a quell'ora il PC è spento, il backup parte appena possibile. Docker Desktop deve essere avviato; il database
può anche essere fermo (viene acceso per il backup e poi rispento). L'esito di ogni esecuzione è in
`backup.log` nella cartella dei backup: controllalo ogni tanto, e scegli come destinazione un disco esterno o
una cartella sincronizzata nel cloud.

## Test
```bash
./mvnw verify
```
I test di integrazione usano Testcontainers (PostgreSQL 16 reale): richiedono Docker in esecuzione.
`PagineSenzaTransazioneTest` apre ogni pagina con i dati demo fuori da una transazione, come il browser.
Gli errori (pagina inesistente, dati non trovati, operazioni non consentite) mostrano una pagina in italiano;
nelle azioni HTMX il messaggio compare in un avviso in alto a destra.

## Struttura
```
src/main/java/it/compravendita/gestionale/
  catalogo/  acquisti/  magazzino/  vendite/  piattaforme/  report/  dashboard/
  esportazioni/  importazione/  ricerca/  spese/  demo/  common/
scripts/          backup e ripristino
src/main/resources/
  db/migration/   migrazioni Flyway
  templates/      pagine Thymeleaf (fragments/layout.html = layout comune)
```

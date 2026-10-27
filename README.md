# Gestionale Compravendita

Gestionale per tracciare acquisti, magazzino e vendite di carte (Pokémon, One Piece – singole, gradate, sealed),
videogiochi, LEGO e oggetti fuori dal core (tazze e bicchieri, oggettistica e gadget, altro), con calcolo di netto, ROI e dashboard. Il piano di sviluppo è in [PIANO.md](PIANO.md).

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
`docker-compose.yml` e lo lascia acceso alla chiusura. In alternativa:
```bash
docker compose up -d            # solo database
docker compose --profile tools up -d   # database + pgAdmin su http://localhost:5050
```
Applicazione: <http://localhost:8080> — credenziali predefinite `admin` / `admin`.

## Configurazione
I valori predefiniti si sovrascrivono con variabili d'ambiente o con un file `.env` nella cartella del progetto
(vedi `.env.example`): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `APP_USERNAME`, `APP_PASSWORD`,
`APP_PORT`, `APP_UPLOAD_DIR`.

## Dati demo
Per provare l'applicazione con un anno di attività di esempio (acquisti, vendite su più piattaforme, grading,
resi, rettifiche e spese):
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
```
I dati demo stanno nello schema PostgreSQL `demo`, separato dai dati reali (schema `public`), e vengono creati
solo se lo schema è vuoto. Per ripartire da zero: `drop schema demo cascade;` (es. da pgAdmin). Il profilo demo
imposta anche commissioni indicative su eBay e Cardmarket, sempre e solo nello schema `demo`.

## Esecuzione in Docker
L'immagine dell'applicazione si costruisce dal `Dockerfile`; il servizio `app` di `docker-compose.yml` è nel
profilo `app`, quindi non parte durante lo sviluppo:
```bash
docker compose --profile app up -d --build      # database + applicazione su http://localhost:8080
docker compose --profile app logs -f app
docker compose --profile app down               # ferma tutto (i dati restano nel volume pgdata)
```
Nel container l'app usa il profilo `prod`. Credenziali, porta e database si impostano nel file `.env`
(vedi `.env.example`); foto e ricevute restano in `data/uploads`, quindi gli script di backup funzionano allo
stesso modo. Per la demo in container: `SPRING_PROFILES_ACTIVE=prod,demo docker compose --profile app up -d`.

## Spese generali
Abbonamenti, materiali, fiere e altri costi dell'attività si registrano in *Spese* (barra laterale). Entrano nel
profitto netto di dashboard, report e riepilogo annuale, ma non nel costo dei singoli articoli; si esportano in
Excel/CSV come le altre liste.

## Prezzi suggeriti (regime forfettario agevolato)
Per ogni riga d'acquisto il gestionale suggerisce due prezzi di vendita per pezzo, visibili nel riepilogo del form
mentre si compila l'acquisto, nel dettaglio dell'acquisto, nei lotti del magazzino e nella scheda dell'articolo
(sul costo del prossimo lotto da vendere):
- **prezzo minimo**: copre costo d'acquisto, commissioni, imballaggio, spedizione, imposta e contributi, compresa
  la quota dei costi fissi; vendendo tutto a questo prezzo con gli incassi previsti, a fine anno si va in pari;
- **prezzo consigliato**: resta in più il margine desiderato sul costo d'acquisto; arrotondato per eccesso ai 50
  centesimi.

Il calcolo segue le regole 2026 di un commerciante in regime forfettario con il regime contributivo agevolato:
- reddito = 40% degli incassi (coefficiente del commercio), imposta sostitutiva 5% sul reddito meno i contributi;
- contributi INPS commercianti 24,48% con minimale di 18.808 € e maternità 0,62 € al mese
  ([circolare INPS n. 14 del 9/2/2026](https://www.inps.it/it/it/inps-comunica/atti/circolari-messaggi-e-normativa/dettaglio.circolari-e-messaggi.2026.02.circolare-numero-14-del-09-02-2026_15162.html));
- riduzione del 35% sia sul minimale sia sull'eccedenza, maternità intera (legge 190/2014, art. 1, commi 77-84;
  circolare INPS n. 35/2016): **3.000,17 € l'anno di contributi fissi**, dovuti anche con incassi bassi, e 15,91%
  sul reddito oltre il minimale.

Con gli incassi previsti in un anno il gestionale separa quanto costa ogni vendita in più (sotto il minimale
solo il 2% di imposta, sopra anche i contributi) dai costi fissi (contributi sul minimale e altri costi), che divide
sugli incassi come percentuale di ogni prezzo: con 10.000 € previsti pesano il 28,5%, con 30.000 € circa il 9,5%.
I parametri si cambiano in *Prezzi e tasse*, dove una simulazione mostra la ripartizione di un prezzo e il conteggio
dell'anno; ogni anno vanno aggiornati con la nuova circolare INPS e verificati con il commercialista.

## Import ed export
- **Export:** nelle liste di acquisti, vendite e magazzino i pulsanti *Excel* e *CSV* scaricano i dati con i filtri
  attivi. Il riepilogo annuale (*Report → Riepilogo annuale*) si esporta allo stesso modo. I CSV usano `;` e la
  virgola decimale, quindi si aprono direttamente in Excel in italiano.
- **Import inventario:** *Importa* nella barra laterale. Scarica il modello CSV, compilalo (una riga per lotto) e
  caricalo: l'anteprima segnala gli errori riga per riga e non viene importato nulla finché non sono tutti corretti.

## Backup e ripristino
Gli script salvano il database (con `pg_dump` dentro il container `compravendita-db`) e la cartella
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
Start-ScheduledTask -TaskName "Gestionale Compravendita - Backup"  # prova subito
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

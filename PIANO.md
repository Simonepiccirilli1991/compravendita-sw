# Piano – Gestionale Compravendita (Java / Spring Boot)

## Context
Serve un gestionale per tracciare l'intera attività di compravendita: carte Pokémon e One Piece (singole e gradate), box sealed, videogiochi retro e nuovi, LEGO sealed e altri oggetti per rivendita. Deve gestire il flusso dell'oggetto (acquisto → magazzino → vendita), i costi reali, il netto per piattaforma, il ROI e una dashboard con gli "hot item" (oggetti, brand e categorie più venduti/redditizi).
La cartella di progetto `compravendita-sw` è vuota: si parte da zero.

**Decisioni prese**
- **DB:** PostgreSQL 16 (via Docker Compose) + colonna `JSONB` per gli attributi specifici di ogni categoria.
- **Frontend:** Thymeleaf + HTMX (unico progetto Spring Boot), Bootstrap 5, Chart.js per i grafici.
- **Utilizzo:** locale, utente singolo (login semplice con Spring Security, un solo utente configurato).
- **Stack:** Java 21, Spring Boot 4.1, Spring Data JPA (Hibernate 6, mapping JSON nativo con `@JdbcTypeCode(SqlTypes.JSON)`), Flyway per le migrazioni, Maven, Testcontainers per i test. Importi sempre in `BigDecimal`.


---

## Fase 0 – Setup progetto
- [x] Generare progetto Spring Boot (Maven, Java 21) con: Web, Thymeleaf, Data JPA, Validation, Security, Flyway, PostgreSQL driver, DevTools, Actuator, Testcontainers *(Lombok escluso: si usano record e codice Java standard)*
- [x] Struttura package per feature: `catalogo`, `acquisti`, `magazzino`, `vendite`, `piattaforme`, `spese`, `report`, `dashboard`, `common`
- [x] `docker-compose.yml` con PostgreSQL 16 + volume persistente (e pgAdmin opzionale)
- [x] Profili `application-dev.yml` / `application-test.yml`
- [x] Layout Thymeleaf base (sidebar, navbar, Bootstrap 5, HTMX, Chart.js da CDN/webjars)
- [x] Login utente singolo (credenziali da variabili d'ambiente)
- [x] `git init` + `.gitignore` + README con istruzioni di avvio

## Fase 1 – Modello dati e migrazioni
- [x] **Categoria** (enum): `CARTA_SINGOLA`, `CARTA_GRADATA`, `SEALED_TCG`, `VIDEOGIOCO`, `LEGO`, `ALTRO`
- [x] **Brand/Franchise** (tabella): Pokémon, One Piece, Nintendo, Sony, Sega, LEGO, … (estendibile da UI)
- [x] **Articolo** (catalogo): nome, categoria, brand, lingua, condizione, note, foto (path), `attributi JSONB`
  - Carte: set, numero, rarità, edizione
  - Gradate: ente (PSA/BGS/CGC/…), voto, n° certificato
  - Videogiochi: console, regione (PAL/NTSC/JP), completezza (Loose/CIB/Sealed), retro/nuovo
  - LEGO: numero set, tema, anno, stato scatola
- [x] **Acquisto** (testata): data, fornitore/canale, costi accessori (spedizione, commissioni, dogana), note, allegato ricevuta
- [x] **RigaAcquisto**: articolo, quantità, prezzo unitario, costo unitario effettivo (con ripartizione dei costi accessori), quantità residua
- [x] **Piattaforma**: nome (eBay, Vinted, Cardmarket, Subito, Wallapop, Fiera, Privato, …), fee %, fee fissa, fee pagamento %
- [x] **Vendita** (testata): data, piattaforma, acquirente/riferimento ordine, prezzo lordo, spedizione incassata, costo spedizione reale, imballaggio, commissioni (calcolate e modificabili), netto
- [x] **RigaVendita** + **AllocazioneVendita** (collega la vendita alle righe d'acquisto con logica FIFO → costo del venduto)
- [x] **Spesa** generica (grading, materiali, abbonamenti) collegabile o meno a un articolo *(package `spese`)*
- [x] Migrazioni Flyway `V1__schema.sql`, `V2__seed_piattaforme_brand.sql`
- [x] Indici per le query di report (data vendita, articolo, brand, categoria, piattaforma)

## Fase 2 – Catalogo articoli
- [x] CRUD articoli con form dinamico in base alla categoria (campi JSONB via HTMX)
- [x] CRUD brand
- [x] Lista articoli con ricerca, filtri (categoria, brand, condizione) e paginazione *(ricerca per nome; ricerca negli attributi in Fase 7)*
- [x] Upload foto articolo (salvataggio su filesystem locale)
- [x] Validazioni (Bean Validation + validatori per attributi di categoria)

## Fase 3 – Acquisti e magazzino
- [x] Registrazione acquisto multi-riga (anche lotti misti: es. lotto di carte + videogiochi)
- [x] Ripartizione costi accessori sulle righe (proporzionale al prezzo o per quantità – scelta per acquisto)
- [x] Stato oggetto: `IN_ARRIVO`, `IN_STOCK`, `IN_GRADING`, `IN_VENDITA`, `VENDUTO`, `RESO` (+ `SCARICATO` per i lotti azzerati da rettifica)
- [x] Flusso grading: invio carta singola → rientro come gradata (costo grading aggiunto al costo dell'oggetto)
- [x] Vista magazzino: giacenze per articolo, valore a costo, giorni di giacenza
- [x] Rettifiche inventario (perso, danneggiato, uso personale) con motivazione
  - Nota: le righe con movimenti (vendite, grading, rettifiche) restano bloccate in modifica; le vendite già registrate mantengono il costo unitario del momento

## Fase 4 – Vendite e piattaforme
- [x] CRUD piattaforme con configurazione commissioni
- [x] Registrazione vendita multi-riga con calcolo automatico di commissioni e netto (modificabili a mano)
- [x] Allocazione FIFO automatica sulle righe d'acquisto (con possibilità di scegliere il lotto manualmente)
- [x] Gestione resi/annullamenti (ripristino giacenza e storno dei valori)
- [x] Storico vendite con filtri per periodo, piattaforma, categoria, brand
  - Nota: commissioni calcolate su merce + spedizione incassata; il reso è sull'intera vendita (i costi rimasti a carico sono una perdita); la modifica di una vendita rialloca la merce al costo attuale dei lotti

## Fase 5 – Calcoli economici
- [x] Servizio `CalcoloEconomicoService`:
  - Netto = lordo + spedizione incassata − commissioni piattaforma − commissioni pagamento − spedizione reale − imballaggio
  - Profitto = netto − costo del venduto (FIFO, costi accessori e grading inclusi)
  - ROI % = profitto / costo del venduto × 100
  - Margine % = profitto / lordo × 100
- [x] ROI per singola vendita, articolo, lotto d'acquisto, brand, categoria, piattaforma e periodo
- [x] Test unitari dedicati ai calcoli (casi limite: quantità parziali, resi, costo zero, lotti misti)
  - Nota: nelle vendite con più articoli netto e costi dei resi sono ripartiti sulle righe in proporzione all'incasso; pagina `/report` con riepilogo per periodo e risultato economico nella pagina di ogni acquisto

## Fase 6 – Dashboard e Hot Items
- [x] KPI principali (filtrabili per periodo): fatturato lordo, netto, profitto, ROI medio, n° vendite, valore magazzino, capitale investito, tempo medio di vendita
- [x] Grafici (Chart.js): andamento mensile profitto/fatturato, ripartizione per piattaforma, per categoria, per brand
- [x] **Hot Items**:
  - Top articoli per quantità venduta
  - Top articoli per profitto e per ROI
  - Top brand e top categorie (quantità, profitto)
  - Velocità di vendita (giorni medi dall'acquisto alla vendita)
- [x] Avvisi: articoli fermi in magazzino da più di N giorni, articoli venduti in perdita
- [x] Query di aggregazione con proiezioni JPA/JPQL o SQL nativo (eventuali viste DB per i report pesanti)
  - Nota: periodo predefinito ultimi 12 mesi; soglia "merce ferma" selezionabile (30–365 giorni); valore magazzino sempre alla data di oggi

## Fase 7 – Utility
- [x] Export CSV/Excel di acquisti, vendite e magazzino (Apache POI)
- [x] Import CSV per caricamento iniziale dell'inventario esistente
- [x] Report fiscale annuale riassuntivo (totali acquisti/vendite/profitto per anno)
- [x] Script di backup/restore del DB (`pg_dump`) documentato nel README
- [x] Ricerca globale rapida (nome articolo, n° certificato, n° set)
  - Nota: script di backup/ripristino provati dal vivo (PowerShell e bash); la ricerca nel catalogo cerca anche negli attributi

## Fase 8 – Qualità e rilascio
- [x] Test di integrazione con Testcontainers (PostgreSQL) per repository e servizi
- [x] Test MVC per controller principali
- [x] Gestione errori centralizzata (`@ControllerAdvice`) e messaggi in italiano
- [x] Dockerfile dell'applicazione + `docker-compose` completo (app + db)
- [x] Dati demo di esempio (profilo `demo`) per provare la dashboard

> Note Fase 8: `mvnw verify` con Testcontainers (PostgreSQL 16) verde; immagine Docker e servizio `app` provati dal vivo
> (profilo prod, utente non root, upload in `data/uploads`). Dati demo nello schema separato `demo`.
> Aggiunte: pagina **Spese generali** (`/spese`, con export) e test che apre tutte le pagine senza transazione.

---

## Verifica end-to-end
- [x] `docker compose up -d` avvia PostgreSQL; `mvn spring-boot:run` avvia l'app e Flyway crea lo schema
- [x] `mvn verify` esegue unit test + test di integrazione Testcontainers tutti verdi
- [x] Scenario manuale: creare un acquisto misto (es. 10 carte Pokémon + 1 LEGO con spedizione) → verificare costo unitario effettivo → vendere 3 carte su Cardmarket e il LEGO su eBay → controllare netto, profitto e ROI calcolati → dashboard e hot items aggiornati
- [x] Scenario reso: annullare una vendita e verificare ripristino giacenza e KPI
- [x] Scenario grading: inviare una carta a PSA, rientro come gradata, vendita e ROI che include il costo di grading

> Esito verifica (13/10/2026), valori controllati a mano: costi unitari 5,50 € (carte) e 165,00 € (LEGO);
> vendita Cardmarket netto 33,88 €, profitto 17,38 €; LEGO su eBay netto 237,15 €, profitto 72,15 €, ROI 43,7%;
> dopo l'annullamento giacenza carte tornata a 10 e KPI ricalcolati; carta gradata PSA 9 con costo 30,50 €
> (5,50 + 25 di grading), venduta a 120 € con profitto 73,55 € e ROI 241,1%. Backup e ripristino provati.

---

## Versione tazze (branch `tazze`)
Stesso gestionale, dedicato a tazze e articoli simili venduti sui marketplace (Vinted, eBay, Etsy, Subito, …).
- [x] Categorie: Tazza, Tazza termica / Borraccia, Bicchiere, Set / Confezione regalo, Accessori, Altro
- [x] Attributi: materiale, capacità (ml), colore, soggetto/tema, forma (tazze), n° pezzi e contenuto (set), tipo (accessori)
- [x] Condizioni da marketplace generalista (nuovo con/senza confezione, come nuovo, ottimo, buono, con difetti)
- [x] Brand/licenze iniziali (Disney, Harry Potter, Marvel, Star Wars, Pokémon, Hello Kitty, Friends, Thun, Ikea, Senza licenza); Etsy al posto di Cardmarket
- [x] Grading rimosso (pagine, stato "In grading", tabella, voce nel conto annuale); spesa "Pubblicità / promozioni" al posto di "Grading"
- [x] Campo lingua tolto da form e scheda articolo
- [x] Migrazione `V7__gestionale_tazze.sql`; dati demo con catalogo di tazze
- [x] Docker separato: progetto `compravendita-sw-tazze` (container `compravendita-sw-tazze-db`/`-app`, volume proprio) che con `docker compose up -d --build` avvia database e applicazione; `docker-compose.dev.yml` con il solo database per lo sviluppo; script di backup e operazione pianificata con nomi propri
- [x] Prezzi suggeriti per il regime forfettario agevolato: prezzo minimo (in pari a fine anno, con la quota dei contributi INPS fissi) e consigliato (più il margine) per ogni riga d'acquisto, nel form, nel dettaglio e nei lotti; valori INPS 2026 dalla circolare n. 14/2026 con riduzione del 35%; parametri, simulazione e conteggio annuo in *Prezzi e tasse* (migrazione `V8__parametri_prezzo.sql`)

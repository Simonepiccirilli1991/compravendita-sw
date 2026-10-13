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
- [x] Struttura package per feature: `catalogo`, `acquisti`, `magazzino`, `vendite`, `piattaforme`, `report`, `dashboard`, `common`
- [x] `docker-compose.yml` con PostgreSQL 16 + volume persistente (e pgAdmin opzionale)
- [x] Profili `application-dev.yml` / `application-test.yml`
- [x] Layout Thymeleaf base (sidebar, navbar, Bootstrap 5, HTMX, Chart.js da CDN/webjars)
- [x] Login utente singolo (credenziali da variabili d'ambiente)
- [x] `git init` + `.gitignore` + README con istruzioni di avvio

## Fase 1 – Modello dati e migrazioni
- [ ] **Categoria** (enum): `CARTA_SINGOLA`, `CARTA_GRADATA`, `SEALED_TCG`, `VIDEOGIOCO`, `LEGO`, `ALTRO`
- [ ] **Brand/Franchise** (tabella): Pokémon, One Piece, Nintendo, Sony, Sega, LEGO, … (estendibile da UI)
- [ ] **Articolo** (catalogo): nome, categoria, brand, lingua, condizione, note, foto (path), `attributi JSONB`
  - Carte: set, numero, rarità, edizione
  - Gradate: ente (PSA/BGS/CGC/…), voto, n° certificato
  - Videogiochi: console, regione (PAL/NTSC/JP), completezza (Loose/CIB/Sealed), retro/nuovo
  - LEGO: numero set, tema, anno, stato scatola
- [ ] **Acquisto** (testata): data, fornitore/canale, costi accessori (spedizione, commissioni, dogana), note, allegato ricevuta
- [ ] **RigaAcquisto**: articolo, quantità, prezzo unitario, costo unitario effettivo (con ripartizione dei costi accessori), quantità residua
- [ ] **Piattaforma**: nome (eBay, Vinted, Cardmarket, Subito, Wallapop, Fiera, Privato, …), fee %, fee fissa, fee pagamento %
- [ ] **Vendita** (testata): data, piattaforma, acquirente/riferimento ordine, prezzo lordo, spedizione incassata, costo spedizione reale, imballaggio, commissioni (calcolate e modificabili), netto
- [ ] **RigaVendita** + **AllocazioneVendita** (collega la vendita alle righe d'acquisto con logica FIFO → costo del venduto)
- [ ] **Spesa** generica (grading, materiali, abbonamenti) collegabile o meno a un articolo
- [ ] Migrazioni Flyway `V1__schema.sql`, `V2__seed_piattaforme_brand.sql`
- [ ] Indici per le query di report (data vendita, articolo, brand, categoria, piattaforma)

## Fase 2 – Catalogo articoli
- [ ] CRUD articoli con form dinamico in base alla categoria (campi JSONB via HTMX)
- [ ] CRUD brand
- [ ] Lista articoli con ricerca, filtri (categoria, brand, condizione) e paginazione
- [ ] Upload foto articolo (salvataggio su filesystem locale)
- [ ] Validazioni (Bean Validation + validatori per attributi di categoria)

## Fase 3 – Acquisti e magazzino
- [ ] Registrazione acquisto multi-riga (anche lotti misti: es. lotto di carte + videogiochi)
- [ ] Ripartizione costi accessori sulle righe (proporzionale al prezzo o per quantità – scelta per acquisto)
- [ ] Stato oggetto: `IN_ARRIVO`, `IN_STOCK`, `IN_GRADING`, `IN_VENDITA`, `VENDUTO`, `RESO`
- [ ] Flusso grading: invio carta singola → rientro come gradata (costo grading aggiunto al costo dell'oggetto)
- [ ] Vista magazzino: giacenze per articolo, valore a costo, giorni di giacenza
- [ ] Rettifiche inventario (perso, danneggiato, uso personale) con motivazione

## Fase 4 – Vendite e piattaforme
- [ ] CRUD piattaforme con configurazione commissioni
- [ ] Registrazione vendita multi-riga con calcolo automatico di commissioni e netto (modificabili a mano)
- [ ] Allocazione FIFO automatica sulle righe d'acquisto (con possibilità di scegliere il lotto manualmente)
- [ ] Gestione resi/annullamenti (ripristino giacenza e storno dei valori)
- [ ] Storico vendite con filtri per periodo, piattaforma, categoria, brand

## Fase 5 – Calcoli economici
- [ ] Servizio `CalcoloEconomicoService`:
  - Netto = lordo + spedizione incassata − commissioni piattaforma − commissioni pagamento − spedizione reale − imballaggio
  - Profitto = netto − costo del venduto (FIFO, costi accessori e grading inclusi)
  - ROI % = profitto / costo del venduto × 100
  - Margine % = profitto / lordo × 100
- [ ] ROI per singola vendita, articolo, lotto d'acquisto, brand, categoria, piattaforma e periodo
- [ ] Test unitari dedicati ai calcoli (casi limite: quantità parziali, resi, costo zero, lotti misti)

## Fase 6 – Dashboard e Hot Items
- [ ] KPI principali (filtrabili per periodo): fatturato lordo, netto, profitto, ROI medio, n° vendite, valore magazzino, capitale investito, tempo medio di vendita
- [ ] Grafici (Chart.js): andamento mensile profitto/fatturato, ripartizione per piattaforma, per categoria, per brand
- [ ] **Hot Items**:
  - Top articoli per quantità venduta
  - Top articoli per profitto e per ROI
  - Top brand e top categorie (quantità, profitto)
  - Velocità di vendita (giorni medi dall'acquisto alla vendita)
- [ ] Avvisi: articoli fermi in magazzino da più di N giorni, articoli venduti in perdita
- [ ] Query di aggregazione con proiezioni JPA/JPQL o SQL nativo (eventuali viste DB per i report pesanti)

## Fase 7 – Utility
- [ ] Export CSV/Excel di acquisti, vendite e magazzino (Apache POI)
- [ ] Import CSV per caricamento iniziale dell'inventario esistente
- [ ] Report fiscale annuale riassuntivo (totali acquisti/vendite/profitto per anno)
- [ ] Script di backup/restore del DB (`pg_dump`) documentato nel README
- [ ] Ricerca globale rapida (nome articolo, n° certificato, n° set)

## Fase 8 – Qualità e rilascio
- [ ] Test di integrazione con Testcontainers (PostgreSQL) per repository e servizi
- [ ] Test MVC per controller principali
- [ ] Gestione errori centralizzata (`@ControllerAdvice`) e messaggi in italiano
- [ ] Dockerfile dell'applicazione + `docker-compose` completo (app + db)
- [ ] Dati demo di esempio (profilo `demo`) per provare la dashboard

---

## Verifica end-to-end
- [ ] `docker compose up -d` avvia PostgreSQL; `mvn spring-boot:run` avvia l'app e Flyway crea lo schema
- [ ] `mvn verify` esegue unit test + test di integrazione Testcontainers tutti verdi
- [ ] Scenario manuale: creare un acquisto misto (es. 10 carte Pokémon + 1 LEGO con spedizione) → verificare costo unitario effettivo → vendere 3 carte su Cardmarket e il LEGO su eBay → controllare netto, profitto e ROI calcolati → dashboard e hot items aggiornati
- [ ] Scenario reso: annullare una vendita e verificare ripristino giacenza e KPI
- [ ] Scenario grading: inviare una carta a PSA, rientro come gradata, vendita e ROI che include il costo di grading

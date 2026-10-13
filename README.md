# Gestionale Compravendita

Gestionale per tracciare acquisti, magazzino e vendite di carte (Pokémon, One Piece – singole, gradate, sealed),
videogiochi, LEGO e altri oggetti, con calcolo di netto, ROI e dashboard. Il piano di sviluppo è in [PIANO.md](PIANO.md).

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

## Test
```bash
./mvnw verify
```
I test di integrazione usano Testcontainers: richiedono Docker in esecuzione.

## Struttura
```
src/main/java/it/compravendita/gestionale/
  catalogo/  acquisti/  magazzino/  vendite/  piattaforme/  report/  dashboard/  common/
src/main/resources/
  db/migration/   migrazioni Flyway
  templates/      pagine Thymeleaf (fragments/layout.html = layout comune)
```

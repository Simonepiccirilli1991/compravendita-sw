package it.compravendita.gestionale.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cartelle usate dalla versione installata: tutto sta sotto una sola cartella dati per utente
 * ({@code %LOCALAPPDATA%\GestionaleTazze}), separata dalla cartella del programma, così
 * disinstallare o aggiornare il programma non tocca i dati.
 *
 * @param base cartella dati
 */
public record CartelleDesktop(Path base) {

	static final String NOME_CARTELLA = "GestionaleTazze";

	/** Cartella dati predefinita, sostituibile con {@code -Dgestionale.dati=...}. */
	public static CartelleDesktop predefinite() {
		String indicata = System.getProperty("gestionale.dati");
		if (indicata != null && !indicata.isBlank()) {
			return new CartelleDesktop(Path.of(indicata));
		}
		String localAppData = System.getenv("LOCALAPPDATA");
		Path radice = localAppData != null ? Path.of(localAppData) : Path.of(System.getProperty("user.home"));
		return new CartelleDesktop(radice.resolve(NOME_CARTELLA));
	}

	/** Dati di PostgreSQL (PGDATA). */
	public Path database() {
		return base.resolve("database");
	}

	/** Foto degli articoli e ricevute. */
	public Path uploads() {
		return base.resolve("uploads");
	}

	public Path log() {
		return base.resolve("log");
	}

	/** Impostazioni modificabili dall'utente (porta, credenziali, cartella dei backup). */
	public Path configurazione() {
		return base.resolve("gestionale.properties");
	}

	/** Password del database, generata alla prima installazione. */
	public Path credenzialiDatabase() {
		return base.resolve("database.properties");
	}

	/** Indirizzo dell'istanza in esecuzione, letto da un secondo avvio per aprire il browser. */
	public Path indirizzo() {
		return base.resolve("indirizzo.txt");
	}

	public Path lock() {
		return base.resolve("istanza.lock");
	}

	public void crea() throws IOException {
		Files.createDirectories(base);
		Files.createDirectories(uploads());
		Files.createDirectories(log());
	}

}

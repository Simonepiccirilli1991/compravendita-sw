package it.compravendita.gestionale.desktop;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PostgreSQL incluso nel programma: alla prima esecuzione crea la cartella dati con initdb, poi a ogni
 * avvio lo fa partire con pg_ctl su una porta libera (solo 127.0.0.1) e lo ferma alla chiusura.
 */
public class PostgresLocale {

	public static final String UTENTE = "compravendita";

	public static final String DATABASE = "compravendita";

	private static final long LOG_MASSIMO = 10L * 1024 * 1024;

	private final Path bin;

	private final Path dati;

	private final Path cartellaLog;

	private final String password;

	private int porta;

	private boolean avviato;

	/**
	 * @param pgsql cartella con l'installazione di PostgreSQL (bin, lib, share)
	 * @param dati cartella dei dati (PGDATA)
	 * @param cartellaLog cartella per il log del server
	 * @param password password dell'utente {@value #UTENTE}
	 */
	public PostgresLocale(Path pgsql, Path dati, Path cartellaLog, String password) {
		this.bin = pgsql.resolve("bin");
		this.dati = dati;
		this.cartellaLog = cartellaLog;
		this.password = password;
	}

	/** Legge la password del database dal file indicato, generandola alla prima esecuzione. */
	public static String password(Path file) throws IOException {
		String chiave = "password=";
		if (Files.exists(file)) {
			for (String riga : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (riga.startsWith(chiave)) {
					return riga.substring(chiave.length()).strip();
				}
			}
		}
		byte[] casuali = new byte[24];
		new SecureRandom().nextBytes(casuali);
		String generata = Base64.getUrlEncoder().withoutPadding().encodeToString(casuali);
		Files.writeString(file, "# Password interna del database: non modificarla" + System.lineSeparator() + chiave
				+ generata + System.lineSeparator(), StandardCharsets.UTF_8);
		return generata;
	}

	public synchronized void avvia() throws IOException, InterruptedException {
		if (!Files.exists(bin.resolve("pg_ctl.exe")) && !Files.exists(bin.resolve("pg_ctl"))) {
			throw new IOException("PostgreSQL non trovato in " + bin);
		}
		if (!Files.exists(dati.resolve("PG_VERSION"))) {
			inizializza();
		}
		// dopo una chiusura improvvisa il server può essere rimasto acceso con la porta vecchia
		if (inEsecuzione()) {
			esegui("pg_ctl", "stop", "-D", dati.toString(), "-m", "fast", "-w", "-t", "60");
		}
		Path fileLog = cartellaLog.resolve("postgres.log");
		if (Files.exists(fileLog) && Files.size(fileLog) > LOG_MASSIMO) {
			Files.move(fileLog, cartellaLog.resolve("postgres.old.log"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		porta = portaLibera();
		// il server avviato da pg_ctl eredita le maniglie dei file: l'output di pg_ctl start va scartato
		// (altrimenti il file resterebbe bloccato) e in caso di errore si mostra la fine del log del server
		Esito avvio = processo(List.of("pg_ctl", "start", "-D", dati.toString(), "-w", "-t", "120", "-l",
				fileLog.toString(), "-o", "-p " + porta + " -c listen_addresses=127.0.0.1"), false);
		if (avvio.codice() != 0) {
			throw new IOException("Avvio di PostgreSQL non riuscito (codice " + avvio.codice() + "): " + fineLog(fileLog));
		}
		avviato = true;
		creaDatabaseSeManca();
	}

	public synchronized void ferma() {
		if (!avviato) {
			return;
		}
		try {
			esegui("pg_ctl", "stop", "-D", dati.toString(), "-m", "fast", "-w", "-t", "60");
		}
		catch (IOException ex) {
			System.err.println("Arresto di PostgreSQL non riuscito: " + ex.getMessage());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		avviato = false;
	}

	public int porta() {
		return porta;
	}

	public String password() {
		return password;
	}

	public Path bin() {
		return bin;
	}

	public String jdbcUrl(String database) {
		return "jdbc:postgresql://127.0.0.1:" + porta + "/" + database;
	}

	private void inizializza() throws IOException, InterruptedException {
		Files.createDirectories(dati.getParent());
		Path filePassword = Files.createTempFile(dati.getParent(), "pw", ".txt");
		try {
			Files.writeString(filePassword, password, StandardCharsets.UTF_8);
			// collazione ICU italiana: ordinamento alfabetico corretto senza dipendere dalle impostazioni di Windows
			esegui("initdb", "-D", dati.toString(), "-U", UTENTE, "--pwfile=" + filePassword, "-A", "scram-sha-256",
					"-E", "UTF8", "--locale-provider=icu", "--icu-locale=it-IT", "--locale=C", "--no-instructions");
		}
		finally {
			Files.deleteIfExists(filePassword);
		}
	}

	private boolean inEsecuzione() throws IOException, InterruptedException {
		if (!Files.exists(dati.resolve("postmaster.pid"))) {
			return false;
		}
		return processo(List.of("pg_ctl", "status", "-D", dati.toString()), true).codice() == 0;
	}

	private void creaDatabaseSeManca() throws IOException {
		try (Connection connessione = DriverManager.getConnection(jdbcUrl("postgres"), UTENTE, password);
				PreparedStatement cerca = connessione.prepareStatement("select 1 from pg_database where datname = ?")) {
			cerca.setString(1, DATABASE);
			try (ResultSet risultato = cerca.executeQuery()) {
				if (risultato.next()) {
					return;
				}
			}
			try (Statement crea = connessione.createStatement()) {
				crea.execute("create database " + DATABASE);
			}
		}
		catch (SQLException ex) {
			throw new IOException("Impossibile creare il database: " + ex.getMessage(), ex);
		}
	}

	/** Esegue un programma di PostgreSQL e solleva un'eccezione con il suo output se fallisce. */
	void esegui(String... comando) throws IOException, InterruptedException {
		Esito esito = processo(List.of(comando), true);
		if (esito.codice() != 0) {
			throw new IOException(comando[0] + " " + comando[1] + " non riuscito (codice " + esito.codice() + "): "
					+ esito.output().strip());
		}
	}

	private Esito processo(List<String> comando, boolean leggiOutput) throws IOException, InterruptedException {
		List<String> completo = new ArrayList<>(comando);
		completo.set(0, bin.resolve(comando.getFirst()).toString());
		Files.createDirectories(cartellaLog);
		// output su file e non su pipe: una pipe ereditata dal server non verrebbe mai chiusa
		Path uscita = leggiOutput ? Files.createTempFile(cartellaLog, "pg", ".txt") : null;
		try {
			ProcessBuilder builder = new ProcessBuilder(completo).redirectErrorStream(true)
				.redirectOutput(uscita != null ? ProcessBuilder.Redirect.to(uscita.toFile()) : ProcessBuilder.Redirect.DISCARD)
				.redirectInput(ProcessBuilder.Redirect.from(nul()));
			// messaggi in inglese (ASCII): quelli tradotti usano la codepage della console
			builder.environment().put("LC_MESSAGES", "C");
			Process processo = builder.start();
			if (!processo.waitFor(5, TimeUnit.MINUTES)) {
				processo.destroyForcibly();
				throw new IOException(comando.getFirst() + " non ha risposto in tempo");
			}
			String output = uscita != null ? new String(Files.readAllBytes(uscita), StandardCharsets.UTF_8) : "";
			return new Esito(processo.exitValue(), output);
		}
		finally {
			if (uscita != null) {
				Files.deleteIfExists(uscita);
			}
		}
	}

	private static String fineLog(Path fileLog) {
		try {
			List<String> righe = Files.readAllLines(fileLog, StandardCharsets.ISO_8859_1);
			return String.join(System.lineSeparator(), righe.subList(Math.max(0, righe.size() - 5), righe.size()));
		}
		catch (IOException ex) {
			return "vedi " + fileLog;
		}
	}

	private static java.io.File nul() {
		return new java.io.File(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
	}

	static int portaLibera() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
			return socket.getLocalPort();
		}
	}

	private record Esito(int codice, String output) {
	}

}

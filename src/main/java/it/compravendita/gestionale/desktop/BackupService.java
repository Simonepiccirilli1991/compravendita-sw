package it.compravendita.gestionale.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.zaxxer.hikari.HikariDataSource;

import it.compravendita.gestionale.common.AppProperties;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;

/**
 * Backup e ripristino della versione installata, con pg_dump / pg_restore inclusi nel programma.
 * Un backup è un file zip con il database ({@code database.dump}, formato custom di pg_dump: lo stesso
 * degli script della versione Docker) e la cartella delle foto ({@code uploads/}).
 * <p>
 * All'avvio viene creato un backup automatico se l'ultimo ha più di un giorno; se ne tengono gli ultimi
 * {@value #AUTOMATICI_DA_TENERE}.
 */
@Service
@ConditionalOnProperty("app.desktop.pgsql-bin")
public class BackupService {

	static final int AUTOMATICI_DA_TENERE = 14;

	static final String DUMP = "database.dump";

	static final String CARTELLA_UPLOADS = "uploads/";

	private static final Logger log = LoggerFactory.getLogger(BackupService.class);

	private static final DateTimeFormatter FORMATO_NOME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

	private static final Pattern NOME_VALIDO = Pattern.compile("[A-Za-z0-9_.-]+\\.zip");

	private final Path bin;

	private final Path cartella;

	private final Path uploads;

	private final String url;

	private final String utente;

	private final String password;

	private final DataSource dataSource;

	private final ObjectProvider<Flyway> flyway;

	private final boolean automatico;

	public BackupService(@Value("${app.desktop.pgsql-bin}") Path bin,
			@Value("${app.desktop.cartella-backup}") Path cartella, AppProperties appProperties,
			JdbcConnectionDetails connessione, DataSource dataSource,
			ObjectProvider<Flyway> flyway, @Value("${app.desktop.backup-automatico:true}") boolean automatico) {
		this.bin = bin;
		this.cartella = cartella;
		this.uploads = appProperties.uploadDir().toAbsolutePath().normalize();
		// URL per pg_dump/pg_restore: stesso indirizzo, senza i parametri del driver JDBC
		this.url = connessione.getJdbcUrl().replaceFirst("^jdbc:", "").replaceFirst("\\?.*$", "");
		this.utente = connessione.getUsername();
		this.password = connessione.getPassword();
		this.dataSource = dataSource;
		this.flyway = flyway;
		this.automatico = automatico;
	}

	public record Backup(String nome, long dimensione, LocalDateTime data, boolean automatico) {
	}

	public Path cartella() {
		return cartella;
	}

	public List<Backup> elenco() {
		if (!Files.isDirectory(cartella)) {
			return List.of();
		}
		try (Stream<Path> file = Files.list(cartella)) {
			return file.filter(p -> NOME_VALIDO.matcher(p.getFileName().toString()).matches())
				.map(BackupService::descrivi)
				.sorted(Comparator.comparing(Backup::data).reversed())
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Percorso di un backup esistente, controllando che il nome non esca dalla cartella. */
	public Path file(String nome) {
		if (nome == null || !NOME_VALIDO.matcher(nome).matches() || !Files.isRegularFile(cartella.resolve(nome))) {
			throw new OperazioneNonConsentitaException("Backup non trovato: " + nome);
		}
		return cartella.resolve(nome);
	}

	/**
	 * Crea un backup completo.
	 *
	 * @param tipo parte del nome del file: "manuale", "automatico", "prima-del-ripristino"
	 */
	public synchronized Backup crea(String tipo) {
		try {
			Files.createDirectories(cartella);
			String nome = "backup-" + tipo + "-" + LocalDateTime.now().format(FORMATO_NOME) + ".zip";
			Path dump = Files.createTempFile(cartella, "dump", ".tmp");
			Path parziale = cartella.resolve(nome + ".tmp");
			try {
				esegui("pg_dump", "-Fc", "-f", dump.toString(), "--dbname=" + url);
				try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(parziale))) {
					zip.putNextEntry(new ZipEntry(DUMP));
					Files.copy(dump, zip);
					zip.closeEntry();
					aggiungiUploads(zip);
				}
				Path destinazione = cartella.resolve(nome);
				Files.move(parziale, destinazione, StandardCopyOption.REPLACE_EXISTING);
				log.info("Backup creato: {}", destinazione);
				return descrivi(destinazione);
			}
			finally {
				Files.deleteIfExists(dump);
				Files.deleteIfExists(parziale);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Backup non riuscito: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Ripristina un backup: un file zip creato dal gestionale oppure un {@code database.dump} della versione
	 * Docker. Prima salva lo stato attuale in un backup "prima-del-ripristino".
	 */
	public synchronized void ripristina(Path file) {
		try {
			boolean zip = contiene(file, DUMP);
			if (!zip && !inizioDump(file)) {
				throw new OperazioneNonConsentitaException(
						"Il file non è un backup del gestionale (serve un .zip creato qui o un database.dump)");
			}
			crea("prima-del-ripristino");
			Path dump = file;
			Path estratto = null;
			if (zip) {
				estratto = Files.createTempFile(cartella, "ripristino", ".dump");
				estraiDump(file, estratto);
				dump = estratto;
			}
			try {
				esegui("pg_restore", "--clean", "--if-exists", "--no-owner", "--no-privileges", "--single-transaction",
						"--exit-on-error", "--dbname=" + url, dump.toString());
			}
			finally {
				if (estratto != null) {
					Files.deleteIfExists(estratto);
				}
			}
			if (zip) {
				ripristinaUploads(file);
			}
			// un backup di una versione precedente viene portato allo schema attuale
			flyway.ifAvailable(Flyway::migrate);
			if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
				hikari.getHikariPoolMXBean().softEvictConnections();
			}
			log.info("Backup ripristinato: {}", file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Ripristino non riuscito: " + ex.getMessage(), ex);
		}
	}

	/** Backup automatico all'avvio, in un thread a parte per non rallentare l'apertura. */
	@EventListener(ApplicationReadyEvent.class)
	public void backupAutomatico() {
		if (!automatico) {
			return;
		}
		Thread.ofVirtual().name("backup-automatico").start(() -> {
			try {
				if (serveBackupAutomatico()) {
					crea("automatico");
					eliminaAutomaticiVecchi();
				}
			}
			catch (RuntimeException ex) {
				log.error("Backup automatico non riuscito", ex);
			}
		});
	}

	boolean serveBackupAutomatico() {
		return elenco().stream()
			.filter(Backup::automatico)
			.findFirst()
			.map(b -> Duration.between(b.data(), LocalDateTime.now()).toHours() >= 20)
			.orElse(true);
	}

	void eliminaAutomaticiVecchi() {
		List<Backup> automatici = elenco().stream().filter(Backup::automatico).toList();
		for (Backup vecchio : automatici.subList(Math.min(AUTOMATICI_DA_TENERE, automatici.size()),
				automatici.size())) {
			try {
				Files.deleteIfExists(cartella.resolve(vecchio.nome()));
			}
			catch (IOException ex) {
				log.warn("Impossibile eliminare il backup {}", vecchio.nome(), ex);
			}
		}
	}

	private void aggiungiUploads(ZipOutputStream zip) throws IOException {
		if (!Files.isDirectory(uploads)) {
			return;
		}
		List<Path> file;
		try (Stream<Path> tutti = Files.walk(uploads)) {
			file = tutti.filter(Files::isRegularFile).toList();
		}
		for (Path p : file) {
			zip.putNextEntry(new ZipEntry(CARTELLA_UPLOADS + uploads.relativize(p).toString().replace('\\', '/')));
			Files.copy(p, zip);
			zip.closeEntry();
		}
	}

	private static boolean contiene(Path file, String voce) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
			for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
				if (e.getName().equals(voce)) {
					return true;
				}
			}
		}
		catch (java.util.zip.ZipException ex) {
			return false;
		}
		return false;
	}

	/** I dump in formato custom iniziano con "PGDMP". */
	private static boolean inizioDump(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return new String(in.readNBytes(5), StandardCharsets.US_ASCII).equals("PGDMP");
		}
	}

	private static void estraiDump(Path file, Path destinazione) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
			for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
				if (e.getName().equals(DUMP)) {
					Files.copy(zip, destinazione, StandardCopyOption.REPLACE_EXISTING);
					return;
				}
			}
		}
	}

	/** Rimette le foto del backup: quelle con lo stesso nome vengono sovrascritte, le altre restano. */
	private void ripristinaUploads(Path file) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
			for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
				if (e.isDirectory() || !e.getName().startsWith(CARTELLA_UPLOADS)) {
					continue;
				}
				Path destinazione = uploads.resolve(e.getName().substring(CARTELLA_UPLOADS.length())).normalize();
				if (!destinazione.startsWith(uploads)) {
					continue;
				}
				Files.createDirectories(destinazione.getParent());
				try (OutputStream out = Files.newOutputStream(destinazione)) {
					zip.transferTo(out);
				}
			}
		}
	}

	private void esegui(String programma, String... argomenti) throws IOException {
		List<String> comando = new ArrayList<>();
		comando.add(bin.resolve(programma).toString());
		comando.addAll(List.of(argomenti));
		Path uscita = Files.createTempFile(cartella, "pg", ".txt");
		try {
			ProcessBuilder builder = new ProcessBuilder(comando).redirectErrorStream(true)
				.redirectOutput(uscita.toFile());
			builder.environment().put("PGUSER", utente);
			builder.environment().put("PGPASSWORD", password);
			Process processo = builder.start();
			if (!processo.waitFor(30, TimeUnit.MINUTES)) {
				processo.destroyForcibly();
				throw new IOException(programma + " non ha terminato in tempo");
			}
			if (processo.exitValue() != 0) {
				throw new IOException(programma + ": "
						+ new String(Files.readAllBytes(uscita), StandardCharsets.UTF_8).strip());
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException(programma + " interrotto", ex);
		}
		finally {
			Files.deleteIfExists(uscita);
		}
	}

	private static Backup descrivi(Path file) {
		try {
			String nome = file.getFileName().toString();
			Instant modificato = Files.getLastModifiedTime(file).toInstant();
			return new Backup(nome, Files.size(file), LocalDateTime.ofInstant(modificato, ZoneId.systemDefault()),
					nome.startsWith("backup-automatico-"));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}

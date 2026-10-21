package it.compravendita.gestionale.desktop;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import javax.swing.filechooser.FileSystemView;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import it.compravendita.gestionale.GestionaleApplication;

/**
 * Punto di avvio della versione installata (installer Windows): avvia il PostgreSQL incluso, poi
 * l'applicazione Spring Boot su 127.0.0.1, apre il browser e lascia un'icona vicino all'orologio per
 * riaprire o chiudere il programma. Un secondo avvio mentre il programma è aperto apre solo il browser.
 * <p>
 * La cartella di PostgreSQL arriva da {@code -Dgestionale.pgsql=...} (impostata dall'installer).
 */
public final class AvvioDesktop {

	public static final String NOME = "Gestionale Tazze";

	private static volatile ConfigurableApplicationContext contesto;

	private static volatile PostgresLocale postgres;

	/** Tenuto in un campo statico perché resti acquisito per tutta la vita del programma. */
	private static FileLock istanza;

	private AvvioDesktop() {
	}

	public static void main(String[] args) {
		CartelleDesktop cartelle = CartelleDesktop.predefinite();
		FinestraAvvio finestra = null;
		Messaggi.prepara();
		try {
			cartelle.crea();
			istanza = acquisisciIstanza(cartelle.lock());
			if (istanza == null) {
				apriIstanzaEsistente(cartelle);
				return;
			}
			Files.deleteIfExists(cartelle.indirizzo());
			ConfigurazioneDesktop configurazione = configurazione(cartelle);
			finestra = FinestraAvvio.mostra();

			String pgsql = System.getProperty("gestionale.pgsql");
			if (pgsql == null || pgsql.isBlank()) {
				throw new IOException("Cartella di PostgreSQL non indicata (-Dgestionale.pgsql)");
			}
			postgres = new PostgresLocale(Path.of(pgsql), cartelle.database(), cartelle.log(),
					PostgresLocale.password(cartelle.credenzialiDatabase()));
			Runtime.getRuntime().addShutdownHook(new Thread(AvvioDesktop::arresta, "arresto-gestionale"));
			finestra.stato("Avvio del database…");
			postgres.avvia();

			finestra.stato("Avvio del gestionale…");
			int porta = portaWeb(configurazione.porta());
			impostaProprieta(cartelle, configurazione, porta);
			SpringApplication applicazione = new SpringApplication(GestionaleApplication.class);
			applicazione.setRegisterShutdownHook(false);
			contesto = applicazione.run(args);

			String indirizzo = "http://127.0.0.1:" + porta + "/";
			Files.writeString(cartelle.indirizzo(), indirizzo, StandardCharsets.UTF_8);
			IconaVassoio.installa(indirizzo, cartelle, configurazione.cartellaBackup());
			finestra.chiudi();
			Browser.apri(indirizzo);
		}
		catch (Throwable ex) {
			if (finestra != null) {
				finestra.chiudi();
			}
			Path log = registraErrore(cartelle, ex);
			Messaggi.errore("Il gestionale non è riuscito ad avviarsi.\n\n" + ex.getMessage()
					+ "\n\nDettagli nel file:\n" + log);
			System.exit(1);
		}
	}

	/** Chiude il programma (pulsante "Chiudi programma" della pagina web o menu dell'icona). */
	public static void chiudi() {
		Thread chiusura = new Thread(() -> {
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			System.exit(0);
		}, "chiusura-gestionale");
		chiusura.start();
	}

	private static void arresta() {
		ConfigurableApplicationContext attuale = contesto;
		if (attuale != null) {
			attuale.close();
		}
		PostgresLocale database = postgres;
		if (database != null) {
			database.ferma();
		}
	}

	/** Alla prima esecuzione chiede le credenziali d'accesso e crea il file delle impostazioni. */
	private static ConfigurazioneDesktop configurazione(CartelleDesktop cartelle) throws IOException {
		Path backupPredefinito = FileSystemView.getFileSystemView().getDefaultDirectory().toPath()
			.resolve(NOME).resolve("Backup");
		ConfigurazioneDesktop configurazione = ConfigurazioneDesktop.leggi(cartelle.configurazione(), backupPredefinito);
		if (!Files.exists(cartelle.configurazione())) {
			String[] credenziali = Messaggi.chiediCredenziali(cartelle.configurazione());
			if (credenziali != null) {
				configurazione = configurazione.conCredenziali(credenziali[0], credenziali[1]);
			}
			configurazione.scrivi(cartelle.configurazione());
		}
		return configurazione;
	}

	private static void impostaProprieta(CartelleDesktop cartelle, ConfigurazioneDesktop configurazione, int porta) {
		System.setProperty("spring.profiles.active", "prod,desktop");
		System.setProperty("spring.datasource.url", postgres.jdbcUrl(PostgresLocale.DATABASE));
		System.setProperty("spring.datasource.username", PostgresLocale.UTENTE);
		System.setProperty("spring.datasource.password", postgres.password());
		System.setProperty("server.address", "127.0.0.1");
		System.setProperty("server.port", Integer.toString(porta));
		System.setProperty("spring.security.user.name", configurazione.utente());
		System.setProperty("spring.security.user.password", configurazione.password());
		System.setProperty("app.upload-dir", cartelle.uploads().toString());
		System.setProperty("logging.file.name", cartelle.log().resolve("gestionale.log").toString());
		System.setProperty("app.desktop.pgsql-bin", postgres.bin().toString());
		System.setProperty("app.desktop.cartella-dati", cartelle.base().toString());
		System.setProperty("app.desktop.cartella-backup", configurazione.cartellaBackup().toString());
	}

	/** La porta indicata nelle impostazioni, oppure una libera se è occupata da un altro programma. */
	private static int portaWeb(int preferita) throws IOException {
		try (ServerSocket prova = new ServerSocket(preferita, 0, InetAddress.getByName("127.0.0.1"))) {
			return preferita;
		}
		catch (IOException occupata) {
			return PostgresLocale.portaLibera();
		}
	}

	/** Lock sul file dell'istanza: resta acquisito finché il programma è aperto. */
	@SuppressWarnings("resource")
	private static FileLock acquisisciIstanza(Path file) throws IOException {
		FileChannel canale = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		FileLock lock = canale.tryLock();
		if (lock == null) {
			canale.close();
		}
		return lock;
	}

	private static void apriIstanzaEsistente(CartelleDesktop cartelle) throws IOException {
		if (Files.exists(cartelle.indirizzo())) {
			Browser.apri(Files.readString(cartelle.indirizzo(), StandardCharsets.UTF_8).strip());
		}
		else {
			Messaggi.informazione(NOME + " si sta già avviando: la pagina si aprirà tra qualche secondo.");
		}
	}

	private static Path registraErrore(CartelleDesktop cartelle, Throwable ex) {
		Path log = cartelle.log().resolve("avvio.log");
		StringWriter dettagli = new StringWriter();
		ex.printStackTrace(new PrintWriter(dettagli));
		try {
			Files.createDirectories(cartelle.log());
			Files.writeString(log, LocalDateTime.now() + System.lineSeparator() + dettagli + System.lineSeparator(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ignorata) {
			// niente da fare: il messaggio a video resta
		}
		return log;
	}

	/** Apre un indirizzo nel browser predefinito. */
	static final class Browser {

		private Browser() {
		}

		static void apri(String indirizzo) {
			try {
				if (java.awt.Desktop.isDesktopSupported()
						&& java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
					java.awt.Desktop.getDesktop().browse(URI.create(indirizzo));
					return;
				}
				new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", indirizzo).start();
			}
			catch (IOException ex) {
				Messaggi.informazione("Apri il browser all'indirizzo " + indirizzo);
			}
		}

	}

}

package it.compravendita.gestionale.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurazioneDesktopTest {

	@TempDir
	Path cartella;

	@Test
	void senzaFileUsaIValoriPredefiniti() throws Exception {
		Path backup = cartella.resolve("Backup");
		ConfigurazioneDesktop configurazione = ConfigurazioneDesktop.leggi(cartella.resolve("manca.properties"), backup);

		assertThat(configurazione).isEqualTo(new ConfigurazioneDesktop(8181, "admin", "admin", backup));
	}

	@Test
	void scritturaELetturaConservanoIValori() throws Exception {
		Path file = cartella.resolve("gestionale.properties");
		ConfigurazioneDesktop scritta = new ConfigurazioneDesktop(9000, "mario", "p@ss=w#rd",
				Path.of("C:\\Users\\Mario Rossi\\Documents\\Backup"));
		scritta.scrivi(file);

		assertThat(ConfigurazioneDesktop.leggi(file, cartella)).isEqualTo(scritta);
	}

	@Test
	void percorsiWindowsECommentiModificatiAMano() throws Exception {
		Path file = cartella.resolve("gestionale.properties");
		Files.writeString(file, "\uFEFF# commento\r\nPORTA = 8282\r\nutente=anna\r\npassword=\r\n"
				+ "cartella_backup=D:\\Backup\\tazze\r\n", StandardCharsets.UTF_8);

		ConfigurazioneDesktop letta = ConfigurazioneDesktop.leggi(file, cartella);

		assertThat(letta.porta()).isEqualTo(8282);
		assertThat(letta.utente()).isEqualTo("anna");
		assertThat(letta.password()).isEqualTo("admin");
		assertThat(letta.cartellaBackup()).isEqualTo(Path.of("D:\\Backup\\tazze"));
	}

	@Test
	void portaNonValidaTornaAlValorePredefinito() throws Exception {
		Path file = cartella.resolve("gestionale.properties");
		Files.writeString(file, "porta=ottanta\n", StandardCharsets.UTF_8);

		assertThat(ConfigurazioneDesktop.leggi(file, cartella).porta()).isEqualTo(ConfigurazioneDesktop.PORTA_PREDEFINITA);
	}

	@Test
	void passwordDelDatabaseGenerataUnaVoltaSola() throws Exception {
		Path file = cartella.resolve("database.properties");

		String prima = PostgresLocale.password(file);
		String seconda = PostgresLocale.password(file);

		assertThat(prima).hasSizeGreaterThanOrEqualTo(30).isEqualTo(seconda);
	}

}

package it.compravendita.gestionale.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import it.compravendita.gestionale.TestcontainersConfiguration;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.Categoria;

/**
 * Backup e ripristino con pg_dump / pg_restore veri. Usa i programmi preparati da
 * installer/crea-installer.ps1 in target/installer: senza quelli il test viene saltato.
 * Contesto e database propri, perché il ripristino sostituisce tutto il database.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.upload-dir=target/test-uploads-backup",
		"app.desktop.pgsql-bin=" + BackupWebTest.BIN, "app.desktop.cartella-backup=target/test-backup",
		"app.desktop.backup-automatico=false" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@EnabledIf("programmiPresenti")
class BackupWebTest {

	static final String BIN = "target/installer/input/pgsql/bin";

	static final Path CARTELLA = Path.of("target/test-backup");

	@Autowired
	MockMvc mvc;

	@Autowired
	BackupService backupService;

	@Autowired
	ArticoloRepository articoloRepository;

	static boolean programmiPresenti() {
		return Files.exists(Path.of(BIN, "pg_dump.exe")) || Files.exists(Path.of(BIN, "pg_dump"));
	}

	@BeforeEach
	void svuotaCartella() throws IOException {
		Files.createDirectories(CARTELLA);
		try (Stream<Path> file = Files.list(CARTELLA)) {
			for (Path p : file.toList()) {
				Files.delete(p);
			}
		}
	}

	@Test
	void backupERipristinoRiportanoIDati() throws Exception {
		Path foto = Path.of("target/test-uploads-backup/articoli/tazza.jpg");
		Files.createDirectories(foto.getParent());
		Files.writeString(foto, "foto originale");
		Long id = articoloRepository.save(new Articolo("Tazza da salvare", Categoria.TAZZA, null)).getId();

		mvc.perform(post("/backup").with(csrf()))
			.andExpect(redirectedUrl("/backup"))
			.andExpect(flash().attribute("messaggio", containsString("backup-manuale-")));
		String nome = backupService.elenco().getFirst().nome();

		articoloRepository.deleteById(id);
		Files.writeString(foto, "foto cambiata");
		mvc.perform(post("/backup/{n}/ripristina", nome).with(csrf()))
			.andExpect(redirectedUrl("/backup"))
			.andExpect(flash().attribute("messaggio", containsString("Ripristinato")));

		assertThat(articoloRepository.findById(id)).get().extracting(Articolo::getNome).isEqualTo("Tazza da salvare");
		assertThat(Files.readString(foto)).isEqualTo("foto originale");
		assertThat(backupService.elenco()).anyMatch(b -> b.nome().contains("prima-del-ripristino"));

		mvc.perform(get("/backup"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(nome)));
		mvc.perform(get("/backup/{n}", nome))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString(nome)));
	}

	@Test
	void fileCheNonEUnBackupVieneRifiutato() throws Exception {
		mvc.perform(multipart("/backup/ripristina")
			.file(new MockMultipartFile("file", "foto.zip", "application/zip", "non sono un backup".getBytes()))
			.with(csrf()))
			.andExpect(redirectedUrl("/backup"))
			.andExpect(flash().attribute("errore", containsString("non è un backup")));
		assertThat(backupService.elenco()).isEmpty();
	}

	@Test
	void nomiFuoriDallaCartellaNonSonoAmmessi() throws Exception {
		mvc.perform(get("/backup/{n}", "..%2Fgestionale.properties")).andExpect(status().is4xxClientError());
		mvc.perform(get("/backup/{n}", "manca.zip")).andExpect(status().isConflict());
	}

	@Test
	void backupAutomaticiVecchiVengonoEliminati() throws Exception {
		for (int i = 0; i < BackupService.AUTOMATICI_DA_TENERE + 3; i++) {
			Path file = CARTELLA.resolve("backup-automatico-2026-01-%02d_100000.zip".formatted(i + 1));
			Files.writeString(file, "x");
			Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(1_700_000_000_000L + i * 1000L));
		}
		Files.writeString(CARTELLA.resolve("backup-manuale-2025-01-01_100000.zip"), "x");
		Files.setLastModifiedTime(CARTELLA.resolve("backup-manuale-2025-01-01_100000.zip"),
				java.nio.file.attribute.FileTime.fromMillis(1_600_000_000_000L));

		assertThat(backupService.serveBackupAutomatico()).isTrue();
		backupService.eliminaAutomaticiVecchi();

		assertThat(backupService.elenco()).filteredOn(BackupService.Backup::automatico)
			.hasSize(BackupService.AUTOMATICI_DA_TENERE)
			.noneMatch(b -> b.nome().equals("backup-automatico-2026-01-01_100000.zip"));
		assertThat(backupService.elenco()).anyMatch(b -> b.nome().startsWith("backup-manuale-"));
	}

}

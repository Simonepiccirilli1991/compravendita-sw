package it.compravendita.gestionale.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import it.compravendita.gestionale.common.FileStorage.FileNonValidoException;

class FileStorageTest {

	@TempDir
	Path cartella;

	@Test
	void salvaEdEliminaImmagine() {
		FileStorage storage = new FileStorage(new AppProperties(cartella));
		MockMultipartFile foto = new MockMultipartFile("foto", "carta.png", "image/png", new byte[] { 1, 2, 3 });

		String relativo = storage.salvaImmagine(foto, "articoli");

		assertThat(relativo).startsWith("articoli/").endsWith(".png");
		assertThat(cartella.resolve(relativo)).exists();

		storage.elimina(relativo);
		assertThat(Files.exists(cartella.resolve(relativo))).isFalse();
	}

	@Test
	void rifiutaFileNonImmagine() {
		FileStorage storage = new FileStorage(new AppProperties(cartella));
		MockMultipartFile pdf = new MockMultipartFile("foto", "doc.pdf", "application/pdf", new byte[] { 1 });

		assertThatThrownBy(() -> storage.salvaImmagine(pdf, "articoli")).isInstanceOf(FileNonValidoException.class);
	}

	@Test
	void rifiutaPercorsiFuoriDallaCartella() {
		FileStorage storage = new FileStorage(new AppProperties(cartella));

		assertThatThrownBy(() -> storage.elimina("../fuori.txt")).isInstanceOf(FileNonValidoException.class);
	}

}

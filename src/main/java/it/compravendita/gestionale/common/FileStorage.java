package it.compravendita.gestionale.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Salvataggio dei file caricati (foto, ricevute) nella cartella {@code app.upload-dir}.
 * I percorsi restituiti sono relativi a quella cartella, con separatore "/".
 */
@Service
public class FileStorage {

	public static final String URL_PREFIX = "/media/";

	private static final Map<String, String> IMMAGINI_AMMESSE = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp",
			"image/gif", "gif");

	private static final Map<String, String> ALLEGATI_AMMESSI = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp",
			"application/pdf", "pdf");

	private final Path radice;

	public FileStorage(AppProperties appProperties) {
		this.radice = appProperties.uploadDir().toAbsolutePath().normalize();
	}

	/**
	 * Salva un'immagine nella sottocartella indicata e ne restituisce il percorso relativo.
	 *
	 * @throws FileNonValidoException se il file non è un'immagine supportata
	 */
	public String salvaImmagine(MultipartFile file, String sottocartella) {
		return salva(file, sottocartella, IMMAGINI_AMMESSE, "Formato non supportato: usa JPG, PNG, WEBP o GIF");
	}

	/**
	 * Salva un documento (ricevuta, fattura) come immagine o PDF.
	 *
	 * @throws FileNonValidoException se il formato non è supportato
	 */
	public String salvaAllegato(MultipartFile file, String sottocartella) {
		return salva(file, sottocartella, ALLEGATI_AMMESSI, "Formato non supportato: usa PDF, JPG, PNG o WEBP");
	}

	private String salva(MultipartFile file, String sottocartella, Map<String, String> ammessi, String errore) {
		String estensione = ammessi.get(file.getContentType());
		if (estensione == null) {
			throw new FileNonValidoException(errore);
		}
		String relativo = sottocartella + "/" + UUID.randomUUID() + "." + estensione;
		Path destinazione = risolvi(relativo);
		try (InputStream in = file.getInputStream()) {
			Files.createDirectories(destinazione.getParent());
			Files.copy(in, destinazione, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Impossibile salvare il file " + relativo, ex);
		}
		return relativo;
	}

	public void elimina(String relativo) {
		if (relativo == null || relativo.isBlank()) {
			return;
		}
		try {
			Files.deleteIfExists(risolvi(relativo));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Impossibile eliminare il file " + relativo, ex);
		}
	}

	public static String url(String relativo) {
		return relativo == null ? null : URL_PREFIX + relativo;
	}

	private Path risolvi(String relativo) {
		Path percorso = radice.resolve(relativo).normalize();
		if (!percorso.startsWith(radice)) {
			throw new FileNonValidoException("Percorso non valido");
		}
		return percorso;
	}

	public static class FileNonValidoException extends RuntimeException {

		public FileNonValidoException(String messaggio) {
			super(messaggio);
		}

	}

}

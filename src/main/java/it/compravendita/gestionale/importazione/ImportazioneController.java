package it.compravendita.gestionale.importazione;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.compravendita.gestionale.common.OperazioneNonConsentitaException;

/**
 * Importazione dell'inventario da CSV in due passi: anteprima con gli errori riga per riga,
 * poi conferma. Tra i due passi il file viaggia nel form (codificato in Base64).
 */
@Controller
@RequestMapping("/import")
public class ImportazioneController {

	private final ImportazioneService importazioneService;

	public ImportazioneController(ImportazioneService importazioneService) {
		this.importazioneService = importazioneService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "import";
	}

	@GetMapping
	String pagina() {
		return "importazione/import";
	}

	@GetMapping("/modello.csv")
	ResponseEntity<byte[]> modello() {
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("modello-inventario.csv").build().toString())
			.body(importazioneService.modello());
	}

	@PostMapping("/anteprima")
	String anteprima(@RequestParam(required = false) MultipartFile file, Model model) {
		if (file == null || file.isEmpty()) {
			model.addAttribute("errore", "Scegli un file CSV da importare");
			return "importazione/import";
		}
		byte[] contenuto;
		try {
			contenuto = file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		model.addAttribute("nomeFile", file.getOriginalFilename());
		model.addAttribute("analisi", importazioneService.analizza(contenuto));
		model.addAttribute("contenuto", Base64.getEncoder().encodeToString(contenuto));
		return "importazione/import";
	}

	@PostMapping("/conferma")
	String conferma(@RequestParam String contenuto, RedirectAttributes redirect) {
		try {
			ImportazioneService.Esito esito = importazioneService.importa(Base64.getDecoder().decode(contenuto));
			redirect.addFlashAttribute("messaggio", "Importate " + esito.righe() + " righe in " + esito.acquisti()
					+ " acquisti (" + esito.articoliCreati() + " articoli e " + esito.brandCreati() + " brand nuovi)");
			return "redirect:/acquisti";
		}
		catch (OperazioneNonConsentitaException | IllegalArgumentException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
			return "redirect:/import";
		}
	}

}

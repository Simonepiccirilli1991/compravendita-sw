package it.compravendita.gestionale.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.compravendita.gestionale.common.OperazioneNonConsentitaException;

/** Pagina "Backup" della versione installata: crea, scarica e ripristina i backup. */
@Controller
@RequestMapping("/backup")
@ConditionalOnProperty("app.desktop.pgsql-bin")
public class BackupController {

	private final BackupService backupService;

	public BackupController(BackupService backupService) {
		this.backupService = backupService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "backup";
	}

	@GetMapping
	String pagina(Model model) {
		model.addAttribute("backup", backupService.elenco());
		model.addAttribute("cartella", backupService.cartella());
		return "backup/backup";
	}

	@PostMapping
	String crea(RedirectAttributes redirect) {
		try {
			BackupService.Backup creato = backupService.crea("manuale");
			redirect.addFlashAttribute("messaggio", "Backup creato: " + creato.nome());
		}
		catch (UncheckedIOException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
		}
		return "redirect:/backup";
	}

	@GetMapping("/{nome}")
	ResponseEntity<Resource> scarica(@PathVariable String nome) {
		Path file = backupService.file(nome);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("application/zip"))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
			.body(new FileSystemResource(file));
	}

	@PostMapping("/{nome}/ripristina")
	String ripristinaEsistente(@PathVariable String nome, RedirectAttributes redirect) {
		return ripristina(backupService.file(nome), nome, redirect);
	}

	@PostMapping("/ripristina")
	String ripristinaCaricato(@RequestParam(required = false) MultipartFile file, RedirectAttributes redirect) {
		if (file == null || file.isEmpty()) {
			redirect.addFlashAttribute("errore", "Scegli il file di backup da ripristinare");
			return "redirect:/backup";
		}
		Path temporaneo = null;
		try {
			Files.createDirectories(backupService.cartella());
			temporaneo = Files.createTempFile(backupService.cartella(), "caricato", ".tmp");
			file.transferTo(temporaneo);
			return ripristina(temporaneo, file.getOriginalFilename(), redirect);
		}
		catch (IOException ex) {
			redirect.addFlashAttribute("errore", "Impossibile leggere il file: " + ex.getMessage());
			return "redirect:/backup";
		}
		finally {
			eliminaSilenziosamente(temporaneo);
		}
	}

	private String ripristina(Path file, String nome, RedirectAttributes redirect) {
		try {
			backupService.ripristina(file);
			redirect.addFlashAttribute("messaggio", "Ripristinato il backup " + nome
					+ ". I dati precedenti sono stati salvati in un backup \"prima-del-ripristino\".");
		}
		catch (OperazioneNonConsentitaException | UncheckedIOException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
		}
		return "redirect:/backup";
	}

	private static void eliminaSilenziosamente(Path file) {
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ignorata) {
			// file temporaneo: verrà sovrascritto o eliminato a mano
		}
	}

}

package it.compravendita.gestionale.acquisti;

import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.compravendita.gestionale.acquisti.AcquistoForm.RigaForm;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.common.FileStorage.FileNonValidoException;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.magazzino.StatoOggetto;
import it.compravendita.gestionale.prezzi.PrezziService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/acquisti")
public class AcquistoController {

	private final AcquistoService acquistoService;

	private final ElencoArticoli elencoArticoli;

	private final PrezziService prezziService;

	public AcquistoController(AcquistoService acquistoService, ElencoArticoli elencoArticoli,
			PrezziService prezziService) {
		this.acquistoService = acquistoService;
		this.elencoArticoli = elencoArticoli;
		this.prezziService = prezziService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "acquisti";
	}

	@GetMapping
	String lista(@ModelAttribute("filtro") FiltroAcquisti filtro,
			@PageableDefault(size = 20, sort = { "dataAcquisto", "id" }, direction = Sort.Direction.DESC) Pageable pageable,
			@RequestHeader(value = "HX-Request", required = false) String htmx, Model model) {
		model.addAttribute("pagina", acquistoService.cerca(filtro, pageable));
		return htmx != null ? "acquisti/lista :: risultati" : "acquisti/lista";
	}

	@GetMapping("/nuovo")
	String nuovo(Model model) {
		return mostraForm(AcquistoForm.nuovo(), null, model);
	}

	@PostMapping
	String crea(@Valid @ModelAttribute("form") AcquistoForm form, BindingResult binding,
			@RequestParam(required = false) MultipartFile allegato, Model model, RedirectAttributes redirect) {
		return salva(null, form, binding, allegato, model, redirect);
	}

	@GetMapping("/{id}")
	String dettaglio(@PathVariable Long id, Model model) {
		Acquisto acquisto = acquistoService.trova(id);
		model.addAttribute("acquisto", acquisto);
		model.addAttribute("bloccate", acquistoService.righeBloccate(acquisto));
		model.addAttribute("calcoloPrezzi", prezziService.calcolo());
		return "acquisti/dettaglio";
	}

	@GetMapping("/{id}/modifica")
	String modifica(@PathVariable Long id, Model model) {
		Acquisto acquisto = acquistoService.trova(id);
		return mostraForm(AcquistoForm.da(acquisto, acquistoService.righeBloccate(acquisto), elencoArticoli::etichetta), acquisto, model);
	}

	@PostMapping("/{id}")
	String aggiorna(@PathVariable Long id, @Valid @ModelAttribute("form") AcquistoForm form, BindingResult binding,
			@RequestParam(required = false) MultipartFile allegato, Model model, RedirectAttributes redirect) {
		return salva(id, form, binding, allegato, model, redirect);
	}

	@PostMapping("/{id}/elimina")
	String elimina(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			acquistoService.elimina(id);
			redirect.addFlashAttribute("messaggio", "Acquisto eliminato");
			return "redirect:/acquisti";
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
			return "redirect:/acquisti/" + id;
		}
	}

	/** Frammento HTMX: aggiunge una riga vuota o rimuove quella indicata e ridisegna le righe. */
	@GetMapping("/righe")
	String righe(@ModelAttribute("form") AcquistoForm form, @RequestParam String azione,
			@RequestParam(required = false) Integer indice, Model model, HttpServletResponse response) {
		form.getRighe().removeIf(Objects::isNull);
		if ("aggiungi".equals(azione)) {
			form.getRighe().add(new RigaForm());
		}
		else if ("rimuovi".equals(azione) && indice != null && indice >= 0 && indice < form.getRighe().size()
				&& !form.getRighe().get(indice).isBloccata()) {
			form.getRighe().remove((int) indice);
		}
		model.addAttribute("stati", StatoOggetto.iniziali());
		// aggiorna l'anteprima dei costi dopo lo scambio delle righe
		response.setHeader("HX-Trigger", "righeAggiornate");
		return "acquisti/form :: righe";
	}

	/** Frammento HTMX con la ripartizione dei costi calcolata sui dati attuali del form. */
	@GetMapping("/anteprima")
	String anteprima(@ModelAttribute("form") AcquistoForm form, Model model) {
		model.addAttribute("anteprima", acquistoService.anteprima(form));
		model.addAttribute("calcoloPrezzi", prezziService.calcolo());
		return "acquisti/form :: anteprima";
	}

	/** Frammento HTMX: elenco articoli aggiornato (dopo averne creato uno in un'altra scheda). */
	@GetMapping("/elenco-articoli")
	String elencoArticoli(Model model) {
		model.addAttribute("opzioniArticoli", elencoArticoli.opzioni());
		return "acquisti/form :: elencoArticoli";
	}

	private String salva(Long id, AcquistoForm form, BindingResult binding, MultipartFile allegato, Model model,
			RedirectAttributes redirect) {
		Acquisto esistente = id == null ? null : acquistoService.trova(id);
		Set<Long> bloccate = esistente == null ? Set.of() : acquistoService.righeBloccate(esistente);
		form.getRighe().removeIf(Objects::isNull);
		form.getRighe().forEach(r -> r.setBloccata(r.getId() != null && bloccate.contains(r.getId())));
		acquistoService.validaRighe(form, bloccate, binding);
		if (binding.hasErrors()) {
			return mostraForm(form, esistente, model);
		}
		try {
			Acquisto salvato = acquistoService.salva(id, form, allegato);
			redirect.addFlashAttribute("messaggio", id == null ? "Acquisto registrato" : "Acquisto aggiornato");
			return "redirect:/acquisti/" + salvato.getId();
		}
		catch (FileNonValidoException ex) {
			model.addAttribute("erroreAllegato", ex.getMessage());
			return mostraForm(form, esistente, model);
		}
		catch (OperazioneNonConsentitaException ex) {
			model.addAttribute("errore", ex.getMessage());
			return mostraForm(form, esistente, model);
		}
	}

	private String mostraForm(AcquistoForm form, Acquisto acquisto, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("acquisto", acquisto);
		model.addAttribute("metodi", MetodoRipartizione.values());
		model.addAttribute("stati", StatoOggetto.iniziali());
		model.addAttribute("opzioniArticoli", elencoArticoli.opzioni());
		model.addAttribute("anteprima", acquistoService.anteprima(form));
		model.addAttribute("calcoloPrezzi", prezziService.calcolo());
		return "acquisti/form";
	}

}

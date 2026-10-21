package it.compravendita.gestionale.magazzino;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.BrandService;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/magazzino")
public class MagazzinoController {

	private final MagazzinoService magazzinoService;

	private final BrandService brandService;

	public MagazzinoController(MagazzinoService magazzinoService, BrandService brandService) {
		this.magazzinoService = magazzinoService;
		this.brandService = brandService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "magazzino";
	}

	@GetMapping
	String giacenze(@ModelAttribute("filtro") FiltroMagazzino filtro, @PageableDefault(size = 25) Pageable pageable,
			@RequestHeader(value = "HX-Request", required = false) String htmx, Model model) {
		model.addAttribute("pagina", magazzinoService.giacenze(filtro, pageable));
		model.addAttribute("sezione", "giacenze");
		if (htmx != null) {
			return "magazzino/giacenze :: risultati";
		}
		model.addAttribute("riepilogo", magazzinoService.riepilogo());
		aggiungiFiltri(model);
		return "magazzino/giacenze";
	}

	@GetMapping("/lotti")
	String lotti(@ModelAttribute("filtro") FiltroMagazzino filtro,
			@PageableDefault(size = 25, sort = { "acquisto.dataAcquisto", "id" }) Pageable pageable,
			@RequestHeader(value = "HX-Request", required = false) String htmx, Model model) {
		model.addAttribute("pagina", magazzinoService.lotti(filtro, pageable));
		model.addAttribute("sezione", "lotti");
		if (htmx != null) {
			return "magazzino/lotti :: risultati";
		}
		aggiungiFiltri(model);
		model.addAttribute("stati", StatoOggetto.values());
		return "magazzino/lotti";
	}

	@PostMapping("/lotti/{id}/stato")
	String cambiaStato(@PathVariable Long id, @RequestParam StatoOggetto stato,
			@RequestParam(required = false) String ritorno, RedirectAttributes redirect) {
		try {
			RigaAcquisto lotto = magazzinoService.cambiaStato(id, stato);
			redirect.addFlashAttribute("messaggio",
					"\"" + lotto.getArticolo().getNome() + "\": " + stato.getEtichetta().toLowerCase());
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
		}
		return "redirect:" + ritornoSicuro(ritorno, "/magazzino/lotti");
	}

	@GetMapping("/lotti/{id}/rettifica")
	String nuovaRettifica(@PathVariable Long id, Model model) {
		return mostraRettifica(id, new RettificaForm(), model);
	}

	@PostMapping("/lotti/{id}/rettifica")
	String registraRettifica(@PathVariable Long id, @Valid @ModelAttribute("form") RettificaForm form,
			BindingResult binding, Model model, RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			return mostraRettifica(id, form, model);
		}
		try {
			magazzinoService.registraRettifica(id, form);
			redirect.addFlashAttribute("messaggio", "Rettifica registrata");
			return "redirect:/magazzino/rettifiche";
		}
		catch (OperazioneNonConsentitaException ex) {
			model.addAttribute("errore", ex.getMessage());
			return mostraRettifica(id, form, model);
		}
	}

	@GetMapping("/rettifiche")
	String rettifiche(@PageableDefault(size = 25) Pageable pageable, Model model) {
		model.addAttribute("pagina", magazzinoService.rettifiche(pageable));
		model.addAttribute("sezione", "rettifiche");
		return "magazzino/rettifiche";
	}

	@PostMapping("/rettifiche/{id}/annulla")
	String annullaRettifica(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			magazzinoService.annullaRettifica(id);
			redirect.addFlashAttribute("messaggio", "Rettifica annullata: i pezzi sono tornati in magazzino");
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
		}
		return "redirect:/magazzino/rettifiche";
	}

	/** Frammento HTMX per la scheda articolo: lotti e rettifiche dell'articolo. */
	@GetMapping("/articoli/{articoloId}")
	String movimentiArticolo(@PathVariable Long articoloId, Model model) {
		model.addAttribute("articoloId", articoloId);
		model.addAttribute("lotti", magazzinoService.lottiArticolo(articoloId));
		model.addAttribute("rettifiche", magazzinoService.rettificheArticolo(articoloId));
		return "magazzino/articolo :: movimenti";
	}

	private String mostraRettifica(Long lottoId, RettificaForm form, Model model) {
		model.addAttribute("lotto", magazzinoService.lotto(lottoId));
		model.addAttribute("form", form);
		model.addAttribute("motivi", MotivoRettifica.values());
		return "magazzino/rettifica-form";
	}

	private void aggiungiFiltri(Model model) {
		model.addAttribute("categorie", Categoria.values());
		model.addAttribute("brands", brandService.tutti());
		model.addAttribute("ordini", FiltroMagazzino.Ordine.values());
	}

	/** Accetta solo percorsi interni all'applicazione. */
	static String ritornoSicuro(String ritorno, String predefinito) {
		if (ritorno == null || !ritorno.startsWith("/") || ritorno.startsWith("//") || ritorno.contains("\\")) {
			return predefinito;
		}
		return ritorno;
	}

}

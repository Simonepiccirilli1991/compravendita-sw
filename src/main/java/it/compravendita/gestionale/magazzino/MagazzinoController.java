package it.compravendita.gestionale.magazzino;

import java.util.List;

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
import it.compravendita.gestionale.prezzi.PrezziService;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/magazzino")
public class MagazzinoController {

	private final MagazzinoService magazzinoService;

	private final GradingService gradingService;

	private final BrandService brandService;

	private final PrezziService prezziService;

	public MagazzinoController(MagazzinoService magazzinoService, GradingService gradingService,
			BrandService brandService, PrezziService prezziService) {
		this.magazzinoService = magazzinoService;
		this.gradingService = gradingService;
		this.brandService = brandService;
		this.prezziService = prezziService;
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
		model.addAttribute("calcoloPrezzi", prezziService.calcolo());
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

	/** Frammento HTMX per la scheda articolo: lotti, rettifiche e grading dell'articolo. */
	@GetMapping("/articoli/{articoloId}")
	String movimentiArticolo(@PathVariable Long articoloId, Model model) {
		List<RigaAcquisto> lotti = magazzinoService.lottiArticolo(articoloId);
		model.addAttribute("articoloId", articoloId);
		model.addAttribute("lotti", lotti);
		model.addAttribute("lottoPrezzi", lottoPrezzi(lotti));
		model.addAttribute("calcoloPrezzi", prezziService.calcolo());
		model.addAttribute("rettifiche", magazzinoService.rettificheArticolo(articoloId));
		model.addAttribute("gradings", gradingService.perArticolo(articoloId));
		return "magazzino/articolo :: movimenti";
	}

	// ------------------------------------------------------------------ grading

	@GetMapping("/grading")
	String grading(@RequestParam(required = false) StatoGrading stato, Model model) {
		model.addAttribute("elenco", gradingService.elenco(stato));
		model.addAttribute("statoFiltro", stato);
		model.addAttribute("statiGrading", StatoGrading.values());
		model.addAttribute("sezione", "grading");
		return "magazzino/grading";
	}

	@GetMapping("/lotti/{id}/grading")
	String nuovoGrading(@PathVariable Long id, Model model) {
		return mostraInvio(id, new InvioGradingForm(), model);
	}

	@PostMapping("/lotti/{id}/grading")
	String inviaGrading(@PathVariable Long id, @Valid @ModelAttribute("form") InvioGradingForm form,
			BindingResult binding, Model model, RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			return mostraInvio(id, form, model);
		}
		try {
			gradingService.invia(id, form);
			redirect.addFlashAttribute("messaggio", "Carta inviata a " + form.getEnte());
			return "redirect:/magazzino/grading";
		}
		catch (OperazioneNonConsentitaException ex) {
			model.addAttribute("errore", ex.getMessage());
			return mostraInvio(id, form, model);
		}
	}

	@GetMapping("/grading/{id}/rientro")
	String rientro(@PathVariable Long id, Model model) {
		Grading grading = gradingService.trova(id);
		RientroGradingForm form = new RientroGradingForm();
		form.setNomeArticolo(grading.getArticoloOriginale().getNome());
		form.setCosto(grading.getCosto());
		return mostraRientro(grading, form, model);
	}

	@PostMapping("/grading/{id}/rientro")
	String registraRientro(@PathVariable Long id, @Valid @ModelAttribute("form") RientroGradingForm form,
			BindingResult binding, Model model, RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			return mostraRientro(gradingService.trova(id), form, model);
		}
		try {
			Long articoloId = gradingService.rientro(id, form).getId();
			redirect.addFlashAttribute("messaggio", "Rientro registrato: creato l'articolo gradato");
			return "redirect:/articoli/" + articoloId;
		}
		catch (OperazioneNonConsentitaException ex) {
			model.addAttribute("errore", ex.getMessage());
			return mostraRientro(gradingService.trova(id), form, model);
		}
	}

	@PostMapping("/grading/{id}/annulla")
	String annullaGrading(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			gradingService.annulla(id);
			redirect.addFlashAttribute("messaggio", "Grading annullato: la carta è tornata in magazzino");
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
		}
		return "redirect:/magazzino/grading";
	}

	private String mostraRettifica(Long lottoId, RettificaForm form, Model model) {
		model.addAttribute("lotto", magazzinoService.lotto(lottoId));
		model.addAttribute("form", form);
		model.addAttribute("motivi", MotivoRettifica.values());
		return "magazzino/rettifica-form";
	}

	private String mostraInvio(Long lottoId, InvioGradingForm form, Model model) {
		RigaAcquisto lotto = magazzinoService.lotto(lottoId);
		model.addAttribute("lotto", lotto);
		model.addAttribute("inviabile", GradingService.inviabile(lotto));
		model.addAttribute("form", form);
		model.addAttribute("enti", gradingService.enti());
		return "magazzino/grading-invio";
	}

	private String mostraRientro(Grading grading, RientroGradingForm form, Model model) {
		model.addAttribute("grading", grading);
		model.addAttribute("form", form);
		return "magazzino/grading-rientro";
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

	/**
	 * Lotto su cui basare i prezzi suggeriti dell'articolo: il prossimo che verrà venduto (il più vecchio
	 * ancora disponibile, come nello scarico FIFO) o, se è tutto esaurito, l'ultimo acquistato.
	 */
	static RigaAcquisto lottoPrezzi(List<RigaAcquisto> lotti) {
		// i lotti arrivano dal più recente al più vecchio
		for (int i = lotti.size() - 1; i >= 0; i--) {
			if (lotti.get(i).getQuantitaResidua() > 0) {
				return lotti.get(i);
			}
		}
		return lotti.isEmpty() ? null : lotti.get(0);
	}

}

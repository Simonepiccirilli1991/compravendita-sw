package it.compravendita.gestionale.vendite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.BrandService;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.piattaforme.PiattaformaService;
import it.compravendita.gestionale.vendite.VenditaForm.RigaForm;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/vendite")
public class VenditaController {

	private final VenditaService venditaService;

	private final PiattaformaService piattaformaService;

	private final BrandService brandService;

	private final ElencoArticoli elencoArticoli;

	private final ArticoloRepository articoloRepository;

	private final RigaAcquistoRepository rigaAcquistoRepository;

	public VenditaController(VenditaService venditaService, PiattaformaService piattaformaService,
			BrandService brandService, ElencoArticoli elencoArticoli, ArticoloRepository articoloRepository,
			RigaAcquistoRepository rigaAcquistoRepository) {
		this.venditaService = venditaService;
		this.piattaformaService = piattaformaService;
		this.brandService = brandService;
		this.elencoArticoli = elencoArticoli;
		this.articoloRepository = articoloRepository;
		this.rigaAcquistoRepository = rigaAcquistoRepository;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "vendite";
	}

	@GetMapping
	String lista(@ModelAttribute("filtro") FiltroVendite filtro,
			@PageableDefault(size = 20, sort = { "dataVendita", "id" }, direction = Sort.Direction.DESC) Pageable pageable,
			@RequestHeader(value = "HX-Request", required = false) String htmx, Model model) {
		model.addAttribute("pagina", venditaService.cerca(filtro, pageable));
		model.addAttribute("totali", venditaService.totali(filtro));
		if (htmx != null) {
			return "vendite/lista :: risultati";
		}
		model.addAttribute("piattaforme", piattaformaService.tutte());
		model.addAttribute("categorie", Categoria.values());
		model.addAttribute("brands", brandService.tutti());
		model.addAttribute("stati", StatoVendita.values());
		return "vendite/lista";
	}

	/** Nuova vendita, eventualmente già impostata su un articolo o su un lotto. */
	@GetMapping("/nuova")
	String nuova(@RequestParam(required = false) Long articoloId, @RequestParam(required = false) Long lottoId,
			Model model) {
		VenditaForm form = VenditaForm.nuova();
		RigaForm riga = form.getRighe().getFirst();
		if (lottoId != null) {
			rigaAcquistoRepository.findConDettagliById(lottoId).ifPresent(lotto -> {
				riga.setArticoloEtichetta(elencoArticoli.etichetta(lotto.getArticolo()));
				riga.setLottoId(lotto.getId());
				riga.setQuantita(1);
			});
		}
		else if (articoloId != null) {
			articoloRepository.findById(articoloId).ifPresent(articolo -> {
				riga.setArticoloEtichetta(elencoArticoli.etichetta(articolo));
				riga.setQuantita(1);
			});
		}
		return mostraForm(form, null, model);
	}

	@PostMapping
	String crea(@Valid @ModelAttribute("form") VenditaForm form, BindingResult binding, Model model,
			RedirectAttributes redirect) {
		return salva(null, form, binding, model, redirect);
	}

	@GetMapping("/{id}")
	String dettaglio(@PathVariable Long id, Model model) {
		model.addAttribute("vendita", venditaService.trova(id));
		return "vendite/dettaglio";
	}

	@GetMapping("/{id}/modifica")
	String modifica(@PathVariable Long id, Model model, RedirectAttributes redirect) {
		Vendita vendita = venditaService.trova(id);
		if (!vendita.isCompletata()) {
			redirect.addFlashAttribute("errore", "Si possono modificare solo le vendite completate");
			return "redirect:/vendite/" + id;
		}
		return mostraForm(VenditaForm.da(vendita, elencoArticoli::etichetta), vendita, model);
	}

	@PostMapping("/{id}")
	String aggiorna(@PathVariable Long id, @Valid @ModelAttribute("form") VenditaForm form, BindingResult binding,
			Model model, RedirectAttributes redirect) {
		return salva(id, form, binding, model, redirect);
	}

	@GetMapping("/{id}/annulla")
	String annullaForm(@PathVariable Long id, Model model) {
		return mostraStorno(venditaService.trova(id), new StornoForm(), "annulla", model);
	}

	@PostMapping("/{id}/annulla")
	String annulla(@PathVariable Long id, @Valid @ModelAttribute("form") StornoForm form, BindingResult binding,
			Model model, RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			return mostraStorno(venditaService.trova(id), form, "annulla", model);
		}
		return esegui(id, redirect, "Vendita annullata: la merce è tornata in magazzino",
				() -> venditaService.annulla(id, form));
	}

	@GetMapping("/{id}/reso")
	String resoForm(@PathVariable Long id, Model model) {
		Vendita vendita = venditaService.trova(id);
		StornoForm form = new StornoForm();
		// di norma restano a carico almeno la spedizione e l'imballaggio sostenuti
		form.setCostiReso(vendita.getTotaleCostiVendita());
		return mostraStorno(vendita, form, "reso", model);
	}

	@PostMapping("/{id}/reso")
	String reso(@PathVariable Long id, @Valid @ModelAttribute("form") StornoForm form, BindingResult binding,
			Model model, RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			return mostraStorno(venditaService.trova(id), form, "reso", model);
		}
		return esegui(id, redirect, "Reso registrato: la merce è tornata in magazzino",
				() -> venditaService.reso(id, form));
	}

	@PostMapping("/{id}/elimina")
	String elimina(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			venditaService.elimina(id);
			redirect.addFlashAttribute("messaggio", "Vendita eliminata");
			return "redirect:/vendite";
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
			return "redirect:/vendite/" + id;
		}
	}

	/** Frammento HTMX: aggiunge una riga vuota o rimuove quella indicata. */
	@GetMapping("/righe")
	String righe(@ModelAttribute("form") VenditaForm form, @RequestParam String azione,
			@RequestParam(required = false) Integer indice, Model model, HttpServletResponse response) {
		form.getRighe().removeIf(Objects::isNull);
		if ("aggiungi".equals(azione)) {
			form.getRighe().add(new RigaForm());
		}
		else if ("rimuovi".equals(azione) && indice != null && indice >= 0 && indice < form.getRighe().size()) {
			form.getRighe().remove((int) indice);
		}
		aggiungiLotti(form, model);
		response.setHeader("HX-Trigger", "righeAggiornate");
		return "vendite/form :: righe";
	}

	/** Frammento HTMX: scelta del lotto per la riga, dopo aver indicato l'articolo. */
	@GetMapping("/lotti")
	String lotti(@ModelAttribute("form") VenditaForm form, @RequestParam int indice, Model model) {
		RigaForm riga = indice < form.getRighe().size() ? form.getRighe().get(indice) : new RigaForm();
		Long articoloId = ElencoArticoli.idDaEtichetta(riga.getArticoloEtichetta());
		model.addAttribute("indice", indice);
		model.addAttribute("lottoScelto", riga.getLottoId());
		model.addAttribute("lottiRiga", articoloId == null ? List.of() : venditaService.lottiVendibili(articoloId));
		return "vendite/fragments :: sceltaLotto";
	}

	@GetMapping("/anteprima")
	String anteprima(@ModelAttribute("form") VenditaForm form, @RequestParam(required = false) Long venditaId,
			Model model) {
		model.addAttribute("anteprima", venditaService.anteprima(form, venditaId));
		return "vendite/form :: anteprima";
	}

	@GetMapping("/elenco-articoli")
	String elencoArticoli(Model model) {
		model.addAttribute("opzioniArticoli", elencoArticoli.opzioniVendibili());
		return "vendite/form :: elencoArticoli";
	}

	/** Frammento per la scheda articolo: vendite in cui compare l'articolo. */
	@GetMapping("/articoli/{articoloId}")
	String venditeArticolo(@PathVariable Long articoloId, Model model) {
		model.addAttribute("articoloId", articoloId);
		model.addAttribute("righeVendita", venditaService.perArticolo(articoloId));
		return "vendite/articolo :: vendite";
	}

	private String salva(Long id, VenditaForm form, BindingResult binding, Model model,
			RedirectAttributes redirect) {
		Vendita esistente = id == null ? null : venditaService.trova(id);
		form.getRighe().removeIf(Objects::isNull);
		venditaService.validaRighe(form, esistente, binding);
		if (binding.hasErrors()) {
			return mostraForm(form, esistente, model);
		}
		try {
			Vendita salvata = venditaService.salva(id, form);
			redirect.addFlashAttribute("messaggio", id == null ? "Vendita registrata" : "Vendita aggiornata");
			return "redirect:/vendite/" + salvata.getId();
		}
		catch (OperazioneNonConsentitaException ex) {
			model.addAttribute("errore", ex.getMessage());
			return mostraForm(form, esistente, model);
		}
	}

	private String esegui(Long id, RedirectAttributes redirect, String messaggio, Runnable operazione) {
		try {
			operazione.run();
			redirect.addFlashAttribute("messaggio", messaggio);
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
		}
		return "redirect:/vendite/" + id;
	}

	private String mostraForm(VenditaForm form, Vendita vendita, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("vendita", vendita);
		model.addAttribute("piattaforme", piattaformaService.attive());
		model.addAttribute("opzioniArticoli", elencoArticoli.opzioniVendibili());
		model.addAttribute("anteprima", venditaService.anteprima(form, vendita == null ? null : vendita.getId()));
		aggiungiLotti(form, model);
		return "vendite/form";
	}

	/** Lotti selezionabili per ogni riga che ha già un articolo. */
	private void aggiungiLotti(VenditaForm form, Model model) {
		Map<Integer, List<RigaAcquisto>> lotti = new HashMap<>();
		for (int i = 0; i < form.getRighe().size(); i++) {
			Long articoloId = ElencoArticoli.idDaEtichetta(form.getRighe().get(i).getArticoloEtichetta());
			lotti.put(i, articoloId == null ? List.of() : venditaService.lottiVendibili(articoloId));
		}
		model.addAttribute("lottiPerRiga", lotti);
	}

	private String mostraStorno(Vendita vendita, StornoForm form, String tipo, Model model) {
		model.addAttribute("vendita", vendita);
		model.addAttribute("form", form);
		model.addAttribute("tipo", tipo);
		return "vendite/storno";
	}

}

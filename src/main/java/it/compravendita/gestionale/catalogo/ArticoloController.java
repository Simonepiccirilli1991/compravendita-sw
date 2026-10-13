package it.compravendita.gestionale.catalogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
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

import it.compravendita.gestionale.common.FileStorage.FileNonValidoException;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/articoli")
public class ArticoloController {

	private final ArticoloService articoloService;

	private final BrandService brandService;

	private final SchemaAttributi schemaAttributi;

	public ArticoloController(ArticoloService articoloService, BrandService brandService,
			SchemaAttributi schemaAttributi) {
		this.articoloService = articoloService;
		this.brandService = brandService;
		this.schemaAttributi = schemaAttributi;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "articoli";
	}

	@GetMapping
	String lista(@ModelAttribute("filtro") FiltroArticoli filtro, @PageableDefault(size = 20, sort = "nome") Pageable pageable,
			@RequestHeader(value = "HX-Request", required = false) String htmx, Model model) {
		model.addAttribute("pagina", articoloService.cerca(filtro, pageable));
		model.addAttribute("categorie", Categoria.values());
		model.addAttribute("condizioni", Condizione.values());
		model.addAttribute("brands", brandService.tutti());
		// Le ricerche HTMX aggiornano solo la tabella dei risultati
		return htmx != null ? "articoli/lista :: risultati" : "articoli/lista";
	}

	@GetMapping("/nuovo")
	String nuovo(@RequestParam(required = false) Categoria categoria, Model model) {
		ArticoloForm form = new ArticoloForm();
		form.setCategoria(categoria);
		return mostraForm(form, null, Map.of(), model);
	}

	@PostMapping
	String crea(@Valid @ModelAttribute("form") ArticoloForm form, BindingResult binding,
			@RequestParam(required = false) MultipartFile foto, Model model, RedirectAttributes redirect) {
		return salva(null, form, binding, foto, model, redirect);
	}

	@GetMapping("/{id}")
	String dettaglio(@PathVariable Long id, Model model) {
		Articolo articolo = articoloService.trova(id);
		model.addAttribute("articolo", articolo);
		model.addAttribute("campi", schemaAttributi.campi(articolo.getCategoria()));
		return "articoli/dettaglio";
	}

	@GetMapping("/{id}/modifica")
	String modifica(@PathVariable Long id, Model model) {
		Articolo articolo = articoloService.trova(id);
		return mostraForm(ArticoloForm.da(articolo, schemaAttributi), articolo, Map.of(), model);
	}

	@PostMapping("/{id}")
	String aggiorna(@PathVariable Long id, @Valid @ModelAttribute("form") ArticoloForm form, BindingResult binding,
			@RequestParam(required = false) MultipartFile foto, Model model, RedirectAttributes redirect) {
		return salva(id, form, binding, foto, model, redirect);
	}

	@PostMapping("/{id}/elimina")
	String elimina(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			articoloService.elimina(id);
			redirect.addFlashAttribute("messaggio", "Articolo eliminato");
			return "redirect:/articoli";
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
			return "redirect:/articoli/" + id;
		}
	}

	/** Frammento HTMX con i campi specifici della categoria scelta nel form. */
	@GetMapping("/campi")
	String campi(@RequestParam(required = false) Categoria categoria, @ModelAttribute("form") ArticoloForm form,
			Model model) {
		model.addAttribute("campi", schemaAttributi.campi(categoria));
		model.addAttribute("valori", form.getAttributi());
		model.addAttribute("erroriAttributi", Map.of());
		return "articoli/form :: campiAttributi";
	}

	private String salva(Long id, ArticoloForm form, BindingResult binding, MultipartFile foto, Model model,
			RedirectAttributes redirect) {
		SchemaAttributi.Risultato attributi = schemaAttributi.valida(form.getCategoria(), form.getAttributi());
		Articolo esistente = id == null ? null : articoloService.trova(id);
		if (binding.hasErrors() || !attributi.valido()) {
			return mostraForm(form, esistente, attributi.errori(), model);
		}
		try {
			Articolo salvato = articoloService.salva(id, form, attributi.valori(), foto);
			redirect.addFlashAttribute("messaggio", id == null ? "Articolo creato" : "Articolo aggiornato");
			return "redirect:/articoli/" + salvato.getId();
		}
		catch (FileNonValidoException ex) {
			model.addAttribute("erroreFoto", ex.getMessage());
			return mostraForm(form, esistente, Map.of(), model);
		}
	}

	private String mostraForm(ArticoloForm form, Articolo articolo, Map<String, String> erroriAttributi, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("articolo", articolo);
		model.addAttribute("categorie", Categoria.values());
		model.addAttribute("condizioni", Condizione.values());
		model.addAttribute("brands", brandSelezionabili(articolo));
		model.addAttribute("campi", schemaAttributi.campi(form.getCategoria()));
		model.addAttribute("valori", form.getAttributi());
		model.addAttribute("erroriAttributi", erroriAttributi);
		return "articoli/form";
	}

	/** Brand attivi, più quello attuale dell'articolo anche se nel frattempo disattivato. */
	private List<Brand> brandSelezionabili(Articolo articolo) {
		List<Brand> brands = new ArrayList<>(brandService.attivi());
		if (articolo != null && articolo.getBrand() != null && !brands.contains(articolo.getBrand())) {
			brands.add(articolo.getBrand());
		}
		return brands;
	}

}

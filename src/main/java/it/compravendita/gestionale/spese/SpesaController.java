package it.compravendita.gestionale.spese;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.compravendita.gestionale.catalogo.ElencoArticoli;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/spese")
public class SpesaController {

	private final SpesaService spesaService;

	private final ElencoArticoli elencoArticoli;

	public SpesaController(SpesaService spesaService, ElencoArticoli elencoArticoli) {
		this.spesaService = spesaService;
		this.elencoArticoli = elencoArticoli;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "spese";
	}

	@ModelAttribute("categorie")
	CategoriaSpesa[] categorie() {
		return CategoriaSpesa.values();
	}

	@GetMapping
	String lista(@ModelAttribute("filtro") FiltroSpese filtro,
			@PageableDefault(size = 25, sort = { "dataSpesa", "id" }, direction = Sort.Direction.DESC) Pageable pageable,
			@RequestHeader(value = "HX-Request", required = false) String htmx, Model model) {
		model.addAttribute("pagina", spesaService.cerca(filtro, pageable));
		model.addAttribute("totali", spesaService.totali(filtro));
		return htmx != null ? "spese/lista :: risultati" : "spese/lista";
	}

	@GetMapping("/nuova")
	String nuova(Model model) {
		return mostraForm(SpesaForm.nuova(), null, model);
	}

	@PostMapping
	String crea(@Valid @ModelAttribute("form") SpesaForm form, BindingResult binding, Model model,
			RedirectAttributes redirect) {
		return salva(null, form, binding, model, redirect);
	}

	@GetMapping("/{id}/modifica")
	String modifica(@PathVariable Long id, Model model) {
		Spesa spesa = spesaService.trova(id);
		return mostraForm(SpesaForm.da(spesa, elencoArticoli::etichetta), spesa, model);
	}

	@PostMapping("/{id}")
	String aggiorna(@PathVariable Long id, @Valid @ModelAttribute("form") SpesaForm form, BindingResult binding,
			Model model, RedirectAttributes redirect) {
		return salva(id, form, binding, model, redirect);
	}

	@PostMapping("/{id}/elimina")
	String elimina(@PathVariable Long id, RedirectAttributes redirect) {
		spesaService.elimina(id);
		redirect.addFlashAttribute("messaggio", "Spesa eliminata");
		return "redirect:/spese";
	}

	private String salva(Long id, SpesaForm form, BindingResult binding, Model model, RedirectAttributes redirect) {
		Spesa esistente = id == null ? null : spesaService.trova(id);
		spesaService.valida(form, binding);
		if (binding.hasErrors()) {
			return mostraForm(form, esistente, model);
		}
		spesaService.salva(id, form);
		redirect.addFlashAttribute("messaggio", id == null ? "Spesa registrata" : "Spesa aggiornata");
		return "redirect:/spese";
	}

	private String mostraForm(SpesaForm form, Spesa spesa, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("spesa", spesa);
		model.addAttribute("opzioniArticoli", elencoArticoli.opzioni());
		return "spese/form";
	}

}

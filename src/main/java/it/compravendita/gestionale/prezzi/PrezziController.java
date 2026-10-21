package it.compravendita.gestionale.prezzi;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.compravendita.gestionale.piattaforme.PiattaformaService;
import jakarta.validation.Valid;

/** Parametri dei prezzi suggeriti con una simulazione su un costo d'esempio. */
@Controller
@RequestMapping("/prezzi")
public class PrezziController {

	static final BigDecimal COSTO_ESEMPIO = BigDecimal.TEN;

	private final PrezziService prezziService;

	private final PiattaformaService piattaformaService;

	public PrezziController(PrezziService prezziService, PiattaformaService piattaformaService) {
		this.prezziService = prezziService;
		this.piattaformaService = piattaformaService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "prezzi";
	}

	@GetMapping
	String parametri(Model model) {
		ParametriPrezzoForm form = ParametriPrezzoForm.da(prezziService.parametri());
		return mostraForm(form, COSTO_ESEMPIO, prezziService.calcolo(form), model);
	}

	@PostMapping
	String salva(@Valid @ModelAttribute("form") ParametriPrezzoForm form, BindingResult binding, Model model,
			RedirectAttributes redirect) {
		if (binding.hasErrors()) {
			return mostraForm(form, COSTO_ESEMPIO, null, model);
		}
		prezziService.salva(form);
		redirect.addFlashAttribute("messaggio", "Parametri dei prezzi salvati");
		return "redirect:/prezzi";
	}

	/** Frammento HTMX: simulazione con i valori attuali del form, anche non salvati. */
	@GetMapping("/simulazione")
	String simulazione(@Valid @ModelAttribute("form") ParametriPrezzoForm form, BindingResult binding,
			@RequestParam(required = false) BigDecimal costo, Model model) {
		BigDecimal costoEsempio = costo == null || costo.signum() < 0 ? COSTO_ESEMPIO : costo;
		model.addAttribute("costo", costoEsempio);
		model.addAttribute("calcolo", binding.hasErrors() ? null : prezziService.calcolo(form));
		return "prezzi/parametri :: simulazione";
	}

	private String mostraForm(ParametriPrezzoForm form, BigDecimal costo, CalcoloPrezzi calcolo, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("costo", costo);
		model.addAttribute("calcolo", calcolo);
		model.addAttribute("piattaforme", piattaformaService.tutte());
		model.addAttribute("speseUltimoAnno", prezziService.speseGeneraliUltimoAnno());
		model.addAttribute("ricaviUltimoAnno", prezziService.ricaviUltimoAnno());
		return "prezzi/parametri";
	}

}

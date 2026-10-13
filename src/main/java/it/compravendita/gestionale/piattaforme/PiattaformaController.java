package it.compravendita.gestionale.piattaforme;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/piattaforme")
public class PiattaformaController {

	private final PiattaformaService piattaformaService;

	public PiattaformaController(PiattaformaService piattaformaService) {
		this.piattaformaService = piattaformaService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "piattaforme";
	}

	@GetMapping
	String lista(Model model) {
		model.addAttribute("piattaforme", piattaformaService.tutte());
		model.addAttribute("conteggi", piattaformaService.contaVendite());
		return "piattaforme/lista";
	}

	@GetMapping("/nuova")
	String nuova(Model model) {
		return mostraForm(new PiattaformaForm(), null, model);
	}

	@PostMapping
	String crea(@Valid @ModelAttribute("form") PiattaformaForm form, BindingResult binding, Model model,
			RedirectAttributes redirect) {
		return salva(null, form, binding, model, redirect);
	}

	@GetMapping("/{id}/modifica")
	String modifica(@PathVariable Long id, Model model) {
		Piattaforma piattaforma = piattaformaService.trova(id);
		return mostraForm(PiattaformaForm.da(piattaforma), piattaforma, model);
	}

	@PostMapping("/{id}")
	String aggiorna(@PathVariable Long id, @Valid @ModelAttribute("form") PiattaformaForm form, BindingResult binding,
			Model model, RedirectAttributes redirect) {
		return salva(id, form, binding, model, redirect);
	}

	@PostMapping("/{id}/elimina")
	String elimina(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			piattaformaService.elimina(id);
			redirect.addFlashAttribute("messaggio", "Piattaforma eliminata");
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
		}
		return "redirect:/piattaforme";
	}

	private String salva(Long id, PiattaformaForm form, BindingResult binding, Model model,
			RedirectAttributes redirect) {
		Piattaforma esistente = id == null ? null : piattaformaService.trova(id);
		if (binding.hasErrors()) {
			return mostraForm(form, esistente, model);
		}
		try {
			piattaformaService.salva(id, form);
			redirect.addFlashAttribute("messaggio", id == null ? "Piattaforma creata" : "Piattaforma aggiornata");
			return "redirect:/piattaforme";
		}
		catch (OperazioneNonConsentitaException ex) {
			binding.rejectValue("nome", "duplicato", ex.getMessage());
			return mostraForm(form, esistente, model);
		}
	}

	private String mostraForm(PiattaformaForm form, Piattaforma piattaforma, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("piattaforma", piattaforma);
		return "piattaforme/form";
	}

}

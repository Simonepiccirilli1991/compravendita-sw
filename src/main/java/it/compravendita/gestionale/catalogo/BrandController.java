package it.compravendita.gestionale.catalogo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.compravendita.gestionale.common.OperazioneNonConsentitaException;

@Controller
@RequestMapping("/brand")
public class BrandController {

	private final BrandService brandService;

	public BrandController(BrandService brandService) {
		this.brandService = brandService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "brand";
	}

	@GetMapping
	String lista(Model model) {
		model.addAttribute("brands", brandService.tutti());
		model.addAttribute("conteggi", brandService.contaArticoliPerBrand());
		return "brand/lista";
	}

	@PostMapping
	String crea(@RequestParam String nome, RedirectAttributes redirect) {
		return esegui(redirect, "Brand creato", () -> brandService.salva(null, nome, true));
	}

	@PostMapping("/{id}")
	String aggiorna(@PathVariable Long id, @RequestParam String nome,
			@RequestParam(defaultValue = "false") boolean attivo, RedirectAttributes redirect) {
		return esegui(redirect, "Brand aggiornato", () -> brandService.salva(id, nome, attivo));
	}

	@PostMapping("/{id}/elimina")
	String elimina(@PathVariable Long id, RedirectAttributes redirect) {
		return esegui(redirect, "Brand eliminato", () -> brandService.elimina(id));
	}

	private String esegui(RedirectAttributes redirect, String messaggio, Runnable operazione) {
		try {
			operazione.run();
			redirect.addFlashAttribute("messaggio", messaggio);
		}
		catch (OperazioneNonConsentitaException ex) {
			redirect.addFlashAttribute("errore", ex.getMessage());
		}
		return "redirect:/brand";
	}

}

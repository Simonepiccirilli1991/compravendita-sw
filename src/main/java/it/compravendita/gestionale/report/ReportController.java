package it.compravendita.gestionale.report;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.AcquistoService;
import it.compravendita.gestionale.common.Formati;

@Controller
@RequestMapping("/report")
public class ReportController {

	private final CalcoloEconomicoService calcoloEconomicoService;

	private final AcquistoService acquistoService;

	public ReportController(CalcoloEconomicoService calcoloEconomicoService, AcquistoService acquistoService) {
		this.calcoloEconomicoService = calcoloEconomicoService;
		this.acquistoService = acquistoService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "report";
	}

	@GetMapping
	String report(@ModelAttribute("periodo") Periodo periodo,
			@RequestParam(defaultValue = "CATEGORIA") Dimensione per, Model model) {
		model.addAttribute("riepilogo", calcoloEconomicoService.riepilogo(periodo));
		model.addAttribute("righe", calcoloEconomicoService.per(per, periodo));
		model.addAttribute("per", per);
		model.addAttribute("dimensioni", Dimensione.values());
		model.addAttribute("scorciatoie", scorciatoie());
		return "report/report";
	}

	/** Riepilogo per anno solare, con le rimanenze al 31/12. */
	@GetMapping("/annuale")
	String annuale(Model model) {
		model.addAttribute("conti", calcoloEconomicoService.anniDisponibili().stream()
			.map(calcoloEconomicoService::contoAnnuale)
			.toList());
		return "report/annuale";
	}

	/** Frammento HTMX per la pagina dell'acquisto: quanto ha reso finora. */
	@GetMapping("/acquisti/{id}")
	String acquisto(@PathVariable Long id, Model model) {
		Acquisto acquisto = acquistoService.trova(id);
		model.addAttribute("acquisto", acquisto);
		model.addAttribute("risultato", calcoloEconomicoService.perAcquisto(acquisto));
		return "report/acquisto :: risultato";
	}

	/** Periodi rapidi: etichetta → [da, a]. */
	private static Map<String, Periodo> scorciatoie() {
		LocalDate oggi = LocalDate.now(Formati.FUSO);
		Map<String, Periodo> periodi = new LinkedHashMap<>();
		periodi.put("Questo mese", new Periodo(oggi.withDayOfMonth(1), oggi.withDayOfMonth(oggi.lengthOfMonth())));
		periodi.put("Ultimi 30 giorni", new Periodo(oggi.minusDays(29), oggi));
		periodi.put("Quest'anno", Periodo.annoCorrente());
		periodi.put("Anno scorso", new Periodo(LocalDate.of(oggi.getYear() - 1, 1, 1),
				LocalDate.of(oggi.getYear() - 1, 12, 31)));
		periodi.put("Sempre", Periodo.sempre());
		return periodi;
	}

}

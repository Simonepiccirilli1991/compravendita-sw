package it.compravendita.gestionale.dashboard;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.report.Periodo;

@Controller
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@ModelAttribute("activePage")
	String activePage() {
		return "dashboard";
	}

	/**
	 * Senza date mostra gli ultimi 12 mesi; {@code tutto=true} mostra l'intero storico.
	 *
	 * @param giorniFermo soglia oltre la quale la merce in magazzino è considerata ferma
	 */
	@GetMapping("/dashboard")
	String dashboard(@ModelAttribute("periodo") Periodo periodo, @RequestParam(defaultValue = "false") boolean tutto,
			@RequestParam(defaultValue = "90") int giorniFermo, Model model) {
		Periodo scelto = periodo.isIllimitato() && !tutto ? DashboardService.ultimi12Mesi() : periodo;
		model.addAttribute("periodo", scelto);
		model.addAttribute("tutto", tutto);
		int soglia = Math.max(1, giorniFermo);
		model.addAttribute("d", dashboardService.carica(scelto, soglia));
		model.addAttribute("sogliaFermi", LocalDate.now(Formati.FUSO).minusDays(soglia));
		model.addAttribute("scorciatoie", scorciatoie());
		return "dashboard";
	}

	private static Map<String, Periodo> scorciatoie() {
		LocalDate oggi = LocalDate.now(Formati.FUSO);
		Map<String, Periodo> periodi = new LinkedHashMap<>();
		periodi.put("30 giorni", new Periodo(oggi.minusDays(29), oggi));
		periodi.put("Questo mese", new Periodo(oggi.withDayOfMonth(1), oggi.withDayOfMonth(oggi.lengthOfMonth())));
		periodi.put("12 mesi", DashboardService.ultimi12Mesi());
		periodi.put("Quest'anno", Periodo.annoCorrente());
		return periodi;
	}

}

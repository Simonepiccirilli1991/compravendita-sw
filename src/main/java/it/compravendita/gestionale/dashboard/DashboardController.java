package it.compravendita.gestionale.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

	@GetMapping("/dashboard")
	String dashboard(Model model) {
		model.addAttribute("activePage", "dashboard");
		return "dashboard";
	}

}

package it.compravendita.gestionale.desktop;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/** Pulsante "Chiudi programma" della versione installata: ferma applicazione e database. */
@Controller
@ConditionalOnProperty("app.desktop.pgsql-bin")
public class ChiusuraController {

	@PostMapping("/chiudi-programma")
	String chiudi() {
		AvvioDesktop.chiudi();
		return "backup/chiuso";
	}

}

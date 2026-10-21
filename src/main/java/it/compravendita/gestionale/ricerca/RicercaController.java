package it.compravendita.gestionale.ricerca;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.compravendita.gestionale.acquisti.AcquistoService;
import it.compravendita.gestionale.acquisti.FiltroAcquisti;
import it.compravendita.gestionale.catalogo.ArticoloService;
import it.compravendita.gestionale.catalogo.FiltroArticoli;
import it.compravendita.gestionale.vendite.FiltroVendite;
import it.compravendita.gestionale.vendite.VenditaService;

/**
 * Ricerca rapida da qualsiasi pagina: articoli (nome e attributi come n° certificato, n° set,
 * numero carta), acquisti (fornitore, canale, articoli) e vendite (acquirente, numero, articoli).
 */
@Controller
public class RicercaController {

	static final int MASSIMO = 10;

	private final ArticoloService articoloService;

	private final AcquistoService acquistoService;

	private final VenditaService venditaService;

	public RicercaController(ArticoloService articoloService, AcquistoService acquistoService,
			VenditaService venditaService) {
		this.articoloService = articoloService;
		this.acquistoService = acquistoService;
		this.venditaService = venditaService;
	}

	@GetMapping("/cerca")
	String cerca(@RequestParam(defaultValue = "") String q, Model model) {
		String testo = q.trim();
		model.addAttribute("q", testo);
		model.addAttribute("activePage", "cerca");
		if (testo.length() >= 2) {
			FiltroArticoli articoli = new FiltroArticoli();
			articoli.setQ(testo);
			FiltroAcquisti acquisti = new FiltroAcquisti();
			acquisti.setQ(testo);
			FiltroVendite vendite = new FiltroVendite();
			vendite.setQ(testo);
			model.addAttribute("articoli", articoloService.cerca(articoli, pagina(Sort.by("nome"))));
			model.addAttribute("acquisti",
					acquistoService.cerca(acquisti, pagina(Sort.by(Sort.Direction.DESC, "dataAcquisto", "id"))));
			model.addAttribute("vendite",
					venditaService.cerca(vendite, pagina(Sort.by(Sort.Direction.DESC, "dataVendita", "id"))));
		}
		return "ricerca/risultati";
	}

	private static Pageable pagina(Sort ordine) {
		return PageRequest.of(0, MASSIMO, ordine);
	}

}

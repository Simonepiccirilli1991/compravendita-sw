package it.compravendita.gestionale.esportazioni;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import it.compravendita.gestionale.acquisti.FiltroAcquisti;
import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;
import it.compravendita.gestionale.magazzino.FiltroMagazzino;
import it.compravendita.gestionale.report.CalcoloEconomicoService;
import it.compravendita.gestionale.spese.FiltroSpese;
import it.compravendita.gestionale.vendite.FiltroVendite;

/**
 * Download di acquisti, vendite, magazzino, spese e riepilogo annuale in CSV o Excel. I parametri di
 * filtro sono gli stessi delle rispettive liste.
 */
@Controller
@RequestMapping("/export")
public class EsportazioneController {

	static final MediaType XLSX = MediaType
		.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

	private final EsportazioneService esportazioneService;

	private final CalcoloEconomicoService calcolo;

	public EsportazioneController(EsportazioneService esportazioneService, CalcoloEconomicoService calcolo) {
		this.esportazioneService = esportazioneService;
		this.calcolo = calcolo;
	}

	@GetMapping("/acquisti.{formato}")
	ResponseEntity<byte[]> acquisti(@ModelAttribute FiltroAcquisti filtro, @PathVariable String formato) {
		return file("acquisti", formato, List.of(esportazioneService.acquisti(filtro)));
	}

	/** In Excel le vendite hanno due fogli: per vendita e per riga. */
	@GetMapping("/vendite.{formato}")
	ResponseEntity<byte[]> vendite(@ModelAttribute FiltroVendite filtro, @PathVariable String formato) {
		List<Tabella> tabelle = "xlsx".equals(formato)
				? List.of(esportazioneService.vendite(filtro), esportazioneService.righeVendita(filtro))
				: List.of(esportazioneService.vendite(filtro));
		return file("vendite", formato, tabelle);
	}

	@GetMapping("/magazzino.{formato}")
	ResponseEntity<byte[]> magazzino(@ModelAttribute FiltroMagazzino filtro, @PathVariable String formato) {
		return file("magazzino", formato, List.of(esportazioneService.magazzino(filtro)));
	}

	@GetMapping("/spese.{formato}")
	ResponseEntity<byte[]> spese(@ModelAttribute FiltroSpese filtro, @PathVariable String formato) {
		return file("spese", formato, List.of(esportazioneService.spese(filtro)));
	}

	@GetMapping("/annuale.{formato}")
	ResponseEntity<byte[]> annuale(@PathVariable String formato) {
		return file("riepilogo-annuale", formato, List.of(esportazioneService.annuale(calcolo.anniDisponibili())));
	}

	private static ResponseEntity<byte[]> file(String nome, String formato, List<Tabella> tabelle) {
		String nomeFile = nome + "-" + LocalDate.now(Formati.FUSO) + "." + formato;
		byte[] contenuto;
		MediaType tipo;
		switch (formato) {
			case "csv" -> {
				contenuto = CsvScrittore.scrivi(tabelle.getFirst());
				tipo = CSV;
			}
			case "xlsx" -> {
				contenuto = ExcelScrittore.scrivi(tabelle);
				tipo = XLSX;
			}
			default -> throw new RisorsaNonTrovataException("Formato " + formato, null);
		}
		return ResponseEntity.ok()
			.contentType(tipo)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nomeFile).build().toString())
			.body(contenuto);
	}

}

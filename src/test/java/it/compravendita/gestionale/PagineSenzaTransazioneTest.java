package it.compravendita.gestionale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.BrandRepository;
import it.compravendita.gestionale.common.AbstractEntity;
import it.compravendita.gestionale.demo.DatiDemo;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;
import it.compravendita.gestionale.report.Dimensione;
import it.compravendita.gestionale.spese.SpesaRepository;
import it.compravendita.gestionale.vendite.VenditaRepository;

/**
 * Apre tutte le pagine dell'applicazione come farebbe il browser, cioè <b>senza</b> la transazione
 * aperta dagli altri test: così emergono i dati lazy non caricati prima della vista
 * (LazyInitializationException, con open-in-view disattivato). Usa un contesto e un database
 * propri (proprietà diversa), perché i dati demo vengono salvati davvero.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.upload-dir=target/test-uploads-pagine")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PagineSenzaTransazioneTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	DatiDemo datiDemo;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	BrandRepository brandRepository;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	RigaAcquistoRepository rigaAcquistoRepository;

	@Autowired
	VenditaRepository venditaRepository;

	@Autowired
	PiattaformaRepository piattaformaRepository;

	@Autowired
	SpesaRepository spesaRepository;

	@BeforeAll
	void caricaDati() {
		if (acquistoRepository.count() == 0) {
			datiDemo.genera(LocalDate.now());
		}
	}

	@Test
	void pagineGenerali() throws Exception {
		List<String> pagine = new ArrayList<>(List.of("/dashboard", "/dashboard?tutto=true",
				"/dashboard?giorniFermo=30", "/articoli", "/articoli/nuovo", "/brand", "/acquisti", "/acquisti/nuovo",
				"/magazzino", "/magazzino/lotti", "/magazzino/rettifiche", "/vendite",
				"/vendite/nuova", "/piattaforme", "/piattaforme/nuova", "/spese", "/spese/nuova", "/report",
				"/report/annuale", "/import", "/import/modello.csv", "/cerca?q=stitch", "/cerca?q=ceramica",
				"/acquisti/elenco-articoli", "/vendite/elenco-articoli", "/articoli/campi?categoria=SET_REGALO",
				"/magazzino?ordine=VALORE", "/magazzino/lotti?stato=IN_VENDITA", "/vendite?stato=RESA"));
		for (Dimensione dimensione : Dimensione.values()) {
			pagine.add("/report?per=" + dimensione);
		}
		for (String esportazione : List.of("acquisti", "vendite", "magazzino", "spese", "annuale")) {
			pagine.add("/export/" + esportazione + ".csv");
			pagine.add("/export/" + esportazione + ".xlsx");
		}
		verifica(pagine);
	}

	@Test
	void dettagliDiOgniArticolo() throws Exception {
		List<String> pagine = new ArrayList<>();
		for (Long id : ids(articoloRepository.findAll())) {
			pagine.addAll(List.of("/articoli/" + id, "/articoli/" + id + "/modifica", "/magazzino/articoli/" + id,
					"/vendite/articoli/" + id, "/vendite/nuova?articoloId=" + id));
		}
		verifica(pagine);
	}

	@Test
	void dettagliDiOgniAcquistoELotto() throws Exception {
		List<String> pagine = new ArrayList<>();
		for (Long id : ids(acquistoRepository.findAll())) {
			pagine.addAll(List.of("/acquisti/" + id, "/acquisti/" + id + "/modifica", "/report/acquisti/" + id));
		}
		rigaAcquistoRepository.findAll().forEach(lotto -> {
			if (lotto.getQuantitaResidua() > 0) {
				pagine.addAll(List.of("/magazzino/lotti/" + lotto.getId() + "/rettifica",
						"/vendite/nuova?lottoId=" + lotto.getId()));
			}
		});
		verifica(pagine);
	}

	@Test
	void dettagliDiOgniVendita() throws Exception {
		List<String> pagine = new ArrayList<>();
		for (Long id : ids(venditaRepository.findAll())) {
			pagine.addAll(List.of("/vendite/" + id, "/vendite/" + id + "/modifica", "/vendite/" + id + "/annulla",
					"/vendite/" + id + "/reso"));
		}
		verifica(pagine);
	}

	@Test
	void anagraficheESpese() throws Exception {
		List<String> pagine = new ArrayList<>();
		ids(piattaformaRepository.findAll()).forEach(id -> pagine.add("/piattaforme/" + id + "/modifica"));
		ids(spesaRepository.findAll()).forEach(id -> pagine.add("/spese/" + id + "/modifica"));
		assertThat(brandRepository.count()).isPositive();
		verifica(pagine);
	}

	/** Ogni pagina deve rispondere senza errori (i redirect sono ammessi, es. vendita già stornata). */
	private void verifica(List<String> pagine) throws Exception {
		assertThat(pagine).isNotEmpty();
		List<String> errori = new ArrayList<>();
		for (String pagina : pagine) {
			MockHttpServletRequestBuilder richiesta = get(pagina);
			if (pagina.contains("/articoli/") && pagina.startsWith("/magazzino")
					|| pagina.startsWith("/vendite/articoli") || pagina.startsWith("/report/acquisti")
					|| pagina.endsWith("elenco-articoli") || pagina.startsWith("/articoli/campi")) {
				richiesta.header("HX-Request", "true");
			}
			MockHttpServletResponse risposta = mvc.perform(richiesta).andReturn().getResponse();
			if (risposta.getStatus() >= 400) {
				errori.add(pagina + " -> " + risposta.getStatus());
			}
		}
		assertThat(errori).as("pagine con errori").isEmpty();
	}

	private static List<Long> ids(List<? extends AbstractEntity> entita) {
		return entita.stream().map(AbstractEntity::getId).toList();
	}

}

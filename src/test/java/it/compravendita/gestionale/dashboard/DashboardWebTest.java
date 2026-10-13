package it.compravendita.gestionale.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.TestcontainersConfiguration;
import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.dashboard.DashboardService.Dashboard;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;
import it.compravendita.gestionale.report.Periodo;
import it.compravendita.gestionale.report.Risultato;
import it.compravendita.gestionale.report.Velocita;
import it.compravendita.gestionale.vendite.RigaVendita;
import it.compravendita.gestionale.vendite.Vendita;
import it.compravendita.gestionale.vendite.VenditaRepository;
import jakarta.persistence.EntityManager;

/**
 * Carte comprate 100 giorni fa (4 a 10), LEGO 60 giorni fa (1 a 100), un articolo fermo da 200 giorni.
 * Vendute 3 carte a 30 dopo 60 giorni e il LEGO a 80 (in perdita) dopo 30 giorni.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class DashboardWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	DashboardService dashboardService;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	PiattaformaRepository piattaformaRepository;

	@Autowired
	VenditaRepository venditaRepository;

	@Autowired
	EntityManager em;

	final LocalDate oggi = LocalDate.now(Formati.FUSO);

	@BeforeEach
	void scenario() {
		Articolo carta = articoloRepository.save(new Articolo("Gengar", Categoria.CARTA_SINGOLA, null));
		Articolo lego = articoloRepository.save(new Articolo("Batmobile", Categoria.LEGO, null));
		Articolo vecchio = articoloRepository.save(new Articolo("Console impolverata", Categoria.VIDEOGIOCO, null));
		RigaAcquisto carte = lotto(carta, oggi.minusDays(100), 4, "10");
		RigaAcquisto legoLotto = lotto(lego, oggi.minusDays(60), 1, "100");
		lotto(vecchio, oggi.minusDays(200), 1, "50");

		var ebay = piattaformaRepository.findByNomeIgnoreCase("eBay").orElseThrow();
		Vendita v1 = new Vendita(oggi.minusDays(40), ebay);
		RigaVendita r1 = new RigaVendita(carta, 3, new BigDecimal("30"));
		r1.allocaDa(carte, 3);
		v1.aggiungiRiga(r1);
		Vendita v2 = new Vendita(oggi.minusDays(30), ebay);
		RigaVendita r2 = new RigaVendita(lego, 1, new BigDecimal("80"));
		r2.allocaDa(legoLotto, 1);
		v2.aggiungiRiga(r2);
		venditaRepository.saveAll(List.of(v1, v2));
		em.flush();
	}

	@Test
	void kpiHotItemsEAvvisi() {
		Dashboard d = dashboardService.carica(Periodo.sempre(), 90);

		assertThat(d.riepilogo().vendite().lordo()).isEqualByComparingTo("170");
		assertThat(d.riepilogo().vendite().profitto()).isEqualByComparingTo("40");
		assertThat(d.riepilogo().acquistato()).isEqualByComparingTo("190");
		assertThat(d.magazzino().valore()).isEqualByComparingTo("60");
		// (3 x 60 + 1 x 30) / 4 pezzi
		assertThat(d.tempoMedioVendita()).isEqualByComparingTo("52.5");

		assertThat(d.topPezzi()).extracting(Risultato::etichetta).containsExactly("Gengar", "Batmobile");
		assertThat(d.topProfitto()).extracting(Risultato::etichetta).containsExactly("Gengar", "Batmobile");
		assertThat(d.topRoi().getFirst().roi()).isEqualByComparingTo("200");
		assertThat(d.velocitaCategorie()).extracting(Velocita::etichetta).containsExactly("LEGO", "Carta singola");
		assertThat(d.velocitaCategorie().getFirst().giorniMedi()).isEqualByComparingTo("30");

		// fermi oltre 90 giorni: la carta rimasta e la console
		assertThat(d.articoliFermi()).isEqualTo(2);
		assertThat(d.valoreFermi()).isEqualByComparingTo("60");
		assertThat(d.fermi().getFirst().nome()).isEqualTo("Console impolverata");
		assertThat(dashboardService.carica(Periodo.sempre(), 150).articoliFermi()).isEqualTo(1);

		assertThat(d.inPerdita()).singleElement().satisfies(p -> {
			assertThat(p.articolo()).isEqualTo("Batmobile");
			assertThat(p.profitto()).isEqualByComparingTo("-20");
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void graficoMensileConMesiVuoti() {
		Periodo periodo = new Periodo(oggi.minusMonths(5).withDayOfMonth(1), oggi);

		Map<String, Object> mesi = (Map<String, Object>) dashboardService.carica(periodo, 90).grafici().get("mesi");

		assertThat((List<String>) mesi.get("etichette")).hasSize(6);
		List<BigDecimal> fatturato = (List<BigDecimal>) mesi.get("fatturato");
		assertThat(fatturato.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("170");
		assertThat(fatturato.getFirst()).isEqualByComparingTo("0");
	}

	@Test
	void paginaDashboard() throws Exception {
		mvc.perform(get("/dashboard"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Hot items")))
			.andExpect(content().string(containsString("Gengar")))
			.andExpect(content().string(containsString("Console impolverata")))
			.andExpect(content().string(containsString("grafico-mesi")))
			.andExpect(content().string(containsString("52,5 giorni")));
		mvc.perform(get("/dashboard").param("tutto", "true").param("giorniFermo", "365"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Nessun articolo fermo oltre la soglia")));
		mvc.perform(get("/dashboard").param("da", "2020-01-01").param("a", "2020-12-31"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Nessuna vendita nel periodo")));
	}

	private RigaAcquisto lotto(Articolo articolo, LocalDate data, int quantita, String prezzo) {
		Acquisto acquisto = new Acquisto(data);
		RigaAcquisto riga = new RigaAcquisto(articolo, quantita, new BigDecimal(prezzo));
		acquisto.aggiungiRiga(riga);
		acquistoRepository.save(acquisto);
		return riga;
	}

}

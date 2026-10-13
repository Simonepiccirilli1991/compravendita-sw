package it.compravendita.gestionale.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.magazzino.GradingRepository;
import it.compravendita.gestionale.magazzino.RettificaMagazzinoRepository;
import it.compravendita.gestionale.magazzino.StatoGrading;
import it.compravendita.gestionale.report.CalcoloEconomicoService;
import it.compravendita.gestionale.report.Periodo;
import it.compravendita.gestionale.report.Risultato;
import it.compravendita.gestionale.vendite.StatoVendita;
import it.compravendita.gestionale.vendite.Vendita;
import it.compravendita.gestionale.vendite.VenditaRepository;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class DatiDemoTest {

	@Autowired
	DatiDemo datiDemo;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	VenditaRepository venditaRepository;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	GradingRepository gradingRepository;

	@Autowired
	RettificaMagazzinoRepository rettificaRepository;

	@Autowired
	CalcoloEconomicoService calcoloEconomicoService;

	@Autowired
	EntityManager em;

	@Autowired
	MockMvc mvc;

	@Test
	void generaUnAnnoDiAttivitaCoerente() throws Exception {
		long acquistiPrima = acquistoRepository.count();
		long venditePrima = venditaRepository.count();
		Risultato prima = calcoloEconomicoService.totale(Periodo.sempre());
		LocalDate oggi = LocalDate.now();

		datiDemo.genera(oggi);
		em.flush();
		em.clear();

		assertThat(acquistoRepository.count() - acquistiPrima).isGreaterThan(25);
		assertThat(venditaRepository.count() - venditePrima).isGreaterThan(40);
		assertThat(venditaRepository.findAll()).extracting(Vendita::getStato)
			.contains(StatoVendita.COMPLETATA, StatoVendita.RESA, StatoVendita.ANNULLATA);
		assertThat(venditaRepository.findAll()).allMatch(v -> !v.getDataVendita().isAfter(oggi));
		assertThat(gradingRepository.findAll()).extracting(g -> g.getStato())
			.contains(StatoGrading.RIENTRATO, StatoGrading.INVIATO);
		assertThat(articoloRepository.findAll()).anyMatch(a -> a.getCategoria() == Categoria.CARTA_GRADATA);
		assertThat(rettificaRepository.count()).isPositive();

		// nel complesso l'attività è in utile, con qualche vendita in perdita
		Risultato totale = calcoloEconomicoService.totale(Periodo.sempre());
		assertThat(totale.profitto().subtract(prima.profitto())).isPositive();
		assertThat(calcoloEconomicoService.venditeInPerdita(Periodo.sempre(), 50)).isNotEmpty();

		mvc.perform(get("/dashboard"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Charizard")));
		mvc.perform(get("/report").param("per", "PIATTAFORMA"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Cardmarket")));
		mvc.perform(get("/magazzino"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("LEGO Titanic")));
	}

}

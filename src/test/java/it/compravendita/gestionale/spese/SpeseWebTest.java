package it.compravendita.gestionale.spese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.report.CalcoloEconomicoService;
import it.compravendita.gestionale.report.Periodo;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class SpeseWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	SpesaRepository spesaRepository;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	ElencoArticoli elencoArticoli;

	@Autowired
	CalcoloEconomicoService calcolo;

	@Autowired
	EntityManager em;

	private static final Periodo MARZO_2031 = new Periodo(LocalDate.of(2031, 3, 1), LocalDate.of(2031, 3, 31));

	@Test
	void registraSpesaCheEntraNelProfittoNetto() throws Exception {
		BigDecimal prima = calcolo.riepilogo(MARZO_2031).speseGenerali();

		mvc.perform(post("/spese").with(csrf())
				.param("dataSpesa", "2031-03-05")
				.param("categoria", "MATERIALI")
				.param("descrizione", "  Toploader e bustine  ")
				.param("importo", "24.90")
				.param("note", "Ordine Amazon"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/spese"))
			.andExpect(flash().attribute("messaggio", "Spesa registrata"));
		em.flush();

		assertThat(calcolo.riepilogo(MARZO_2031).speseGenerali().subtract(prima)).isEqualByComparingTo("24.90");
		mvc.perform(get("/spese").param("da", "2031-03-01").param("a", "2031-03-31"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Toploader e bustine"),
					containsString("Materiali e imballaggi"), containsString("24,90"))));
	}

	@Test
	void filtroPerCategoriaETesto() throws Exception {
		spesaRepository.save(new Spesa(LocalDate.of(2031, 3, 1), CategoriaSpesa.ABBONAMENTO, "Negozio eBay",
				new BigDecimal("19.50")));
		spesaRepository.save(new Spesa(LocalDate.of(2031, 3, 2), CategoriaSpesa.FIERA, "Tavolo Lucca Comics",
				new BigDecimal("120")));

		mvc.perform(get("/spese").param("categoria", "FIERA").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(view().name("spese/lista :: risultati"))
			.andExpect(content().string(allOf(containsString("Tavolo Lucca Comics"),
					not(containsString("Negozio eBay")))));
		mvc.perform(get("/spese").param("q", "ebay"))
			.andExpect(content().string(allOf(containsString("Negozio eBay"), not(containsString("Lucca")))));
	}

	@Test
	void modificaConArticoloCollegatoEdElimina() throws Exception {
		Spesa spesa = spesaRepository.save(new Spesa(LocalDate.of(2031, 3, 10), CategoriaSpesa.SPEDIZIONE,
				"Assicurazione spedizione", new BigDecimal("8")));
		Articolo articolo = articoloRepository.save(new Articolo("Tazza Darth Vader 3D", Categoria.TAZZA, null));

		mvc.perform(get("/spese/{id}/modifica", spesa.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Assicurazione spedizione")));
		mvc.perform(post("/spese/{id}", spesa.getId()).with(csrf())
				.param("dataSpesa", "2031-03-11")
				.param("categoria", "SPEDIZIONE")
				.param("descrizione", "Assicurazione spedizione")
				.param("importo", "9.50")
				.param("articoloEtichetta", elencoArticoli.etichetta(articolo)))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("messaggio", "Spesa aggiornata"));
		em.flush();
		em.clear();
		Spesa aggiornata = spesaRepository.findConArticoloById(spesa.getId()).orElseThrow();
		assertThat(aggiornata.getImporto()).isEqualByComparingTo("9.50");
		assertThat(aggiornata.getArticolo().getId()).isEqualTo(articolo.getId());

		mvc.perform(post("/spese/{id}/elimina", spesa.getId()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("messaggio", "Spesa eliminata"));
		em.flush();
		assertThat(spesaRepository.findById(spesa.getId())).isEmpty();
	}

	@Test
	void erroriDiValidazione() throws Exception {
		mvc.perform(post("/spese").with(csrf())
				.param("dataSpesa", "2031-03-05")
				.param("importo", "-3")
				.param("articoloEtichetta", "articolo inventato"))
			.andExpect(status().isOk())
			.andExpect(view().name("spese/form"))
			.andExpect(model().attributeHasFieldErrors("form", "categoria", "descrizione", "importo",
					"articoloEtichetta"))
			.andExpect(content().string(containsString("Scegli un articolo dall&#39;elenco o lascia vuoto")));
	}

	@Test
	void esportazioneCsv() throws Exception {
		spesaRepository.save(new Spesa(LocalDate.of(2031, 3, 1), CategoriaSpesa.ABBONAMENTO, "Negozio eBay",
				new BigDecimal("19.50")));

		byte[] csv = mvc.perform(get("/export/spese.csv").param("da", "2031-03-01").param("a", "2031-03-31"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();

		assertThat(new String(csv, StandardCharsets.UTF_8))
			.contains("Data;Categoria;Descrizione;Importo;Articolo;Note")
			.contains("01/03/2031;Abbonamento;Negozio eBay;19,50;;");
	}

}

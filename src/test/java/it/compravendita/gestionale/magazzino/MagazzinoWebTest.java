package it.compravendita.gestionale.magazzino;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.AttributiArticolo;
import it.compravendita.gestionale.catalogo.Brand;
import it.compravendita.gestionale.catalogo.BrandRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class MagazzinoWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	BrandRepository brandRepository;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	RigaAcquistoRepository rigaAcquistoRepository;

	@Autowired
	RettificaMagazzinoRepository rettificaRepository;

	@Autowired
	EntityManager em;

	@Test
	void giacenzeAggregatePerArticolo() throws Exception {
		Articolo lego = articoloRepository.save(new Articolo("Hogwarts Castle", Categoria.TAZZA_TERMICA, null));
		lotto(lego, LocalDate.now().minusDays(200), 2, "300");
		lotto(lego, LocalDate.now().minusDays(10), 1, "330");
		RigaAcquisto venduto = lotto(lego, LocalDate.now().minusDays(5), 1, "999");
		venduto.preleva(1);
		em.flush();

		mvc.perform(get("/magazzino"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("riepilogo"))
			.andExpect(content().string(containsString("Hogwarts Castle")))
			// 2 x 300 + 330, il lotto venduto non conta
			.andExpect(content().string(containsString("930,00")))
			.andExpect(content().string(containsString("310,00")));

		mvc.perform(get("/magazzino").param("categoria", "BICCHIERE").param("ordine", "VALORE")
				.header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Hogwarts Castle"))))
			.andExpect(content().string(not(containsString("<html"))));
	}

	@Test
	void riepilogoECambioStato() throws Exception {
		Articolo gioco = articoloRepository.save(new Articolo("Pokémon Rosso", Categoria.BICCHIERE, null));
		RigaAcquisto lotto = lotto(gioco, LocalDate.now(), 3, "20");
		lotto.setStato(StatoOggetto.IN_ARRIVO);
		em.flush();

		mvc.perform(post("/magazzino/lotti/{id}/stato", lotto.getId()).with(csrf())
				.param("stato", "IN_VENDITA").param("ritorno", "/articoli/" + gioco.getId()))
			.andExpect(redirectedUrl("/articoli/" + gioco.getId()));
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.IN_VENDITA);

		mvc.perform(post("/magazzino/lotti/{id}/stato", lotto.getId()).with(csrf())
				.param("stato", "VENDUTO").param("ritorno", "https://esempio.it"))
			.andExpect(redirectedUrl("/magazzino/lotti"))
			.andExpect(flash().attribute("errore", containsString("Non puoi passare")));

		mvc.perform(get("/magazzino/lotti").param("stato", "IN_VENDITA"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Pokémon Rosso")));
	}

	@Test
	void rettificaEAnnullamento() throws Exception {
		Articolo carta = articoloRepository.save(new Articolo("Gyarados", Categoria.TAZZA, null));
		RigaAcquisto lotto = lotto(carta, LocalDate.now(), 3, "4");
		em.flush();

		mvc.perform(post("/magazzino/lotti/{id}/rettifica", lotto.getId()).with(csrf())
				.param("data", "2026-06-01").param("quantita", "5").param("motivo", "PERSO"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errore", containsString("al massimo 3")));

		mvc.perform(post("/magazzino/lotti/{id}/rettifica", lotto.getId()).with(csrf())
				.param("data", "2026-06-01").param("quantita", "3").param("motivo", "RESO_FORNITORE")
				.param("importoRecuperato", "10"))
			.andExpect(redirectedUrl("/magazzino/rettifiche"));
		assertThat(lotto.getQuantitaResidua()).isZero();
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.RESO);

		RettificaMagazzino rettifica = rettificaRepository.findAll().getFirst();
		assertThat(rettifica.getPerdita()).isEqualByComparingTo("2");
		mvc.perform(get("/magazzino/rettifiche"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Gyarados")));

		mvc.perform(post("/magazzino/rettifiche/{id}/annulla", rettifica.getId()).with(csrf()))
			.andExpect(redirectedUrl("/magazzino/rettifiche"));
		assertThat(lotto.getQuantitaResidua()).isEqualTo(3);
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.IN_STOCK);
		assertThat(rettificaRepository.count()).isZero();
	}

	private RigaAcquisto lotto(Articolo articolo, LocalDate data, int quantita, String prezzo) {
		Acquisto acquisto = new Acquisto(data);
		RigaAcquisto riga = new RigaAcquisto(articolo, quantita, new BigDecimal(prezzo));
		acquisto.aggiungiRiga(riga);
		acquistoRepository.save(acquisto);
		return riga;
	}

}

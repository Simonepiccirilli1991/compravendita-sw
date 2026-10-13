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
	GradingRepository gradingRepository;

	@Autowired
	EntityManager em;

	@Test
	void giacenzeAggregatePerArticolo() throws Exception {
		Articolo lego = articoloRepository.save(new Articolo("Hogwarts Castle", Categoria.LEGO, null));
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

		mvc.perform(get("/magazzino").param("categoria", "VIDEOGIOCO").param("ordine", "VALORE")
				.header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Hogwarts Castle"))))
			.andExpect(content().string(not(containsString("<html"))));
	}

	@Test
	void riepilogoECambioStato() throws Exception {
		Articolo gioco = articoloRepository.save(new Articolo("Pokémon Rosso", Categoria.VIDEOGIOCO, null));
		RigaAcquisto lotto = lotto(gioco, LocalDate.now(), 3, "20");
		lotto.setStato(StatoOggetto.IN_ARRIVO);
		em.flush();

		mvc.perform(post("/magazzino/lotti/{id}/stato", lotto.getId()).with(csrf())
				.param("stato", "IN_VENDITA").param("ritorno", "/articoli/" + gioco.getId()))
			.andExpect(redirectedUrl("/articoli/" + gioco.getId()));
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.IN_VENDITA);

		mvc.perform(post("/magazzino/lotti/{id}/stato", lotto.getId()).with(csrf())
				.param("stato", "IN_GRADING").param("ritorno", "https://esempio.it"))
			.andExpect(redirectedUrl("/magazzino/lotti"))
			.andExpect(flash().attribute("errore", containsString("Non puoi passare")));

		mvc.perform(get("/magazzino/lotti").param("stato", "IN_VENDITA"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Pokémon Rosso")));
	}

	@Test
	void rettificaEAnnullamento() throws Exception {
		Articolo carta = articoloRepository.save(new Articolo("Gyarados", Categoria.CARTA_SINGOLA, null));
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

	@Test
	void flussoGradingCompleto() throws Exception {
		Brand pokemon = brandRepository.findByNomeIgnoreCase("Pokémon").orElseThrow();
		Articolo charizard = new Articolo("Charizard Base Set", Categoria.CARTA_SINGOLA, pokemon);
		charizard.setLingua("ENG");
		charizard.setAttributo(AttributiArticolo.SET, "Base Set");
		charizard.setAttributo(AttributiArticolo.NUMERO_CARTA, "4/102");
		articoloRepository.save(charizard);
		RigaAcquisto lotto = lotto(charizard, LocalDate.of(2026, 1, 15), 3, "100");
		em.flush();

		mvc.perform(get("/magazzino/lotti/{id}/grading", lotto.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("verrà separata")));

		mvc.perform(post("/magazzino/lotti/{id}/grading", lotto.getId()).with(csrf())
				.param("ente", "PSA").param("servizio", "Value").param("dataInvio", "2026-02-01")
				.param("costoPrevisto", "20"))
			.andExpect(redirectedUrl("/magazzino/grading"));

		Grading grading = gradingRepository.findAll().getFirst();
		RigaAcquisto carta = grading.getRigaAcquisto();
		assertThat(carta).isNotEqualTo(lotto);
		assertThat(carta.getStato()).isEqualTo(StatoOggetto.IN_GRADING);
		assertThat(carta.getCostoUnitarioEffettivo()).isEqualByComparingTo("100");
		assertThat(lotto.getQuantita()).isEqualTo(2);
		// la carta in grading non è vendibile
		assertThat(rigaAcquistoRepository.findDisponibiliFifo(charizard.getId())).containsExactly(lotto);

		mvc.perform(get("/magazzino/grading"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Charizard Base Set")));

		String location = mvc.perform(post("/magazzino/grading/{id}/rientro", grading.getId()).with(csrf())
				.param("dataRientro", "2026-04-01").param("nomeArticolo", "Charizard Base Set PSA 9")
				.param("voto", "9").param("numeroCertificato", "87654321").param("costo", "25"))
			.andExpect(redirectedUrlPattern("/articoli/*"))
			.andReturn().getResponse().getRedirectedUrl();

		Long gradatoId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		Articolo gradato = articoloRepository.findById(gradatoId).orElseThrow();
		assertThat(gradato.getCategoria()).isEqualTo(Categoria.CARTA_GRADATA);
		assertThat(gradato.getBrand()).isEqualTo(pokemon);
		assertThat(gradato.getLingua()).isEqualTo("ENG");
		assertThat(gradato.getAttributo(AttributiArticolo.ENTE_GRADING)).isEqualTo("PSA");
		assertThat(gradato.getAttributo(AttributiArticolo.NUMERO_CERTIFICATO)).isEqualTo("87654321");
		assertThat(gradato.getAttributo(AttributiArticolo.SET)).isEqualTo("Base Set");
		assertThat(grading.getStato()).isEqualTo(StatoGrading.RIENTRATO);
		assertThat(carta.getArticolo()).isEqualTo(gradato);
		assertThat(carta.getStato()).isEqualTo(StatoOggetto.IN_STOCK);
		assertThat(carta.getCostoUnitarioEffettivo()).isEqualByComparingTo("125");

		mvc.perform(get("/magazzino/articoli/{id}", gradatoId).header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lotti e giacenza")))
			.andExpect(content().string(containsString("125,00")));
		mvc.perform(get("/magazzino/articoli/{id}", charizard.getId()).header("HX-Request", "true"))
			.andExpect(content().string(containsString("voto 9")));

		// la carta originale ora ha un grading collegato e non si può eliminare
		mvc.perform(post("/articoli/{id}/elimina", charizard.getId()).with(csrf()))
			.andExpect(flash().attribute("errore", containsString("grading")));
	}

	@Test
	void annullaGradingEInvioNonConsentito() throws Exception {
		Articolo carta = articoloRepository.save(new Articolo("Lugia Neo Genesis", Categoria.CARTA_SINGOLA, null));
		RigaAcquisto lotto = lotto(carta, LocalDate.now(), 1, "60");
		Articolo lego = articoloRepository.save(new Articolo("Titanic", Categoria.LEGO, null));
		RigaAcquisto lottoLego = lotto(lego, LocalDate.now(), 1, "500");
		em.flush();

		mvc.perform(post("/magazzino/lotti/{id}/grading", lotto.getId()).with(csrf())
				.param("ente", "BGS").param("dataInvio", "2026-02-01"))
			.andExpect(redirectedUrl("/magazzino/grading"));
		// lotto da un solo pezzo: niente scorporo
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.IN_GRADING);
		List<Grading> elenco = gradingRepository.findAll();
		assertThat(elenco).singleElement().extracting(Grading::getRigaAcquisto).isEqualTo(lotto);

		mvc.perform(post("/magazzino/grading/{id}/annulla", elenco.getFirst().getId()).with(csrf()))
			.andExpect(redirectedUrl("/magazzino/grading"));
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.IN_STOCK);
		assertThat(lotto.getCostoUnitarioEffettivo()).isEqualByComparingTo("60");

		mvc.perform(post("/magazzino/lotti/{id}/grading", lottoLego.getId()).with(csrf())
				.param("ente", "PSA").param("dataInvio", "2026-02-01"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errore", containsString("solo carte singole")));
	}

	private RigaAcquisto lotto(Articolo articolo, LocalDate data, int quantita, String prezzo) {
		Acquisto acquisto = new Acquisto(data);
		RigaAcquisto riga = new RigaAcquisto(articolo, quantita, new BigDecimal(prezzo));
		acquisto.aggiungiRiga(riga);
		acquistoRepository.save(acquisto);
		return riga;
	}

}

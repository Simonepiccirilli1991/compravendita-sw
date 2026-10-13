package it.compravendita.gestionale.acquisti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.TestcontainersConfiguration;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.magazzino.StatoOggetto;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class AcquistiWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	ElencoArticoli elencoArticoli;

	@Autowired
	EntityManager em;

	@Test
	void registraAcquistoMistoConRipartizioneDeiCosti() throws Exception {
		Articolo carta = articoloRepository.save(new Articolo("Pikachu Promo", Categoria.CARTA_SINGOLA, null));
		Articolo lego = articoloRepository.save(new Articolo("Millennium Falcon", Categoria.LEGO, null));
		MockMultipartFile ricevuta = new MockMultipartFile("allegato", "ricevuta.pdf", "application/pdf",
				new byte[] { 37, 80, 68, 70 });

		String location = mvc.perform(multipart("/acquisti").file(ricevuta).with(csrf())
				.param("dataAcquisto", "2026-05-01")
				.param("fornitore", "Mario Rossi")
				.param("canale", "Subito")
				.param("costoSpedizione", "20")
				.param("metodoRipartizione", "PREZZO")
				.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(carta))
				.param("righe[0].quantita", "10")
				.param("righe[0].prezzoUnitario", "5")
				.param("righe[0].stato", "IN_STOCK")
				.param("righe[1].articoloEtichetta", elencoArticoli.etichetta(lego))
				.param("righe[1].quantita", "1")
				.param("righe[1].prezzoUnitario", "150")
				.param("righe[1].stato", "IN_ARRIVO")
				// riga lasciata vuota nel form: ignorata
				.param("righe[2].articoloEtichetta", "")
				.param("righe[2].stato", "IN_STOCK"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("/acquisti/*"))
			.andExpect(flash().attribute("messaggio", "Acquisto registrato"))
			.andReturn().getResponse().getRedirectedUrl();

		Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		em.flush();
		em.clear();
		Acquisto acquisto = acquistoRepository.findConRigheById(id).orElseThrow();
		assertThat(acquisto.getRighe()).hasSize(2);
		assertThat(acquisto.getTotale()).isEqualByComparingTo("220");
		assertThat(acquisto.getAllegatoPath()).startsWith("acquisti/").endsWith(".pdf");
		RigaAcquisto righeCarte = acquisto.getRighe().get(0);
		assertThat(righeCarte.getCostoUnitarioEffettivo()).isEqualByComparingTo("5.50");
		assertThat(acquisto.getRighe().get(1).getCostoUnitarioEffettivo()).isEqualByComparingTo("165");
		assertThat(acquisto.getRighe().get(1).getStato()).isEqualTo(StatoOggetto.IN_ARRIVO);

		mvc.perform(get("/acquisti/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Millennium Falcon")))
			.andExpect(content().string(containsString("165,00")));
	}

	@Test
	void senzaRigheOArticoloNonValidoMostraErrori() throws Exception {
		mvc.perform(post("/acquisti").with(csrf()).param("dataAcquisto", "2026-05-01"))
			.andExpect(status().isOk())
			.andExpect(view().name("acquisti/form"))
			.andExpect(content().string(containsString("Aggiungi almeno un articolo")));

		mvc.perform(post("/acquisti").with(csrf())
				.param("dataAcquisto", "2026-05-01")
				.param("righe[0].articoloEtichetta", "testo libero")
				.param("righe[0].quantita", "0")
				.param("righe[0].prezzoUnitario", "3"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "righe[0].articoloEtichetta", "righe[0].quantita"))
			.andExpect(content().string(containsString("Scegli un articolo dall&#39;elenco")));
		assertThat(acquistoRepository.count()).isZero();
	}

	@Test
	void frammentiHtmxRigheEAnteprima() throws Exception {
		mvc.perform(get("/acquisti/righe").param("azione", "aggiungi").param("righe[0].quantita", "2")
				.header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(header().string("HX-Trigger", "righeAggiornate"))
			.andExpect(content().string(containsString("righe[1].articoloEtichetta")))
			.andExpect(content().string(not(containsString("<html"))));

		mvc.perform(get("/acquisti/righe").param("azione", "rimuovi").param("indice", "0")
				.param("righe[0].quantita", "2").param("righe[1].quantita", "7"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"7\"")))
			.andExpect(content().string(not(containsString("righe[1].quantita"))));

		mvc.perform(get("/acquisti/anteprima")
				.param("costoSpedizione", "10")
				.param("metodoRipartizione", "PREZZO")
				.param("righe[0].articoloEtichetta", "Carta #1")
				.param("righe[0].quantita", "2")
				.param("righe[0].prezzoUnitario", "5"))
			.andExpect(status().isOk())
			// (2 x 5 + 10 di spedizione) / 2 pezzi
			.andExpect(content().string(containsString("10,00")))
			.andExpect(content().string(containsString("20,00")));
	}

	@Test
	void modificaRiripartisceIConservaLeRigheMovimentate() throws Exception {
		Articolo carta = articoloRepository.save(new Articolo("Mew", Categoria.CARTA_SINGOLA, null));
		Articolo gioco = articoloRepository.save(new Articolo("Zelda", Categoria.VIDEOGIOCO, null));
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 4, 1));
		RigaAcquisto venduta = new RigaAcquisto(carta, 2, new BigDecimal("10.00"));
		RigaAcquisto libera = new RigaAcquisto(gioco, 1, new BigDecimal("10.00"));
		acquisto.aggiungiRiga(venduta);
		acquisto.aggiungiRiga(libera);
		acquisto.ripartisciCostiAccessori();
		venduta.preleva(1);
		acquistoRepository.saveAndFlush(acquisto);

		// il form di modifica mostra le etichette complete, risolvibili al salvataggio
		mvc.perform(get("/acquisti/{id}/modifica", acquisto.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"" + elencoArticoli.etichetta(gioco) + "\"")));

		mvc.perform(post("/acquisti/{id}", acquisto.getId()).with(csrf())
				.param("dataAcquisto", "2026-04-01")
				.param("costoSpedizione", "4")
				.param("metodoRipartizione", "QUANTITA")
				// tentativo di cambiare la riga movimentata: ignorato
				.param("righe[0].id", venduta.getId().toString())
				.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(carta))
				.param("righe[0].quantita", "5")
				.param("righe[0].prezzoUnitario", "1")
				.param("righe[1].id", libera.getId().toString())
				.param("righe[1].articoloEtichetta", elencoArticoli.etichetta(gioco))
				.param("righe[1].quantita", "2")
				.param("righe[1].prezzoUnitario", "12"))
			.andExpect(redirectedUrl("/acquisti/" + acquisto.getId()));

		assertThat(venduta.getQuantita()).isEqualTo(2);
		assertThat(venduta.getPrezzoUnitario()).isEqualByComparingTo("10");
		assertThat(libera.getQuantita()).isEqualTo(2);
		// 4 € su 4 pezzi: 1 € a pezzo
		assertThat(venduta.getCostoUnitarioEffettivo()).isEqualByComparingTo("11");
		assertThat(libera.getCostoUnitarioEffettivo()).isEqualByComparingTo("13");

		// rimuovere la riga movimentata non è consentito
		mvc.perform(post("/acquisti/{id}", acquisto.getId()).with(csrf())
				.param("dataAcquisto", "2026-04-01")
				.param("metodoRipartizione", "PREZZO")
				.param("righe[0].id", libera.getId().toString())
				.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(gioco))
				.param("righe[0].quantita", "2")
				.param("righe[0].prezzoUnitario", "12"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("errore", containsString("non può essere rimossa")));

		mvc.perform(post("/acquisti/{id}/elimina", acquisto.getId()).with(csrf()))
			.andExpect(redirectedUrl("/acquisti/" + acquisto.getId()))
			.andExpect(flash().attribute("errore", containsString("non può essere eliminato")));
	}

	@Test
	void eliminaAcquistoSenzaMovimentiEFiltraLista() throws Exception {
		Articolo gioco = articoloRepository.save(new Articolo("Chrono Trigger", Categoria.VIDEOGIOCO, null));
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 3, 1));
		acquisto.setFornitore("Negozio Retro");
		acquisto.aggiungiRiga(new RigaAcquisto(gioco, 1, new BigDecimal("80")));
		acquistoRepository.saveAndFlush(acquisto);

		mvc.perform(get("/acquisti").param("q", "chrono").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Negozio Retro")))
			.andExpect(content().string(not(containsString("<html"))));
		mvc.perform(get("/acquisti").param("q", "zzz"))
			.andExpect(content().string(containsString("Nessun acquisto trovato")));

		mvc.perform(post("/acquisti/{id}/elimina", acquisto.getId()).with(csrf()))
			.andExpect(redirectedUrl("/acquisti"));
		assertThat(acquistoRepository.findById(acquisto.getId())).isEmpty();
	}

}

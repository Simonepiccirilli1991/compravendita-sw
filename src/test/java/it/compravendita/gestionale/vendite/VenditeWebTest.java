package it.compravendita.gestionale.vendite;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.TestcontainersConfiguration;
import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.magazzino.StatoOggetto;
import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class VenditeWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	PiattaformaRepository piattaformaRepository;

	@Autowired
	VenditaRepository venditaRepository;

	@Autowired
	ElencoArticoli elencoArticoli;

	@Autowired
	EntityManager em;

	Piattaforma ebay;

	Articolo carta;

	RigaAcquisto lottoVecchio;

	RigaAcquisto lottoNuovo;

	@BeforeEach
	void dati() {
		ebay = piattaformaRepository.findByNomeIgnoreCase("eBay").orElseThrow();
		ebay.setFeePercentuale(new BigDecimal("10"));
		ebay.setFeeFissa(new BigDecimal("0.35"));
		carta = articoloRepository.save(new Articolo("Blastoise Base Set", Categoria.CARTA_SINGOLA, null));
		lottoVecchio = lotto(LocalDate.of(2026, 1, 10), 1, "10");
		lottoNuovo = lotto(LocalDate.of(2026, 3, 10), 3, "14");
		em.flush();
	}

	@Test
	void venditaFifoConCommissioniAutomatiche() throws Exception {
		Long id = registra(vendita("3", "25").param("spedizioneIncassata", "5").param("costoSpedizione", "4"));

		Vendita vendita = venditaRepository.findById(id).orElseThrow();
		// incasso 75 + 5 = 80: 10% + 0,35
		assertThat(vendita.getCommissioniPiattaforma()).isEqualByComparingTo("8.35");
		assertThat(vendita.getNetto()).isEqualByComparingTo("67.65");
		// FIFO: 1 pezzo a 10 dal lotto vecchio, 2 a 14 dal nuovo
		assertThat(vendita.getCostoVenduto()).isEqualByComparingTo("38");
		assertThat(vendita.getProfitto()).isEqualByComparingTo("29.65");
		assertThat(lottoVecchio.getStato()).isEqualTo(StatoOggetto.VENDUTO);
		assertThat(lottoNuovo.getQuantitaResidua()).isEqualTo(1);

		mvc.perform(get("/vendite/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("29,65")))
			.andExpect(content().string(containsString("78,0%")));
	}

	@Test
	void numeroProgressivoAutomatico() throws Exception {
		mvc.perform(get("/vendite/nuova"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("riferimentoOrdine"))));

		Long primaId = registra(vendita("1", "20"));
		Long secondaId = registra(vendita("1", "22"));
		Long primo = venditaRepository.findById(primaId).orElseThrow().getNumero();
		Long secondo = venditaRepository.findById(secondaId).orElseThrow().getNumero();
		assertThat(primo).isNotNull();
		assertThat(secondo).isGreaterThan(primo);

		mvc.perform(get("/vendite/{id}", secondaId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Vendita n° " + secondo)));
		mvc.perform(get("/vendite").param("q", "#" + secondo))
			.andExpect(status().isOk())
			.andExpect(model().attribute("pagina", org.hamcrest.Matchers.hasProperty("totalElements", org.hamcrest.Matchers.is(1L))));

		// la modifica non cambia il numero
		mvc.perform(post("/vendite/{id}", secondaId).with(csrf())
			.param("dataVendita", "2026-04-02")
			.param("piattaformaId", ebay.getId().toString())
			.param("commissioniAutomatiche", "true")
			.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(carta))
			.param("righe[0].quantita", "1")
			.param("righe[0].prezzoUnitario", "23"))
			.andExpect(status().is3xxRedirection());
		em.flush();
		em.clear();
		assertThat(venditaRepository.findById(secondaId).orElseThrow().getNumero()).isEqualTo(secondo);
	}

	@Test
	void lottoSceltoACommissioniManuali() throws Exception {
		Long id = registra(vendita("1", "30")
			.param("righe[0].lottoId", lottoNuovo.getId().toString())
			.param("commissioniAutomatiche", "false")
			.param("commissioniPiattaforma", "2.10")
			.param("commissioniPagamento", "0.40"));

		Vendita vendita = venditaRepository.findById(id).orElseThrow();
		assertThat(vendita.isCommissioniAutomatiche()).isFalse();
		assertThat(vendita.getNetto()).isEqualByComparingTo("27.50");
		assertThat(vendita.getCostoVenduto()).isEqualByComparingTo("14");
		assertThat(lottoVecchio.getQuantitaResidua()).isEqualTo(1);
		assertThat(lottoNuovo.getQuantitaResidua()).isEqualTo(2);
	}

	@Test
	void disponibilitaInsufficienteEArticoloNonValido() throws Exception {
		mvc.perform(vendita("5", "25"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "righe[0].quantita"))
			.andExpect(content().string(containsString("Disponibili solo 4 pezzi")));

		mvc.perform(post("/vendite").with(csrf())
				.param("dataVendita", "2026-10-01")
				.param("piattaformaId", ebay.getId().toString())
				.param("righe[0].articoloEtichetta", "qualcosa")
				.param("righe[0].quantita", "1")
				.param("righe[0].prezzoUnitario", "1"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "righe[0].articoloEtichetta"));
		assertThat(venditaRepository.count()).isZero();
	}

	@Test
	void modificaRiallocaLaMerce() throws Exception {
		Long id = registra(vendita("3", "25"));
		assertThat(lottoNuovo.getQuantitaResidua()).isEqualTo(1);

		mvc.perform(get("/vendite/{id}/modifica", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(elencoArticoli.etichetta(carta))));

		// ora 4 pezzi: tutti quelli disponibili, contando i 3 già allocati
		mvc.perform(post("/vendite/{id}", id).with(csrf())
				.param("dataVendita", "2026-10-02")
				.param("piattaformaId", ebay.getId().toString())
				.param("commissioniAutomatiche", "true")
				.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(carta))
				.param("righe[0].quantita", "4")
				.param("righe[0].prezzoUnitario", "20"))
			.andExpect(redirectedUrl("/vendite/" + id));

		em.flush();
		em.clear();
		Vendita vendita = venditaRepository.findById(id).orElseThrow();
		assertThat(vendita.getTotaleLordo()).isEqualByComparingTo("80");
		assertThat(vendita.getCostoVenduto()).isEqualByComparingTo("52");
		assertThat(em.find(RigaAcquisto.class, lottoNuovo.getId()).getQuantitaResidua()).isZero();
	}

	@Test
	void annullamentoResoEStorico() throws Exception {
		Long annullata = registra(vendita("1", "25"));
		Long resa = registra(vendita("2", "30").param("costoSpedizione", "6"));
		Long buona = registra(vendita("1", "40"));
		assertThat(lottoNuovo.getQuantitaResidua()).isZero();

		mvc.perform(post("/vendite/{id}/annulla", annullata).with(csrf()).param("data", "2026-10-05"))
			.andExpect(redirectedUrl("/vendite/" + annullata));
		mvc.perform(get("/vendite/{id}/reso", resa))
			.andExpect(content().string(containsString("value=\"6.00\"")));
		mvc.perform(post("/vendite/{id}/reso", resa).with(csrf()).param("data", "2026-10-06")
				.param("costiReso", "6").param("motivo", "Carta rovinata"))
			.andExpect(redirectedUrl("/vendite/" + resa));
		assertThat(lottoVecchio.getQuantitaResidua() + lottoNuovo.getQuantitaResidua()).isEqualTo(3);

		// un reso non si può annullare
		mvc.perform(post("/vendite/{id}/annulla", resa).with(csrf()).param("data", "2026-10-07"))
			.andExpect(flash().attribute("errore", containsString("già")));

		mvc.perform(get("/vendite").param("piattaformaId", ebay.getId().toString()))
			.andExpect(status().isOk())
			// totali: solo la vendita completata (40 - 4,35 di commissioni - 14 di costo) meno 6 di reso
			.andExpect(model().attribute("totali", org.hamcrest.Matchers.hasToString(containsString("vendite=1"))))
			.andExpect(content().string(containsString("15,65")));
		mvc.perform(get("/vendite").param("categoria", "LEGO").header("HX-Request", "true"))
			.andExpect(content().string(containsString("Nessuna vendita trovata")))
			.andExpect(content().string(not(containsString("<html"))));

		mvc.perform(post("/vendite/{id}/elimina", buona).with(csrf()))
			.andExpect(flash().attribute("errore", containsString("solo le vendite annullate")));
		mvc.perform(post("/vendite/{id}/elimina", annullata).with(csrf()))
			.andExpect(redirectedUrl("/vendite"));
		assertThat(venditaRepository.findById(annullata)).isEmpty();

		mvc.perform(get("/vendite/articoli/{id}", carta.getId()).header("HX-Request", "true"))
			.andExpect(content().string(containsString("Resa dal cliente")));
	}

	@Test
	void frammentiHtmx() throws Exception {
		mvc.perform(get("/vendite/lotti").param("indice", "0")
				.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(carta)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("righe[0].lottoId")))
			.andExpect(content().string(containsString("3 disp.")));

		mvc.perform(get("/vendite/anteprima")
				.param("piattaformaId", ebay.getId().toString())
				.param("commissioniAutomatiche", "true")
				.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(carta))
				.param("righe[0].quantita", "2")
				.param("righe[0].prezzoUnitario", "50"))
			.andExpect(status().isOk())
			// 100 - 10,35 di commissioni - 24 di costo (10 + 14)
			.andExpect(content().string(containsString("65,65")));

		mvc.perform(get("/vendite/righe").param("azione", "aggiungi").param("righe[0].quantita", "1"))
			.andExpect(content().string(containsString("righe[1].articoloEtichetta")));

		mvc.perform(get("/vendite/nuova").param("lottoId", lottoNuovo.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Blastoise Base Set")));
	}

	@Test
	void gestionePiattaforme() throws Exception {
		mvc.perform(post("/piattaforme").with(csrf()).param("nome", "Catawiki").param("feePercentuale", "12.5")
				.param("feeFissa", "0").param("feePagamentoPercentuale", "0").param("feePagamentoFissa", "0")
				.param("attiva", "true"))
			.andExpect(redirectedUrl("/piattaforme"));
		assertThat(piattaformaRepository.findByNomeIgnoreCase("catawiki")).get()
			.extracting(Piattaforma::getFeePercentuale)
			.satisfies(fee -> assertThat(fee).isEqualByComparingTo("12.5"));

		mvc.perform(post("/piattaforme").with(csrf()).param("nome", "EBAY").param("feePercentuale", "1")
				.param("feeFissa", "0").param("feePagamentoPercentuale", "0").param("feePagamentoFissa", "0"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "nome"));

		mvc.perform(post("/piattaforme").with(csrf()).param("nome", "Errata").param("feePercentuale", "150")
				.param("feeFissa", "0").param("feePagamentoPercentuale", "0").param("feePagamentoFissa", "0"))
			.andExpect(model().attributeHasFieldErrors("form", "feePercentuale"));

		registra(vendita("1", "25"));
		mvc.perform(post("/piattaforme/{id}/elimina", ebay.getId()).with(csrf()))
			.andExpect(flash().attribute("errore", containsString("disattivala")));
		mvc.perform(get("/piattaforme"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Catawiki")));
	}

	private MockHttpServletRequestBuilder vendita(String quantita, String prezzo) {
		return post("/vendite").with(csrf())
			.param("dataVendita", "2026-10-01")
			.param("piattaformaId", ebay.getId().toString())
			.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(carta))
			.param("righe[0].quantita", quantita)
			.param("righe[0].prezzoUnitario", prezzo);
	}

	private Long registra(MockHttpServletRequestBuilder richiesta) throws Exception {
		String location = mvc.perform(richiesta)
			.andExpect(redirectedUrlPattern("/vendite/*"))
			.andReturn().getResponse().getRedirectedUrl();
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private RigaAcquisto lotto(LocalDate data, int quantita, String prezzo) {
		Acquisto acquisto = new Acquisto(data);
		RigaAcquisto riga = new RigaAcquisto(carta, quantita, new BigDecimal(prezzo));
		acquisto.aggiungiRiga(riga);
		acquistoRepository.save(acquisto);
		return riga;
	}

}

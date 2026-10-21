package it.compravendita.gestionale.prezzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
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
import java.util.LinkedHashMap;
import java.util.Map;

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
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class PrezziWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	PrezziService prezziService;

	@Autowired
	PiattaformaRepository piattaformaRepository;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	ElencoArticoli elencoArticoli;

	/** Richiesta con i parametri tipici; le coppie nome, valore indicate sostituiscono quelli predefiniti. */
	private static MockHttpServletRequestBuilder parametri(MockHttpServletRequestBuilder richiesta, String... modifiche) {
		Map<String, String> valori = new LinkedHashMap<>(Map.of("coefficienteRedditivita", "40", "aliquotaImposta", "5",
				"aliquotaContributi", "24.48", "riduzioneContributi", "35", "minimaleInps", "18808",
				"contributoMaternita", "7.44", "altriCostiFissi", "0", "ricaviAnnuiPrevisti", "10000",
				"margineDesiderato", "30", "costoImballaggio", "1"));
		valori.put("costoSpedizione", "0");
		for (int i = 0; i < modifiche.length; i += 2) {
			valori.put(modifiche[i], modifiche[i + 1]);
		}
		valori.forEach(richiesta::param);
		return richiesta;
	}

	@Test
	void parametriIniziali() throws Exception {
		ParametriPrezzo p = prezziService.parametri();
		assertThat(p.getCoefficienteRedditivita()).isEqualByComparingTo("40");
		assertThat(p.getAliquotaImposta()).isEqualByComparingTo("5");
		assertThat(p.getAliquotaContributi()).isEqualByComparingTo("24.48");
		assertThat(p.getRiduzioneContributi()).isEqualByComparingTo("35");
		assertThat(p.getMinimaleInps()).isEqualByComparingTo("18808");
		assertThat(p.getContributoMaternita()).isEqualByComparingTo("7.44");

		mvc.perform(get("/prezzi"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Prezzi e tasse"), containsString("Prezzo minimo (in pari)"),
					containsString("3.000,17"), containsString("28,5%"))));
	}

	@Test
	void salvaParametriConPiattaforma() throws Exception {
		Piattaforma etsy = new Piattaforma("Etsy prova");
		etsy.setFeePercentuale(new BigDecimal("6.5"));
		etsy = piattaformaRepository.save(etsy);

		mvc.perform(parametri(post("/prezzi").with(csrf()), "margineDesiderato", "50", "aliquotaImposta", "15",
				"piattaformaId", etsy.getId().toString()))
			.andExpect(redirectedUrl("/prezzi"))
			.andExpect(flash().attribute("messaggio", "Parametri dei prezzi salvati"));

		ParametriPrezzo p = prezziService.parametri();
		assertThat(p.getAliquotaImposta()).isEqualByComparingTo("15");
		assertThat(p.getPiattaforma().getNome()).isEqualTo("Etsy prova");
		assertThat(prezziService.calcolo().commissionePercentuale()).isEqualByComparingTo("6.5");
	}

	@Test
	void valoriNonValidiMostranoErrori() throws Exception {
		mvc.perform(parametri(post("/prezzi").with(csrf()), "aliquotaContributi", "120", "ricaviAnnuiPrevisti", "0"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "aliquotaContributi", "ricaviAnnuiPrevisti"))
			.andExpect(content().string(containsString("Massimo 100%")));
		assertThat(prezziService.parametri().getAliquotaContributi()).isEqualByComparingTo("24.48");
	}

	@Test
	void simulazioneConIValoriDelForm() throws Exception {
		mvc.perform(parametri(get("/prezzi/simulazione")).param("costo", "10").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("15,83"), containsString("20,50"))));

		mvc.perform(parametri(get("/prezzi/simulazione"), "aliquotaImposta", "").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Completa i parametri")));
	}

	@Test
	void acquistoMostraIPrezziSuggeriti() throws Exception {
		mvc.perform(parametri(post("/prezzi").with(csrf()))).andExpect(redirectedUrl("/prezzi"));
		Articolo tazza = articoloRepository.save(new Articolo("Tazza Hogwarts", Categoria.TAZZA, null));

		// anteprima mentre si compila il form
		mvc.perform(get("/acquisti/anteprima").header("HX-Request", "true")
				.param("dataAcquisto", "2026-05-01")
				.param("metodoRipartizione", "PREZZO")
				.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(tazza))
				.param("righe[0].quantita", "2")
				.param("righe[0].prezzoUnitario", "10"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Prezzo consigliato"), containsString("15,83"),
					containsString("20,50"))));

		String location = mvc.perform(post("/acquisti").with(csrf())
				.param("dataAcquisto", "2026-05-01")
				.param("metodoRipartizione", "PREZZO")
				.param("righe[0].articoloEtichetta", elencoArticoli.etichetta(tazza))
				.param("righe[0].quantita", "2")
				.param("righe[0].prezzoUnitario", "10")
				.param("righe[0].stato", "IN_STOCK"))
			.andExpect(redirectedUrlPattern("/acquisti/*"))
			.andReturn().getResponse().getRedirectedUrl();

		mvc.perform(get(location))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Prezzo minimo"), containsString("15,83"),
					containsString("20,50"), containsString("Modifica i parametri"))));

		mvc.perform(get("/magazzino/lotti"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("15,83"), containsString("20,50"))));

		// scheda dell'articolo: riquadro con i prezzi del prossimo lotto da vendere
		mvc.perform(get("/magazzino/articoli/{id}", tazza.getId()).header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Prezzi di vendita suggeriti"),
					containsString("Prezzo minimo (in pari)"), containsString("15,83"), containsString("20,50"),
					containsString("il prossimo da vendere"))));
	}

}

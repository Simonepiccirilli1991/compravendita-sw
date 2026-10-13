package it.compravendita.gestionale.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.ModelAndView;

import it.compravendita.gestionale.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class GestioneErroriWebTest {

	@Autowired
	MockMvc mvc;

	@Test
	void risorsaInesistenteMostraPaginaInItaliano() throws Exception {
		mvc.perform(get("/acquisti/{id}", 999999))
			.andExpect(status().isNotFound())
			.andExpect(view().name("error"))
			.andExpect(content().string(allOf(containsString("Non trovato"),
					containsString("Acquisto 999999 non trovato"), containsString("Codice 404"))));
	}

	@Test
	void indirizzoInesistente() throws Exception {
		mvc.perform(get("/pagina-che-non-esiste"))
			.andExpect(status().isNotFound())
			.andExpect(content().string(containsString("La pagina richiesta non esiste")));
	}

	@Test
	void parametroNonNumerico() throws Exception {
		mvc.perform(get("/vendite/{id}", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(containsString("Valore non valido per &quot;id&quot;")));
	}

	@Test
	void metodoNonSupportato() throws Exception {
		mvc.perform(post("/dashboard").with(csrf()))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().string(containsString("Operazione non consentita su questo indirizzo")));
	}

	@Test
	void operazioneNonConsentitaDiventa409() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/vendite/1/elimina");
		ModelAndView mav = new GestoreErrori().nonConsentita(
				new OperazioneNonConsentitaException("Si possono eliminare solo le vendite annullate"), request,
				new MockHttpServletResponse());

		assertThat(mav.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(mav.getViewName()).isEqualTo("error");
		assertThat(mav.getModel()).containsEntry("message", "Si possono eliminare solo le vendite annullate");
	}

	@Test
	void richiestaHtmxRiceveSoloIlMessaggio() throws Exception {
		mvc.perform(get("/report/acquisti/{id}", 999999).header("HX-Request", "true"))
			.andExpect(status().isNotFound())
			.andExpect(header().string("HX-Reswap", "none"))
			.andExpect(content().string(allOf(containsString("Acquisto 999999 non trovato"),
					not(containsString("<html")))));
	}

	@Test
	void erroriDiConversioneNeiFormInItaliano() throws Exception {
		mvc.perform(multipart("/acquisti").with(csrf())
				.param("dataAcquisto", "2026-05-01")
				.param("costoSpedizione", "dieci")
				.param("metodoRipartizione", "PREZZO"))
			.andExpect(status().isOk())
			.andExpect(view().name("acquisti/form"))
			.andExpect(content().string(allOf(containsString("Inserisci un numero valido"),
					not(containsString("Failed to convert")))));
	}

}

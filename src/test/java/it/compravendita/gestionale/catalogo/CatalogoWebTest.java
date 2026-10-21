package it.compravendita.gestionale.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.nio.file.Path;

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
import it.compravendita.gestionale.common.AppProperties;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class CatalogoWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	BrandRepository brandRepository;

	@Autowired
	AppProperties appProperties;

	@Test
	void creaTazzaConAttributiEFoto() throws Exception {
		Long disney = brandRepository.findByNomeIgnoreCase("Disney").orElseThrow().getId();
		MockMultipartFile foto = new MockMultipartFile("foto", "tazza.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

		String location = mvc.perform(multipart("/articoli").file(foto).with(csrf())
				.param("nome", "Tazza Stitch sagomata")
				.param("categoria", "TAZZA")
				.param("brandId", disney.toString())
				.param("condizione", "NUOVO_CONFEZIONATO")
				.param("attributi[materiale]", "Ceramica")
				.param("attributi[capacita]", "450")
				.param("attributi[soggetto]", "Stitch e Angel"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("/articoli/*"))
			.andExpect(flash().attribute("messaggio", "Articolo creato"))
			.andReturn()
			.getResponse()
			.getRedirectedUrl();

		Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		Articolo articolo = articoloRepository.findById(id).orElseThrow();
		assertThat(articolo.getAttributo("materiale")).isEqualTo("Ceramica");
		assertThat(new BigDecimal(articolo.getAttributo("capacita").toString())).isEqualByComparingTo("450");
		assertThat(articolo.getFotoPath()).startsWith("articoli/");
		assertThat(Path.of(appProperties.uploadDir().toString(), articolo.getFotoPath())).exists();

		mvc.perform(get("/articoli/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Stitch e Angel")))
			.andExpect(content().string(containsString("Nuovo con confezione")));
	}

	@Test
	void attributiObbligatoriMancantiMostranoErrori() throws Exception {
		mvc.perform(post("/articoli").with(csrf())
				.param("nome", "Accessorio senza tipo")
				.param("categoria", "ACCESSORIO")
				.param("attributi[materiale]", "Acciaio inox"))
			.andExpect(status().isOk())
			.andExpect(view().name("articoli/form"))
			.andExpect(model().attributeExists("erroriAttributi"))
			.andExpect(content().string(containsString("Campo obbligatorio")));
		assertThat(articoloRepository.findByNomeContainingIgnoreCaseOrderByNomeAsc("senza tipo")).isEmpty();
	}

	@Test
	void nomeObbligatorio() throws Exception {
		mvc.perform(post("/articoli").with(csrf()).param("nome", " ").param("categoria", "ALTRO"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "nome"));
	}

	@Test
	void campiDinamiciPerCategoria() throws Exception {
		mvc.perform(get("/articoli/campi").param("categoria", "SET_REGALO").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("attributi[numeroPezzi]")))
			.andExpect(content().string(not(containsString("<html"))));
	}

	@Test
	void listaFiltraEConRichiestaHtmxRestituisceSoloRisultati() throws Exception {
		Brand potter = brandRepository.findByNomeIgnoreCase("Harry Potter").orElseThrow();
		Articolo termica = new Articolo("Tazza termica Hogwarts", Categoria.TAZZA_TERMICA, potter);
		termica.setAttributo("materiale", "Acciaio inox");
		articoloRepository.save(termica);
		articoloRepository.save(new Articolo("Bicchiere Nutella vintage", Categoria.BICCHIERE, null));

		mvc.perform(get("/articoli").param("categoria", "TAZZA_TERMICA"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Tazza termica Hogwarts")))
			.andExpect(content().string(not(containsString("Bicchiere Nutella vintage"))));

		mvc.perform(get("/articoli").param("q", "nutella").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Bicchiere Nutella vintage")))
			.andExpect(content().string(not(containsString("Tazza termica Hogwarts"))))
			.andExpect(content().string(not(containsString("<html"))));
	}

	@Test
	void eliminaArticoloSenzaMovimenti() throws Exception {
		Long id = articoloRepository.save(new Articolo("Da eliminare", Categoria.ALTRO, null)).getId();

		mvc.perform(post("/articoli/{id}/elimina", id).with(csrf()))
			.andExpect(redirectedUrl("/articoli"));
		assertThat(articoloRepository.findById(id)).isEmpty();
	}

	@Test
	void brandDuplicatoRifiutato() throws Exception {
		mvc.perform(post("/brand").with(csrf()).param("nome", "pokémon"))
			.andExpect(redirectedUrl("/brand"))
			.andExpect(flash().attribute("errore", containsString("Esiste già")));
	}

	@Test
	void brandConArticoliNonEliminabile() throws Exception {
		Brand sega = brandRepository.findByNomeIgnoreCase("Thun").orElseThrow();
		articoloRepository.save(new Articolo("Tazza angioletti", Categoria.TAZZA, sega));

		mvc.perform(post("/brand/{id}/elimina", sega.getId()).with(csrf()))
			.andExpect(flash().attribute("errore", containsString("disattivalo")));
		assertThat(brandRepository.findById(sega.getId())).isPresent();

		mvc.perform(get("/brand"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Thun")));
	}

	@Test
	void articoloInesistente404() throws Exception {
		mvc.perform(get("/articoli/{id}", 999999))
			.andExpect(status().isNotFound());
	}

}

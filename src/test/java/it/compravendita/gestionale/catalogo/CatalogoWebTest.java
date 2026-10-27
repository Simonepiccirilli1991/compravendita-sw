package it.compravendita.gestionale.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
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
	void creaCartaGradataConAttributiEFoto() throws Exception {
		Long pokemon = brandRepository.findByNomeIgnoreCase("Pokémon").orElseThrow().getId();
		MockMultipartFile foto = new MockMultipartFile("foto", "psa.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

		String location = mvc.perform(multipart("/articoli").file(foto).with(csrf())
				.param("nome", "Charizard Base Set")
				.param("categoria", "CARTA_GRADATA")
				.param("brandId", pokemon.toString())
				.param("lingua", "ENG")
				.param("attributi[enteGrading]", "PSA")
				.param("attributi[voto]", "9")
				.param("attributi[numeroCertificato]", "44556677"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("/articoli/*"))
			.andExpect(flash().attribute("messaggio", "Articolo creato"))
			.andReturn()
			.getResponse()
			.getRedirectedUrl();

		Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		Articolo articolo = articoloRepository.findById(id).orElseThrow();
		assertThat(articolo.getAttributo("enteGrading")).isEqualTo("PSA");
		assertThat(new BigDecimal(articolo.getAttributo("voto").toString())).isEqualByComparingTo("9");
		assertThat(articolo.getFotoPath()).startsWith("articoli/");
		assertThat(Path.of(appProperties.uploadDir().toString(), articolo.getFotoPath())).exists();

		mvc.perform(get("/articoli/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("44556677")));
	}

	@Test
	void creaOggettiFuoriDalCore() throws Exception {
		String location = mvc.perform(post("/articoli").with(csrf())
				.param("nome", "Tazza Pikachu")
				.param("categoria", "TAZZA")
				.param("attributi[materiale]", "Ceramica")
				.param("attributi[capacita]", "350")
				.param("attributi[colore]", "Giallo"))
			.andExpect(redirectedUrlPattern("/articoli/*"))
			.andReturn()
			.getResponse()
			.getRedirectedUrl();
		Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		Articolo tazza = articoloRepository.findById(id).orElseThrow();
		assertThat(tazza.getCategoria()).isEqualTo(Categoria.TAZZA);
		assertThat(tazza.getAttributo("capacita")).isEqualTo(350);
		mvc.perform(get("/articoli/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Tazze e bicchieri"), containsString("Capacità (ml)"),
					containsString("Ceramica"))));

		mvc.perform(post("/articoli").with(csrf())
				.param("nome", "Portachiavi One Piece")
				.param("categoria", "OGGETTISTICA")
				.param("attributi[tipoOggetto]", "Portachiavi"))
			.andExpect(redirectedUrlPattern("/articoli/*"));
		assertThat(articoloRepository.findByNomeContainingIgnoreCaseOrderByNomeAsc("Portachiavi"))
			.singleElement()
			.satisfies(a -> assertThat(a.getCategoria()).isEqualTo(Categoria.OGGETTISTICA));
	}

	@Test
	void attributiObbligatoriMancantiMostranoErrori() throws Exception {
		mvc.perform(post("/articoli").with(csrf())
				.param("nome", "Carta senza voto")
				.param("categoria", "CARTA_GRADATA")
				.param("attributi[enteGrading]", "PSA"))
			.andExpect(status().isOk())
			.andExpect(view().name("articoli/form"))
			.andExpect(model().attributeExists("erroriAttributi"))
			.andExpect(content().string(containsString("Campo obbligatorio")));
		assertThat(articoloRepository.findByNomeContainingIgnoreCaseOrderByNomeAsc("senza voto")).isEmpty();
	}

	@Test
	void nomeObbligatorio() throws Exception {
		mvc.perform(post("/articoli").with(csrf()).param("nome", " ").param("categoria", "ALTRO"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "nome"));
	}

	@Test
	void campiDinamiciPerCategoria() throws Exception {
		mvc.perform(get("/articoli/campi").param("categoria", "LEGO").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("attributi[numeroSet]")))
			.andExpect(content().string(not(containsString("<html"))));
	}

	@Test
	void listaFiltraEConRichiestaHtmxRestituisceSoloRisultati() throws Exception {
		Brand lego = brandRepository.findByNomeIgnoreCase("LEGO").orElseThrow();
		Articolo falcon = new Articolo("Millennium Falcon", Categoria.LEGO, lego);
		falcon.setAttributo("numeroSet", "75192");
		articoloRepository.save(falcon);
		articoloRepository.save(new Articolo("Super Mario 64", Categoria.VIDEOGIOCO, null));

		mvc.perform(get("/articoli").param("categoria", "LEGO"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Millennium Falcon")))
			.andExpect(content().string(not(containsString("Super Mario 64"))));

		mvc.perform(get("/articoli").param("q", "mario").header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Super Mario 64")))
			.andExpect(content().string(not(containsString("Millennium Falcon"))))
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
		Brand sega = brandRepository.findByNomeIgnoreCase("Sega").orElseThrow();
		articoloRepository.save(new Articolo("Sonic", Categoria.VIDEOGIOCO, sega));

		mvc.perform(post("/brand/{id}/elimina", sega.getId()).with(csrf()))
			.andExpect(flash().attribute("errore", containsString("disattivalo")));
		assertThat(brandRepository.findById(sega.getId())).isPresent();

		mvc.perform(get("/brand"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Sega")));
	}

	@Test
	void articoloInesistente404() throws Exception {
		mvc.perform(get("/articoli/{id}", 999999))
			.andExpect(status().isNotFound());
	}

}

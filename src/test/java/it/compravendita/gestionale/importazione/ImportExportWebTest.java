package it.compravendita.gestionale.importazione;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.AttributiArticolo;
import it.compravendita.gestionale.catalogo.BrandRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.magazzino.MotivoRettifica;
import it.compravendita.gestionale.magazzino.RettificaMagazzino;
import it.compravendita.gestionale.magazzino.RettificaMagazzinoRepository;
import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;
import it.compravendita.gestionale.report.CalcoloEconomicoService;
import it.compravendita.gestionale.report.ContoAnnuale;
import it.compravendita.gestionale.vendite.RigaVendita;
import it.compravendita.gestionale.vendite.Vendita;
import it.compravendita.gestionale.vendite.VenditaRepository;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class ImportExportWebTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ImportazioneService importazioneService;

	@Autowired
	CalcoloEconomicoService calcolo;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	BrandRepository brandRepository;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	PiattaformaRepository piattaformaRepository;

	@Autowired
	VenditaRepository venditaRepository;

	@Autowired
	RettificaMagazzinoRepository rettificaRepository;

	@Autowired
	EntityManager em;

	@Test
	void ilModelloScaricabileSiImportaSenzaErrori() throws Exception {
		byte[] modello = mvc.perform(get("/import/modello.csv"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("modello-inventario.csv")))
			.andReturn().getResponse().getContentAsByteArray();

		ImportazioneService.Analisi analisi = importazioneService.analizza(modello);
		assertThat(analisi.erroriGenerali()).isEmpty();
		assertThat(analisi.righe()).allSatisfy(r -> assertThat(r.errori()).isEmpty());
		assertThat(analisi.righe()).hasSize(4);
		// stessa data e stesso fornitore → un solo acquisto per le due righe del 20/02
		assertThat(analisi.acquisti()).isEqualTo(3);

		ImportazioneService.Esito esito = importazioneService.importa(modello);
		assertThat(esito.righe()).isEqualTo(4);
		assertThat(esito.articoliCreati()).isEqualTo(4);
		Articolo gradata = articoloRepository.findByNomeIgnoreCaseAndCategoria("Charizard Base Set PSA 9",
				Categoria.CARTA_GRADATA).getFirst();
		assertThat(gradata.getAttributo(AttributiArticolo.NUMERO_CERTIFICATO)).isEqualTo("12345678");
		assertThat(gradata.getBrand().getNome()).isEqualTo("Pokémon");
	}

	@Test
	void anteprimaConErroriPoiImportConArticoloEsistenteEBrandNuovo() throws Exception {
		Articolo esistente = articoloRepository.save(new Articolo("Zelda Ocarina of Time", Categoria.VIDEOGIOCO,
				brandRepository.findByNomeIgnoreCase("Nintendo").orElseThrow()));
		String errato = "nome;categoria;quantita;prezzo\nCarta;Figurina;1;2\nBox;Carta gradata;1;10\nX;Altro;0;abc\n";

		mvc.perform(multipart("/import/anteprima")
				.file(new MockMultipartFile("file", "errato.csv", "text/csv", errato.getBytes(StandardCharsets.UTF_8)))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Categoria &quot;Figurina&quot; non riconosciuta")))
			.andExpect(content().string(containsString("Ente di grading: campo obbligatorio")))
			.andExpect(content().string(containsString("Quantità non valida")))
			.andExpect(content().string(containsString("3 righe con errori")))
			.andExpect(content().string(not(containsString("/import/conferma"))));

		String valido = """
				Nome;Categoria;Brand;Quantità;Prezzo unitario;Data acquisto;Fornitore;console;Colonna strana
				Zelda Ocarina of Time;Videogioco;nintendo;2;30,00;10/01/2026;Mercatino;N64;x
				Sonic Adventure;Videogioco;Sega Nuova;1;12;10/01/2026;mercatino;Dreamcast;
				""";
		mvc.perform(multipart("/import/anteprima")
				.file(new MockMultipartFile("file", "ok.csv", "text/csv", valido.getBytes(StandardCharsets.UTF_8)))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Articolo esistente")))
			.andExpect(content().string(containsString("Colonne non riconosciute e ignorate: Colonna strana")))
			.andExpect(content().string(containsString("/import/conferma")));

		mvc.perform(post("/import/conferma").with(csrf())
				.param("contenuto", Base64.getEncoder().encodeToString(valido.getBytes(StandardCharsets.UTF_8))))
			.andExpect(redirectedUrl("/acquisti"))
			.andExpect(flash().attribute("messaggio", containsString("Importate 2 righe in 1 acquisti")));

		em.flush();
		em.clear();
		List<Acquisto> acquisti = acquistoRepository.findAll();
		assertThat(acquisti).singleElement().satisfies(a -> {
			assertThat(a.getDataAcquisto()).isEqualTo(LocalDate.of(2026, 1, 10));
			assertThat(a.getRighe()).hasSize(2);
			assertThat(a.getTotale()).isEqualByComparingTo("72");
			assertThat(a.getRighe().getFirst().getArticolo().getId()).isEqualTo(esistente.getId());
		});
		assertThat(brandRepository.findByNomeIgnoreCase("sega nuova")).isPresent();
	}

	@Test
	void exportCsvExcelERiepilogoAnnuale() throws Exception {
		Articolo carta = articoloRepository.save(new Articolo("Lugia", Categoria.CARTA_SINGOLA, null));
		RigaAcquisto lotto = lotto(carta, LocalDate.of(2025, 6, 1), 4, "10");
		lotto.getAcquisto().setFornitore("Fiera di Verona");
		Piattaforma ebay = piattaformaRepository.findByNomeIgnoreCase("eBay").orElseThrow();
		vendita(ebay, carta, lotto, LocalDate.of(2025, 9, 1), "25");
		Vendita resa = vendita(ebay, carta, lotto, LocalDate.of(2025, 12, 20), "30");
		resa.registraReso(LocalDate.of(2026, 1, 10), BigDecimal.ZERO, null);
		vendita(ebay, carta, lotto, LocalDate.of(2026, 3, 1), "28");
		rettificaRepository.save(new RettificaMagazzino(lotto, LocalDate.of(2026, 2, 1), 1, MotivoRettifica.PERSO,
				null, null));
		em.flush();

		// a fine 2025: 4 − 1 venduto − 1 in una vendita resa solo nel 2026
		ContoAnnuale anno2025 = calcolo.contoAnnuale(2025);
		assertThat(anno2025.rimanenzeFinali()).isEqualByComparingTo("20");
		assertThat(anno2025.vendite().vendite()).isEqualTo(1);
		assertThat(anno2025.acquistato()).isEqualByComparingTo("40");
		// a fine 2026: 4 − 2 vendute − 1 persa
		assertThat(calcolo.contoAnnuale(2026).rimanenzeFinali()).isEqualByComparingTo("10");
		assertThat(calcolo.anniDisponibili()).contains(2026, 2025);

		String csv = mvc.perform(get("/export/acquisti.csv").param("q", "lugia"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", containsString("text/csv")))
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("Fiera di Verona").contains("Lugia").contains("10,00");

		byte[] xlsx = mvc.perform(get("/export/vendite.xlsx").param("da", "2025-01-01").param("a", "2025-12-31"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString(".xlsx")))
			.andReturn().getResponse().getContentAsByteArray();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
			assertThat(workbook.getSheet("Vendite").getPhysicalNumberOfRows()).isEqualTo(3);
			assertThat(workbook.getSheet("Vendite").getRow(1).getCell(8).getNumericCellValue()).isEqualTo(25.0);
		}

		mvc.perform(get("/export/magazzino.csv"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lugia")));
		byte[] annuale = mvc.perform(get("/export/annuale.xlsx")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(annuale))) {
			assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Voce");
		}
		mvc.perform(get("/report/annuale"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Rimanenze al 31/12")))
			.andExpect(content().string(containsString("20,00")));
		mvc.perform(get("/export/acquisti.pdf")).andExpect(status().isNotFound());
	}

	@Test
	void ricercaGlobaleNegliAttributi() throws Exception {
		Articolo psa = new Articolo("Blastoise", Categoria.CARTA_GRADATA, null);
		psa.setAttributo(AttributiArticolo.ENTE_GRADING, "PSA");
		psa.setAttributo(AttributiArticolo.NUMERO_CERTIFICATO, "98765432");
		articoloRepository.save(psa);
		Articolo lego = new Articolo("Castello", Categoria.LEGO, null);
		lego.setAttributo(AttributiArticolo.NUMERO_SET, "10305");
		articoloRepository.save(lego);
		em.flush();

		mvc.perform(get("/cerca").param("q", "8765"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Blastoise")))
			.andExpect(content().string(not(containsString("Castello"))));
		mvc.perform(get("/articoli").param("q", "10305").header("HX-Request", "true"))
			.andExpect(content().string(containsString("Castello")));
		mvc.perform(get("/cerca").param("q", "zzzzz"))
			.andExpect(content().string(containsString("Nessun risultato")));
		mvc.perform(get("/cerca").param("q", "x"))
			.andExpect(content().string(containsString("almeno 2 caratteri")));
	}

	private RigaAcquisto lotto(Articolo articolo, LocalDate data, int quantita, String prezzo) {
		Acquisto acquisto = new Acquisto(data);
		RigaAcquisto riga = new RigaAcquisto(articolo, quantita, new BigDecimal(prezzo));
		acquisto.aggiungiRiga(riga);
		acquistoRepository.save(acquisto);
		return riga;
	}

	private Vendita vendita(Piattaforma piattaforma, Articolo articolo, RigaAcquisto lotto, LocalDate data,
			String prezzo) {
		Vendita vendita = new Vendita(data, piattaforma);
		RigaVendita riga = new RigaVendita(articolo, 1, new BigDecimal(prezzo));
		riga.allocaDa(lotto, 1);
		vendita.aggiungiRiga(riga);
		return venditaRepository.save(vendita);
	}

}

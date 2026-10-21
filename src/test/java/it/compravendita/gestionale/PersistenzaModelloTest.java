package it.compravendita.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;
import it.compravendita.gestionale.vendite.RigaVendita;
import it.compravendita.gestionale.vendite.Vendita;
import it.compravendita.gestionale.vendite.VenditaRepository;
import jakarta.persistence.EntityManager;

/**
 * Verifica che schema Flyway e mapping JPA siano allineati su un PostgreSQL reale.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenzaModelloTest {

	@Autowired
	EntityManager em;

	@Autowired
	BrandRepository brandRepository;

	@Autowired
	PiattaformaRepository piattaformaRepository;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	RigaAcquistoRepository rigaAcquistoRepository;

	@Autowired
	VenditaRepository venditaRepository;

	@Test
	void datiInizialiPresenti() {
		assertThat(brandRepository.findByNomeIgnoreCase("disney")).isPresent();
		assertThat(brandRepository.findByNomeIgnoreCase("Harry Potter")).isPresent();
		assertThat(brandRepository.findByNomeIgnoreCase("LEGO")).isEmpty();
		assertThat(piattaformaRepository.findByAttivaTrueOrderByNomeAsc())
			.extracting(Piattaforma::getNome)
			.contains("eBay", "Vinted", "Etsy")
			.doesNotContain("Cardmarket");
	}

	@Test
	void attributiJsonbSalvatiERiletti() {
		Brand pokemon = brandRepository.findByNomeIgnoreCase("Pokémon").orElseThrow();
		Articolo tazza = new Articolo("Tazza Pikachu", Categoria.TAZZA, pokemon);
		tazza.setAttributo(AttributiArticolo.MATERIALE, "Ceramica");
		tazza.setAttributo(AttributiArticolo.CAPACITA, 350);
		tazza.setAttributo(AttributiArticolo.SOGGETTO, "Pikachu");
		Long id = articoloRepository.saveAndFlush(tazza).getId();
		em.clear();

		Articolo riletto = articoloRepository.findById(id).orElseThrow();
		assertThat(riletto.getAttributo(AttributiArticolo.MATERIALE)).isEqualTo("Ceramica");
		assertThat(riletto.getAttributo(AttributiArticolo.CAPACITA)).isEqualTo(350);
		assertThat(riletto.getCreatoIl()).isNotNull();
	}

	@Test
	void flussoAcquistoVenditaConFifo() {
		Brand lego = brandRepository.findByNomeIgnoreCase("Disney").orElseThrow();
		Articolo set = articoloRepository.save(new Articolo("Millennium Falcon 75192", Categoria.TAZZA_TERMICA, lego));

		Acquisto primo = new Acquisto(LocalDate.of(2026, 1, 10));
		primo.aggiungiRiga(new RigaAcquisto(set, 1, new BigDecimal("600.00")));
		Acquisto secondo = new Acquisto(LocalDate.of(2026, 3, 5));
		secondo.aggiungiRiga(new RigaAcquisto(set, 2, new BigDecimal("650.00")));
		acquistoRepository.saveAll(List.of(secondo, primo));
		em.flush();

		List<RigaAcquisto> lotti = rigaAcquistoRepository.findDisponibiliFifo(set.getId());
		assertThat(lotti).extracting(RigaAcquisto::getPrezzoUnitario)
			.usingElementComparator(BigDecimal::compareTo)
			.containsExactly(new BigDecimal("600"), new BigDecimal("650"));

		Piattaforma ebay = piattaformaRepository.findByNomeIgnoreCase("eBay").orElseThrow();
		Vendita vendita = new Vendita(LocalDate.of(2026, 10, 1), ebay);
		RigaVendita riga = new RigaVendita(set, 2, new BigDecimal("800.00"));
		vendita.aggiungiRiga(riga);
		riga.allocaDa(lotti.get(0), 1);
		riga.allocaDa(lotti.get(1), 1);
		vendita.setCommissioniPiattaforma(new BigDecimal("100.00"));
		Long idVendita = venditaRepository.saveAndFlush(vendita).getId();
		em.clear();

		Vendita riletta = venditaRepository.findById(idVendita).orElseThrow();
		assertThat(riletta.getTotaleLordo()).isEqualByComparingTo("1600");
		assertThat(riletta.getNetto()).isEqualByComparingTo("1500");
		assertThat(riletta.getCostoVenduto()).isEqualByComparingTo("1250");
		assertThat(rigaAcquistoRepository.findDisponibiliFifo(set.getId()))
			.singleElement()
			.extracting(RigaAcquisto::getQuantitaResidua)
			.isEqualTo(1);
	}

}
